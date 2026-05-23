package com.cresensolutions.leaveservice.service.impl;

import com.cresensolutions.leaveservice.dto.MyEmpLeaveHistory;
import com.cresensolutions.leaveservice.entity.Leave;
import com.cresensolutions.leaveservice.entity.LeaveDayDetails;
import com.cresensolutions.leaveservice.exception.CustomException;
import com.cresensolutions.leaveservice.repository.ApplyLeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveDayDetailsRepository;
import com.cresensolutions.leaveservice.service.EmailService;
import com.cresensolutions.leaveservice.service.ManagerService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import static com.cresensolutions.leaveservice.common.LeaveConstants.*;

@Service
@AllArgsConstructor
@Slf4j
public class ManagerServiceImpl implements ManagerService {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final ApplyLeaveRepository applyLeaveRepository;
    private final LeaveDayDetailsRepository leaveDayDetailsRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    // GET: send employees all pending leave requests on which manager have to take action
    @Override
    public List<Map<String, Object>> getManagerTasks(String managerEmail) {

        // Retrieve a list of Pending tasks assigned to Manager
        List<Task> tasks = taskService.createTaskQuery()
                .taskAssignee(managerEmail)
                .list();

        List<Map<String, Object>> response = new ArrayList<>();

        for (Task task : tasks) {

            Map<String, Object> vars = runtimeService.getVariables(task.getExecutionId());
            Long leaveId = (Long) vars.get(LEAVE_ID);

            Leave leave = applyLeaveRepository.findById(leaveId)
                    .orElseThrow(() -> new RuntimeException("Leave not found for id: " + leaveId));

            List<LeaveDayDetails> dayDetails = leaveDayDetailsRepository.findByLeaveId(leaveId);

            // Expose day-level details including the DB id so the frontend can
            // send back which specific day IDs the manager is approving.
            List<Map<String, Object>> cleanDays = new ArrayList<>();
            for (LeaveDayDetails d : dayDetails) {
                Map<String, Object> day = new HashMap<>();
                day.put("id", d.getId());   // needed for partial-approval payload
                day.put(DAY, d.getLeaveDate());
                day.put(DAY_TYPE, d.getDayType());
                day.put(HALF_DAY_SESSION, d.getHalfDaySession());
                cleanDays.add(day);
            }

            Map<String, Object> taskData = new HashMap<>();
            taskData.put(TASK_ID, task.getId());
            taskData.put(LEAVE_ID, leaveId);
            taskData.put(EMPLOYEE_NAME, vars.get(USER_FULL_NAME));
            taskData.put(FROM_DATE, leave.getFromDate());
            taskData.put(TO_DATE, leave.getToDate());
            taskData.put(LEAVE_TYPE, leave.getLeaveType());
            taskData.put(EMP_LEAVE_REASON, leave.getReason());
            taskData.put(LEAVE_DAY_DETAILS, cleanDays);
            response.add(taskData);
        }
        return response;
    }

    // POST: manager submits approve / reject / partial-approve

    /**
     * Expected request payload:

     * Full approval  : { taskId, managerApproved: true }
     * Full rejection : { taskId, managerApproved: false, rejectionReason: "..." }
     * Partial        : { taskId, managerApproved: true,
     * partialApprovedDayIds: [1, 3],   ← IDs of approved days
     * rejectionReason: "Day 2 clashes" }

     * When partialApprovedDayIds is present and its size is less than the total
     * number of day-details for that leave, partial-approval logic kicks in:
     * • A new Leave record is created for the approved days → forwarded to HR.
     * • The original leave (or the rejected days) is marked REJECTED and
     * a rejection mail is sent to the employee & manager immediately.
     * • The Flowable task is completed with managerApproved=true so the
     * process advances to HR review for the approved child leave.
     */
    @Override
    public void handleManagerAction(Map<String, Object> request) {

        String taskId = (String) request.get(TASK_ID);
        Boolean managerApproved = (Boolean) request.get(MANAGER_APPROVED);

        // Resolve which leave this task belongs to
        Task task = taskService.createTaskQuery().
                taskId(taskId)
                .singleResult();

        if (task == null) {
            throw new CustomException("Task not found: " + taskId, 404);
        }

        Map<String, Object> processVars = runtimeService.getVariables(task.getExecutionId());
        Long originalLeaveId = (Long) processVars.get(LEAVE_ID);

        // Partial-approval IDs sent by the frontend (maybe null)
        List<Long> approvedDayIds = normalizeDayIds(request.get(PARTIAL_APPROVED_DAY_IDS));

        List<LeaveDayDetails> allDays = leaveDayDetailsRepository.findByLeaveId(originalLeaveId);

        boolean isPartial = Boolean.TRUE.equals(managerApproved)
                && approvedDayIds != null
                && !approvedDayIds.isEmpty()
                && approvedDayIds.size() < allDays.size();

        if (isPartial) {
            handlePartialApproval(request, task, processVars, originalLeaveId, approvedDayIds, allDays);
        } else {
            handleFullDecision(request, taskId, managerApproved, originalLeaveId, allDays);
        }
    }

    // Full approve or full reject
    private void handleFullDecision(Map<String, Object> request,
                                    String taskId,
                                    Boolean managerApproved,
                                    Long leaveId,
                                    List<LeaveDayDetails> allDays) {
        log.info("FULL REQUEST PAYLOAD: {}", request);

        Map<String, Object> variables = new HashMap<>();
        variables.put(MANAGER_APPROVED, managerApproved);

        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        String managerName = resolveManagerName(request, task.getExecutionId());
        LocalDateTime actionTime = LocalDateTime.now();

        List<Map<String, Object>> trail =
                (List<Map<String, Object>>) runtimeService.getVariable(task.getExecutionId(), TRAIL);

        if (trail == null) {
            trail = new ArrayList<>();
        }

        // make previous step inactive (IS_CURRENT_STEP is for better UI)
        for (Map<String, Object> t : trail) {
            t.put(IS_CURRENT_STEP, false);
        }

        // Manager Entry

        // Manager is approving all leaves
        if (Boolean.TRUE.equals(managerApproved)) {

            Map<String, Object> entry = new HashMap<>();
            entry.put(ROLE, ROLE_MANAGER);
            entry.put(ACTOR_NAME, managerName);
            entry.put(ACTION, ACTION_MANAGER_APPROVED);
            entry.put(TIME, actionTime);
            entry.put(IS_CURRENT_STEP, true);

            trail.add(entry);

            // save back
            runtimeService.setVariable(task.getExecutionId(), TRAIL, trail);
            Leave leave = applyLeaveRepository.findById(leaveId).orElseThrow();
            leave.setTrail(objectMapper.valueToTree(trail));
            applyLeaveRepository.save(leave);
        }

        // Manager is rejecting leaves
        if (Boolean.FALSE.equals(managerApproved)) {
            String globalRejectionReason = resolveRejectionReason(request.get(REJECTION_REASON));
            Map<Long, String> rejectedDayReasons = normalizeReasonMap(request.get("rejectedDayReasons"));

            if (allDays != null && allDays.size() > 1) {
                // Split the leave request into individual rejected days
                LeaveDayDetails firstRejectedDay = allDays.get(0);
                String firstReason = rejectedDayReasons.getOrDefault(firstRejectedDay.getId(), globalRejectionReason);
                if (firstReason == null || firstReason.isBlank()) {
                    firstReason = "This leave day was not approved by manager";
                }

                variables.put(REJECTION_REASON, firstReason);

                // Update original leave in DB to represent only the first rejected day
                Leave originalLeave = applyLeaveRepository.findById(leaveId).orElseThrow();
                originalLeave.setStatus(STATUS_REJECTED);
                originalLeave.setRejectionReason(firstReason);
                originalLeave.setFromDate(firstRejectedDay.getLeaveDate());
                originalLeave.setToDate(firstRejectedDay.getLeaveDate());
                originalLeave.setEditable(false);

                List<Map<String, Object>> rejectedTrail = buildRejectedTrailSnapshot(trail, managerName, firstReason, actionTime);
                originalLeave.setTrail(objectMapper.valueToTree(rejectedTrail));
                applyLeaveRepository.save(originalLeave);

                // Remove other days from original leave day details, leaving only the first one
                List<LeaveDayDetails> existingDayDetails = leaveDayDetailsRepository.findByLeaveId(leaveId);
                leaveDayDetailsRepository.deleteAll(
                        existingDayDetails.stream()
                                .filter(d -> !Objects.equals(d.getId(), firstRejectedDay.getId()))
                                .toList()
                );

                // Create new Leave records for the remaining rejected days
                for (int i = 1; i < allDays.size(); i++) {
                    LeaveDayDetails rejectedDay = allDays.get(i);
                    String rejectionReason = rejectedDayReasons.getOrDefault(rejectedDay.getId(), globalRejectionReason);
                    if (rejectionReason == null || rejectionReason.isBlank()) {
                        rejectionReason = "This leave day was not approved by manager";
                    }

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

                    List<Map<String, Object>> stepTrail = buildRejectedTrailSnapshot(trail, managerName, rejectionReason, actionTime);
                    rejectedLeave.setTrail(objectMapper.valueToTree(stepTrail));

                    Leave savedRejectedLeave = applyLeaveRepository.save(rejectedLeave);

                    LeaveDayDetails rejectedDayCopy = new LeaveDayDetails();
                    rejectedDayCopy.setLeaveId(savedRejectedLeave.getId());
                    rejectedDayCopy.setLeaveDate(rejectedDay.getLeaveDate());
                    rejectedDayCopy.setDayType(rejectedDay.getDayType());
                    rejectedDayCopy.setHalfDaySession(rejectedDay.getHalfDaySession());
                    leaveDayDetailsRepository.save(rejectedDayCopy);

                    emailService.sendManagerRejectionMailToEmployee(
                            savedRejectedLeave.getEmailId(),
                            savedRejectedLeave.getId(),
                            rejectionReason
                    );
                }

                // Update the Flowable variable for trail
                runtimeService.setVariable(task.getExecutionId(), TRAIL, rejectedTrail);

            } else {
                // 1 day or no days
                String rejectionReason = globalRejectionReason;
                if (rejectionReason.isBlank() && !rejectedDayReasons.isEmpty()) {
                    rejectionReason = rejectedDayReasons.values().iterator().next();
                }
                if (rejectionReason.isBlank()) {
                    rejectionReason = "This leave day was not approved by manager";
                }

                variables.put(REJECTION_REASON, rejectionReason);

                Map<String, Object> entry = new HashMap<>();
                entry.put(ROLE, ROLE_MANAGER);
                entry.put(ACTOR_NAME, managerName);
                entry.put(ACTION, ACTION_MANAGER_REJECTED);
                entry.put(REASON, rejectionReason);
                entry.put(REJECTION_REASON, rejectionReason);
                entry.put(TIME, actionTime);
                entry.put(IS_CURRENT_STEP, true);

                trail.add(entry);

                // save back
                runtimeService.setVariable(task.getExecutionId(), TRAIL, trail);

                Leave leave = applyLeaveRepository.findById(leaveId).orElseThrow();
                leave.setStatus(STATUS_REJECTED);
                leave.setRejectionReason(rejectionReason);
                leave.setEditable(false);
                leave.setTrail(objectMapper.valueToTree(trail));
                applyLeaveRepository.save(leave);
            }
        }

        taskService.complete(taskId, variables);
    }

    /**
     * Partial approval:
     * 1. Create a NEW Leave record containing only the approved days.
     * 2. Mark the original leave as REJECTED, record rejection reason,
     * and send the manager-rejection email to the employee right now.
     * 3. Update the Flowable process variable LEAVE_ID to point to the new
     * approved leave, then complete the task with managerApproved=true so
     * the process continues (HR review) for the approved portion.
     */
    private void handlePartialApproval(Map<String, Object> request,
                                       Task task,
                                       Map<String, Object> processVars,
                                       Long originalLeaveId,
                                       List<Long> approvedDayIds,
                                       List<LeaveDayDetails> allDays) {

        // STEP 1: Get existing trail
        List<Map<String, Object>> trail =
                (List<Map<String, Object>>) runtimeService.getVariable(task.getExecutionId(), TRAIL);

        if (trail == null) {
            trail = new ArrayList<>();
        }

        // STEP 2: Update trail (VERY IMPORTANT - do this early)
        for (Map<String, Object> t : trail) {
            t.put(IS_CURRENT_STEP, false);
        }

        String managerName = resolveManagerName(request, task.getExecutionId());
        LocalDateTime actionTime = LocalDateTime.now();

        Map<String, Object> entry = new HashMap<>();
        entry.put(ROLE, ROLE_MANAGER);
        entry.put(ACTOR_NAME, managerName);
        entry.put(ACTION, ACTION_MANAGER_PARTIAL_APPROVED);
        entry.put(TIME, actionTime);
        entry.put(IS_CURRENT_STEP, true);

        trail.add(entry);

        // save in flowable
        runtimeService.setVariable(task.getExecutionId(), TRAIL, trail);

        Map<Long, String> rejectionReasonsByDayId = normalizeReasonMap(request.get("rejectedDayReasons"));

        Leave originalLeave = applyLeaveRepository.findById(originalLeaveId)
                .orElseThrow(() -> new RuntimeException("Leave not found: " + originalLeaveId));

        Set<Long> approvedIdSet = new HashSet<>(approvedDayIds);

        List<LeaveDayDetails> approvedDays = allDays.stream()
                .filter(d -> approvedIdSet.contains(d.getId()))
                .toList();

        List<LeaveDayDetails> rejectedDays = allDays.stream()
                .filter(d -> !approvedIdSet.contains(d.getId()))
                .toList();

        // STEP 4: Create NEW leave (approved part)
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

        // SAVE trail in NEW leave
        savedApprovedLeave.setTrail(objectMapper.valueToTree(trail));
        applyLeaveRepository.save(savedApprovedLeave);

        // STEP 5: Save approved day details
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

        createRejectedLeaveEntries(
                originalLeave,
                rejectedDays,
                trail,
                rejectionReasonsByDayId,
                approvedIdSet,
                managerName,
                actionTime
        );

        // STEP 7: Flowable variables update
        runtimeService.setVariable(task.getExecutionId(), LEAVE_ID, savedApprovedLeave.getId());
        runtimeService.setVariable(task.getExecutionId(), PARTIAL_APPROVAL, Boolean.TRUE);
        runtimeService.setVariable(task.getExecutionId(), FROM_DATE, approvedFrom);
        runtimeService.setVariable(task.getExecutionId(), TO_DATE, approvedTo);

        Map<String, Object> completionVars = new HashMap<>();
        completionVars.put(MANAGER_APPROVED, Boolean.TRUE);
        completionVars.put(PARTIAL_APPROVAL, Boolean.TRUE);

        // STEP 8: Complete task
        taskService.complete(task.getId(), completionVars);
    }

    @Override
    public List<MyEmpLeaveHistory> getEmployeeLeaveHistory(String managerEmail) {
        List<Leave> leaves = applyLeaveRepository.findByManagerEmailOrderByCreatedAtDesc(managerEmail);
        List<MyEmpLeaveHistory> response = new ArrayList<>();

        for (Leave leave: leaves){
            MyEmpLeaveHistory myEmpLeaveHistoryResponse = new MyEmpLeaveHistory();

            myEmpLeaveHistoryResponse.setLeaveId(leave.getId());
            myEmpLeaveHistoryResponse.setEmployeeName(extractActorNameFromTrail(leave));
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

            response.add(myEmpLeaveHistoryResponse);
        }
        return response;
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
                                            String managerName,
                                            LocalDateTime actionTime) {
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
                "This leave day was not approved by manager");

        originalLeave.setStatus(STATUS_REJECTED);
        originalLeave.setRejectionReason(firstReason);
        originalLeave.setFromDate(firstRejectedDay.getLeaveDate());
        originalLeave.setToDate(firstRejectedDay.getLeaveDate());
        originalLeave.setEditable(false);
        originalLeave.setTrail(objectMapper.valueToTree(buildRejectedTrailSnapshot(
                trail, managerName, firstReason, actionTime)));
        applyLeaveRepository.save(originalLeave);

        List<LeaveDayDetails> existingDayDetails = leaveDayDetailsRepository.findByLeaveId(originalLeave.getId());
        leaveDayDetailsRepository.deleteAll(
                existingDayDetails.stream()
                        .filter(d -> approvedIdSet.contains(d.getId())
                                || !Objects.equals(d.getId(), firstRejectedDay.getId()))
                        .toList()
        );

        emailService.sendManagerRejectionMailToEmployee(
                originalLeave.getEmailId(),
                originalLeave.getId(),
                firstReason
        );

        for (int i = 1; i < rejectedDays.size(); i++) {
            LeaveDayDetails rejectedDay = rejectedDays.get(i);
            String rejectionReason = resolveRejectedDayReason(rejectionReasonsByDayId, rejectedDay,
                    "This leave day was not approved by manager");

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
            rejectedLeave.setTrail(objectMapper.valueToTree(buildRejectedTrailSnapshot(
                    trail, managerName, rejectionReason, actionTime)));

            Leave savedRejectedLeave = applyLeaveRepository.save(rejectedLeave);

            LeaveDayDetails rejectedDayCopy = new LeaveDayDetails();
            rejectedDayCopy.setLeaveId(savedRejectedLeave.getId());
            rejectedDayCopy.setLeaveDate(rejectedDay.getLeaveDate());
            rejectedDayCopy.setDayType(rejectedDay.getDayType());
            rejectedDayCopy.setHalfDaySession(rejectedDay.getHalfDaySession());
            leaveDayDetailsRepository.save(rejectedDayCopy);

            emailService.sendManagerRejectionMailToEmployee(
                    savedRejectedLeave.getEmailId(),
                    savedRejectedLeave.getId(),
                    rejectionReason
            );
        }
    }

    private String resolveRejectedDayReason(Map<Long, String> rejectionReasonsByDayId,
                                            LeaveDayDetails rejectedDay,
                                            String defaultReason) {
        return rejectionReasonsByDayId.getOrDefault(rejectedDay.getId(), defaultReason);
    }

    private List<Map<String, Object>> buildRejectedTrailSnapshot(List<Map<String, Object>> sourceTrail,
                                                                 String managerName,
                                                                 String rejectionReason,
                                                                 LocalDateTime actionTime) {
        List<Map<String, Object>> rejectedTrail = new ArrayList<>();

        for (Map<String, Object> step : sourceTrail) {
            String role = String.valueOf(step.getOrDefault(ROLE, ""));
            String action = String.valueOf(step.getOrDefault(ACTION, ""));

            if (ROLE_MANAGER.equalsIgnoreCase(role)
                    && ACTION_MANAGER_PARTIAL_APPROVED.equalsIgnoreCase(action)) {
                continue;
            }

            Map<String, Object> copiedStep = new HashMap<>(step);
            copiedStep.put(IS_CURRENT_STEP, false);
            rejectedTrail.add(copiedStep);
        }

        Map<String, Object> rejectedEntry = new HashMap<>();
        rejectedEntry.put(ROLE, ROLE_MANAGER);
        rejectedEntry.put(ACTOR_NAME, managerName);
        rejectedEntry.put(ACTION, ACTION_MANAGER_REJECTED);
        rejectedEntry.put(REASON, rejectionReason);
        rejectedEntry.put(REJECTION_REASON, rejectionReason);
        rejectedEntry.put(TIME, actionTime);
        rejectedEntry.put(IS_CURRENT_STEP, true);
        rejectedTrail.add(rejectedEntry);

        return rejectedTrail;
    }

    private String resolveRejectionReason(Object rawReason) {
        if (rawReason == null) {
            return "";
        }

        return String.valueOf(rawReason).trim();
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Long.parseLong(stringValue.trim());
            } catch (NumberFormatException ex) {
                log.warn("Unable to convert day id '{}' to Long", stringValue);
            }
        }

        return null;
    }

    private String resolveManagerName(Map<String, Object> request, String executionId) {
        Object managerName = request.get(MANAGER_NAME);
        if (managerName instanceof String managerNameValue && !managerNameValue.isBlank()) {
            return managerNameValue.trim();
        }

        Object workflowManagerName = runtimeService.getVariable(executionId, MANAGER_NAME);
        if (workflowManagerName instanceof String workflowManagerNameValue
                && !workflowManagerNameValue.isBlank()) {
            return workflowManagerNameValue.trim();
        }

        return null;
    }

    private String extractActorNameFromTrail(Leave leave) {
        if (leave.getTrail() == null || !leave.getTrail().isArray() || leave.getTrail().isEmpty()) {
            return null;
        }

        return leave.getTrail().get(0).path(ACTOR_NAME).asText(null);
    }
}
