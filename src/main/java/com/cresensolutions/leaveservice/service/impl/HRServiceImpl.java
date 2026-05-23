package com.cresensolutions.leaveservice.service.impl;

import com.cresensolutions.leaveservice.dto.HRTaskResponse;
import com.cresensolutions.leaveservice.dto.MyEmpLeaveHistory;
import com.cresensolutions.leaveservice.entity.Leave;
import com.cresensolutions.leaveservice.entity.LeaveDayDetails;
import com.cresensolutions.leaveservice.repository.ApplyLeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveDayDetailsRepository;
import com.cresensolutions.leaveservice.service.EmailService;
import com.cresensolutions.leaveservice.service.HRService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.cresensolutions.leaveservice.common.LeaveConstants.*;

@Service
@AllArgsConstructor
@Slf4j
public class HRServiceImpl implements HRService {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final ApplyLeaveRepository applyLeaveRepository;
    private final LeaveDayDetailsRepository leaveDayDetailsRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    // Step - 1 : When HR is logging in, I will display all pending tasks of that HR (hrEmail)
    @Override
    public List<HRTaskResponse> getHRTasks(String hrEmail) {

        List<Task> tasks = taskService.createTaskQuery()
                .taskAssignee(hrEmail)
                .list();

        List<HRTaskResponse> response = new ArrayList<>();

        for (Task task : tasks) {

            Map<String, Object> vars = runtimeService.getVariables(task.getExecutionId());
            Long leaveId = toLong(vars.get(LEAVE_ID));

            if (leaveId == null) {
                log.warn("Skipping HR task {} because leaveId invalid: {}", task.getId(), vars.get(LEAVE_ID));
                continue;
            }

            Leave leave = applyLeaveRepository.findById(leaveId).orElse(null);

            if (leave == null) {
                log.warn("Skipping HR task {} because leave {} not found", task.getId(), leaveId);
                continue;
            }

            // Day Details
            List<LeaveDayDetails> dayDetailsList = leaveDayDetailsRepository.findByLeaveId(leaveId);

            List<Map<String, Object>> cleanDays = new ArrayList<>();
            for (LeaveDayDetails d : dayDetailsList) {
                Map<String, Object> day = new HashMap<>();
                day.put("id", d.getId());
                day.put(DAY, d.getLeaveDate());
                day.put(DAY_TYPE, d.getDayType());
                day.put(HALF_DAY_SESSION, d.getHalfDaySession());
                cleanDays.add(day);
            }

            // Trail (Runtime → DB fallback with JsonNode conversion)
            List<Map<String, Object>> trail =
                    (List<Map<String, Object>>) runtimeService.getVariable(task.getExecutionId(), TRAIL);

            if (trail == null && leave.getTrail() != null && !leave.getTrail().isNull()) {
                trail = objectMapper.convertValue(
                        leave.getTrail(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}
                );
            }

            // DTO mapping
            HRTaskResponse dto = new HRTaskResponse();
            dto.setTaskId(task.getId());
            dto.setLeaveId(leaveId);
            dto.setEmployeeName((String) vars.get(USER_FULL_NAME));
            dto.setFromDate(leave.getFromDate());
            dto.setToDate(leave.getToDate());
            dto.setLeaveType(leave.getLeaveType());
            dto.setReason(leave.getReason());
            dto.setDayDetails(cleanDays);
            dto.setTrail(trail);

            response.add(dto);
        }

        return response;
    }

    // Step - 2 : When HR clicks on Approve/Reject button from frontend
    @Override
    public void handleHRAction(Map<String, Object> request) {

        // will receive taskId, hrApproved and hrName
        String taskId = (String) request.get(TASK_ID);
        Boolean hrApproved = (Boolean) request.get(HR_APPROVED);
        String hrName = (String) request.get("hrName");

        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();

        Long originalLeaveId = toLong(runtimeService.getVariable(task.getExecutionId(), LEAVE_ID));
        List<LeaveDayDetails> allDays = leaveDayDetailsRepository.findByLeaveId(originalLeaveId);
        List<Long> approvedDayIds = normalizeDayIds(request.get(PARTIAL_APPROVED_DAY_IDS));

        boolean isPartial = Boolean.TRUE.equals(hrApproved)
                && !approvedDayIds.isEmpty()
                && approvedDayIds.size() < allDays.size();

        if (isPartial) {
            handlePartialApproval(request, task, hrName, originalLeaveId, approvedDayIds, allDays);
            return;
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put(HR_APPROVED, hrApproved);

        if (Boolean.FALSE.equals(hrApproved)) {
            variables.put(REJECTION_REASON, request.get(REJECTION_REASON));
        }

        List<Map<String, Object>> trail =
                (List<Map<String, Object>>) runtimeService.getVariable(task.getExecutionId(), TRAIL);

        if (trail == null) {
            trail = new ArrayList<>();
        }

        // make previous step inactive
        for (Map<String, Object> t : trail) {
            t.put(IS_CURRENT_STEP, false);
        }


        // HR taking action and storing value according to hrApproved True/False
        Map<String, Object> entry = new HashMap<>();
        entry.put(ROLE, ROLE_HR);
        entry.put(ACTOR_NAME, hrName);
        entry.put(TIME, LocalDateTime.now());
        entry.put(IS_CURRENT_STEP, true);

        if (Boolean.TRUE.equals(hrApproved)) {
            entry.put(ACTION, ACTION_HR_APPROVED);
        } else {
            entry.put(ACTION, ACTION_HR_REJECTED);
            entry.put(REJECTION_REASON, request.get(REJECTION_REASON));
        }

        trail.add(entry);
        log.info("Trail after HR action: {}", trail);

        // save back
        runtimeService.setVariable(task.getExecutionId(), TRAIL, trail);

        Leave leave = applyLeaveRepository.findById(originalLeaveId).orElseThrow();

        if (Boolean.FALSE.equals(hrApproved)) {
            Map<Long, String> rejectionReasonsByDayId = normalizeReasonMap(request.get("rejectedDayReasons"));
            String defaultRejectionReason = request.get(REJECTION_REASON) == null
                    ? ""
                    : String.valueOf(request.get(REJECTION_REASON)).trim();
            List<String> fallbackReasonsByIndex = splitRejectionReasonSummary(defaultRejectionReason);
            String dayLevelDefaultReason = fallbackReasonsByIndex.size() > 1
                    ? "This leave day was not approved by HR"
                    : defaultRejectionReason;
            String fullName = (String) runtimeService.getVariable(task.getExecutionId(), USER_FULL_NAME);

            createRejectedLeaveEntries(
                    leave,
                    allDays,
                    trail,
                    rejectionReasonsByDayId,
                    Collections.emptySet(),
                    fullName,
                    fallbackReasonsByIndex,
                    dayLevelDefaultReason.isBlank()
                            ? "This leave day was not approved by HR"
                            : dayLevelDefaultReason
            );

            taskService.complete(taskId, variables);
            return;
        }

        leave.setTrail(objectMapper.valueToTree(trail));
        applyLeaveRepository.save(leave);

        taskService.complete(taskId, variables);
    }

    // Helper : If some selected dates are approved
    private void handlePartialApproval(Map<String, Object> request,
                                       Task task,
                                       String hrName,
                                       Long originalLeaveId,
                                       List<Long> approvedDayIds,
                                       List<LeaveDayDetails> allDays) {
        List<Map<String, Object>> trail =
                (List<Map<String, Object>>) runtimeService.getVariable(task.getExecutionId(), TRAIL);

        if (trail == null) {
            trail = new ArrayList<>();
        }

        for (Map<String, Object> t : trail) {
            t.put(IS_CURRENT_STEP, false);
        }

        Map<String, Object> entry = new HashMap<>();
        entry.put(ROLE, ROLE_HR);
        entry.put(ACTOR_NAME, hrName);
        entry.put(ACTION, ACTION_HR_PARTIAL_APPROVED);
        entry.put(TIME, LocalDateTime.now());
        entry.put(IS_CURRENT_STEP, true);
        trail.add(entry);

        runtimeService.setVariable(task.getExecutionId(), TRAIL, trail);

        Map<Long, String> rejectionReasonsByDayId = normalizeReasonMap(request.get("rejectedDayReasons"));

        Leave originalLeave = applyLeaveRepository.findById(originalLeaveId).orElseThrow();
        Set<Long> approvedIdSet = new HashSet<>(approvedDayIds);

        List<LeaveDayDetails> approvedDays = allDays.stream()
                .filter(d -> approvedIdSet.contains(d.getId()))
                .toList();

        List<LeaveDayDetails> rejectedDays = allDays.stream()
                .filter(d -> !approvedIdSet.contains(d.getId()))
                .toList();

        Leave approvedLeave = new Leave();
        approvedLeave.setUserId(originalLeave.getUserId());
        approvedLeave.setLeaveType(originalLeave.getLeaveType());
        approvedLeave.setEmailId(originalLeave.getEmailId());
        approvedLeave.setReason(originalLeave.getReason());
        approvedLeave.setComments(originalLeave.getComments());
        approvedLeave.setManagerEmail(originalLeave.getManagerEmail());
        approvedLeave.setManagerId(originalLeave.getManagerId());
        approvedLeave.setStatus(STATUS_PARTIAL_APPROVED);
        approvedLeave.setEditable(false);
        approvedLeave.setCreatedAt(LocalDate.now());

        LocalDate approvedFrom = approvedDays.stream()
                .map(LeaveDayDetails::getLeaveDate)
                .min(LocalDate::compareTo)
                .orElse(originalLeave.getFromDate());

        LocalDate approvedTo = approvedDays.stream()
                .map(LeaveDayDetails::getLeaveDate)
                .max(LocalDate::compareTo)
                .orElse(originalLeave.getToDate());

        approvedLeave.setFromDate(approvedFrom);
        approvedLeave.setToDate(approvedTo);

        Leave savedApprovedLeave = applyLeaveRepository.save(approvedLeave);
        savedApprovedLeave.setTrail(objectMapper.valueToTree(trail));
        applyLeaveRepository.save(savedApprovedLeave);

        List<LeaveDayDetails> approvedDayCopies = approvedDays.stream()
                .map(d -> {
                    LeaveDayDetails copy = new LeaveDayDetails();
                    copy.setLeaveId(savedApprovedLeave.getId());
                    copy.setLeaveDate(d.getLeaveDate());
                    copy.setDayType(d.getDayType());
                    copy.setHalfDaySession(d.getHalfDaySession());
                    return copy;
                })
                .toList();
        leaveDayDetailsRepository.saveAll(approvedDayCopies);

        String fullName = (String) runtimeService.getVariable(task.getExecutionId(), USER_FULL_NAME);
        createRejectedLeaveEntries(
                originalLeave,
                rejectedDays,
                trail,
                rejectionReasonsByDayId,
                approvedIdSet,
                fullName,
                Collections.emptyList(),
                "This leave day was not approved by HR"
        );

        runtimeService.setVariable(task.getExecutionId(), LEAVE_ID, savedApprovedLeave.getId());
        runtimeService.setVariable(task.getExecutionId(), PARTIAL_APPROVAL, Boolean.TRUE);
        runtimeService.setVariable(task.getExecutionId(), FROM_DATE, approvedFrom);
        runtimeService.setVariable(task.getExecutionId(), TO_DATE, approvedTo);

        Map<String, Object> completionVars = new HashMap<>();
        completionVars.put(HR_APPROVED, Boolean.TRUE);
        completionVars.put(PARTIAL_APPROVAL, Boolean.TRUE);

        taskService.complete(task.getId(), completionVars);
    }

    // Returning all leave history to HR Panel
    @Override
    public List<MyEmpLeaveHistory> getAllLeaveHistory() {
        List<Leave> leaves = applyLeaveRepository.findAllByOrderByCreatedAtDesc();
        List<MyEmpLeaveHistory> response = new ArrayList<>();

        for (Leave leave: leaves){
            MyEmpLeaveHistory myEmpLeaveHistoryResponse = new MyEmpLeaveHistory();

            myEmpLeaveHistoryResponse.setLeaveId(leave.getId());
            myEmpLeaveHistoryResponse.setEmployeeEmail(leave.getEmailId());
            myEmpLeaveHistoryResponse.setManagerEmail(leave.getManagerEmail());
            myEmpLeaveHistoryResponse.setLeaveType(leave.getLeaveType());
            myEmpLeaveHistoryResponse.setFromDate(leave.getFromDate());
            myEmpLeaveHistoryResponse.setToDate(leave.getToDate());
            myEmpLeaveHistoryResponse.setCreatedAt(leave.getCreatedAt());
            myEmpLeaveHistoryResponse.setReason(leave.getReason());
            myEmpLeaveHistoryResponse.setTrail(leave.getTrail());
            myEmpLeaveHistoryResponse.setStatus(leave.getStatus());
            myEmpLeaveHistoryResponse.setApprovedBy(leave.getApprovedBy());
            myEmpLeaveHistoryResponse.setRejectionReason(leave.getRejectionReason());
            myEmpLeaveHistoryResponse.setTrail(leave.getTrail());
            myEmpLeaveHistoryResponse.setEmployeeName(extractActorNameFromTrail(leave));

            response.add(myEmpLeaveHistoryResponse);
        }
        return response;
    }

    private String extractActorNameFromTrail(Leave leave) {
        if (leave.getTrail() == null || !leave.getTrail().isArray() || leave.getTrail().isEmpty()) {
            return null;
        }

        return leave.getTrail().get(0).path(ACTOR_NAME).asText(null);
    }

    // Helper functions
    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Long.parseLong(stringValue.trim());
            } catch (NumberFormatException ex) {
                log.warn("Could not parse leaveId '{}' as Long", stringValue);
            }
        }

        return null;
    }

    private List<Long> normalizeDayIds(Object rawDayIds) {
        if (!(rawDayIds instanceof Collection<?> collection)) {
            return Collections.emptyList();
        }

        List<Long> normalized = new ArrayList<>();
        for (Object value : collection) {
            Long dayId = toLong(value);
            if (dayId != null) {
                normalized.add(dayId);
            }
        }
        return normalized;
    }

    private Map<Long, String> normalizeReasonMap(Object rawReasons) {
        if (!(rawReasons instanceof Map<?, ?> rawMap)) {
            return Collections.emptyMap();
        }

        Map<Long, String> normalized = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            Long dayId = toLong(entry.getKey());
            if (dayId == null) {
                continue;
            }

            String reason = entry.getValue() == null ? "" : String.valueOf(entry.getValue()).trim();
            if (!reason.isBlank()) {
                normalized.put(dayId, reason);
            }
        }
        return normalized;
    }

    private void createRejectedLeaveEntries(Leave originalLeave,
                                            List<LeaveDayDetails> rejectedDays,
                                            List<Map<String, Object>> trail,
                                            Map<Long, String> rejectionReasonsByDayId,
                                            Set<Long> approvedIdSet,
                                            String fullName,
                                            List<String> fallbackReasonsByIndex,
                                            String defaultReason) {
        if (rejectedDays.isEmpty()) {
            leaveDayDetailsRepository.deleteAll(
                    leaveDayDetailsRepository.findByLeaveId(originalLeave.getId()).stream()
                            .filter(d -> approvedIdSet.contains(d.getId()))
                            .toList()
            );
            return;
        }

        LeaveDayDetails firstRejectedDay = rejectedDays.get(0);
        String firstReason = resolveRejectedDayReason(rejectionReasonsByDayId, firstRejectedDay,
                fallbackReasonsByIndex, 0, defaultReason);

        originalLeave.setStatus(STATUS_REJECTED);
        originalLeave.setRejectionReason(firstReason);
        originalLeave.setFromDate(firstRejectedDay.getLeaveDate());
        originalLeave.setToDate(firstRejectedDay.getLeaveDate());
        originalLeave.setEditable(false);
//        originalLeave.setTrail(objectMapper.valueToTree(trail));
        LocalDateTime actionTime = LocalDateTime.now();

        List<Map<String, Object>> firstTrail =
                buildRejectedTrailSnapshot(trail, fullName, firstReason, actionTime);

        originalLeave.setTrail(objectMapper.valueToTree(firstTrail));
        applyLeaveRepository.save(originalLeave);

        List<LeaveDayDetails> existingDayDetails = leaveDayDetailsRepository.findByLeaveId(originalLeave.getId());
        leaveDayDetailsRepository.deleteAll(
                existingDayDetails.stream()
                        .filter(d -> approvedIdSet.contains(d.getId())
                                || !Objects.equals(d.getId(), firstRejectedDay.getId()))
                        .toList()
        );

        emailService.sendHRRejectionMail(
                originalLeave.getEmailId(),
                originalLeave.getManagerEmail(),
                originalLeave.getId(),
                firstReason,
                fullName
        );

        for (int i = 1; i < rejectedDays.size(); i++) {
            LeaveDayDetails rejectedDay = rejectedDays.get(i);
            String rejectionReason = resolveRejectedDayReason(rejectionReasonsByDayId, rejectedDay,
                    fallbackReasonsByIndex, i, defaultReason);

            Leave rejectedLeave = new Leave();
            rejectedLeave.setUserId(originalLeave.getUserId());
            rejectedLeave.setLeaveType(originalLeave.getLeaveType());
            rejectedLeave.setEmailId(originalLeave.getEmailId());
            rejectedLeave.setReason(originalLeave.getReason());
            rejectedLeave.setComments(originalLeave.getComments());
            rejectedLeave.setManagerEmail(originalLeave.getManagerEmail());
            rejectedLeave.setManagerId(originalLeave.getManagerId());
            rejectedLeave.setStatus(STATUS_REJECTED);
            rejectedLeave.setRejectionReason(rejectionReason);
            rejectedLeave.setEditable(false);
            rejectedLeave.setCreatedAt(LocalDate.now());
            rejectedLeave.setFromDate(rejectedDay.getLeaveDate());
            rejectedLeave.setToDate(rejectedDay.getLeaveDate());
//            rejectedLeave.setTrail(objectMapper.valueToTree(trail));
            List<Map<String, Object>> perDayTrail =
                    buildRejectedTrailSnapshot(trail, fullName, rejectionReason, actionTime);

            rejectedLeave.setTrail(objectMapper.valueToTree(perDayTrail));
            Leave savedRejectedLeave = applyLeaveRepository.save(rejectedLeave);

            LeaveDayDetails rejectedDayCopy = new LeaveDayDetails();
            rejectedDayCopy.setLeaveId(savedRejectedLeave.getId());
            rejectedDayCopy.setLeaveDate(rejectedDay.getLeaveDate());
            rejectedDayCopy.setDayType(rejectedDay.getDayType());
            rejectedDayCopy.setHalfDaySession(rejectedDay.getHalfDaySession());
            leaveDayDetailsRepository.save(rejectedDayCopy);

            emailService.sendHRRejectionMail(
                    savedRejectedLeave.getEmailId(),
                    savedRejectedLeave.getManagerEmail(),
                    savedRejectedLeave.getId(),
                    rejectionReason,
                    fullName
            );
        }
    }

    private List<Map<String, Object>> buildRejectedTrailSnapshot(
            List<Map<String, Object>> sourceTrail,
            String hrName,
            String rejectionReason,
            LocalDateTime actionTime) {

        List<Map<String, Object>> rejectedTrail = new ArrayList<>();

        for (Map<String, Object> step : sourceTrail) {
            String role = String.valueOf(step.getOrDefault(ROLE, ""));
            String action = String.valueOf(step.getOrDefault(ACTION, ""));

            // remove partial step
            if (ROLE_HR.equalsIgnoreCase(role)
                    && ACTION_HR_PARTIAL_APPROVED.equalsIgnoreCase(action)) {
                continue;
            }

            Map<String, Object> copiedStep = new HashMap<>(step);
            copiedStep.put(IS_CURRENT_STEP, false);
            rejectedTrail.add(copiedStep);
        }

        Map<String, Object> rejectedEntry = new HashMap<>();
        rejectedEntry.put(ROLE, ROLE_HR);
        rejectedEntry.put(ACTOR_NAME, hrName);
        rejectedEntry.put(ACTION, ACTION_HR_REJECTED);
        rejectedEntry.put(REASON, rejectionReason);
        rejectedEntry.put(TIME, actionTime);
        rejectedEntry.put(IS_CURRENT_STEP, true);

        rejectedTrail.add(rejectedEntry);

        return rejectedTrail;
    }

    private String resolveRejectedDayReason(Map<Long, String> rejectionReasonsByDayId,
                                            LeaveDayDetails rejectedDay,
                                            List<String> fallbackReasonsByIndex,
                                            int rejectedDayIndex,
                                            String defaultReason) {
        String reasonByDayId = rejectionReasonsByDayId.get(rejectedDay.getId());
        if (reasonByDayId != null && !reasonByDayId.isBlank()) {
            return reasonByDayId;
        }

        if (rejectedDayIndex >= 0 && rejectedDayIndex < fallbackReasonsByIndex.size()) {
            String fallbackReason = fallbackReasonsByIndex.get(rejectedDayIndex);
            if (fallbackReason != null && !fallbackReason.isBlank()) {
                return fallbackReason;
            }
        }

        return defaultReason;
    }

    private List<String> splitRejectionReasonSummary(String rejectionReasonSummary) {
        if (rejectionReasonSummary == null || rejectionReasonSummary.isBlank()) {
            return Collections.emptyList();
        }

        List<String> reasons = new ArrayList<>();
        for (String reason : rejectionReasonSummary.split("\\|")) {
            String trimmedReason = reason.trim();
            if (!trimmedReason.isBlank()) {
                reasons.add(trimmedReason);
            }
        }
        return reasons;
    }
}
