package com.cresensolutions.leaveservice.service.impl;

import com.cresensolutions.leaveservice.dto.ApplyLeaveRequest;
import com.cresensolutions.leaveservice.dto.ApplyLeaveResponse;
import com.cresensolutions.leaveservice.entity.Leave;
import com.cresensolutions.leaveservice.entity.LeaveDayDetails;
import com.cresensolutions.leaveservice.exception.CustomException;
import com.cresensolutions.leaveservice.repository.ApplyLeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveDayDetailsRepository;
import com.cresensolutions.leaveservice.service.ApplyLeaveService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import java.time.LocalDate;
import java.util.List;

import static com.cresensolutions.leaveservice.common.LeaveConstants.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplyLeaveServiceImpl implements ApplyLeaveService {

    private final ApplyLeaveRepository applyLeaveRepository;
    private final LeaveDayDetailsRepository leaveDayDetailsRepository;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    @Value("${app.hr.email:" + DEFAULT_HR_EMAIL + "}")
    private String hrEmail;

    // Returns all leaves for particular user id
    @Override
    public List<ApplyLeaveResponse> getAllMyLeaves(Long userId) {
        return applyLeaveRepository.findByUserId(userId)
                .stream()
                .map(leave -> {
                  ApplyLeaveResponse response = new ApplyLeaveResponse();
                    response.setId(leave.getId());
                    response.setLeaveType(leave.getLeaveType());
                    response.setFromDate(leave.getFromDate());
                    response.setToDate(leave.getToDate());
                    response.setEmailId(leave.getEmailId());
                    response.setReason(leave.getReason());
                    response.setTrail(leave.getTrail());
                    response.setComments(leave.getComments());
                    response.setStatus(leave.getStatus());
                    response.setEditable(leave.getEditable());
                    response.setCreatedAt(leave.getCreatedAt());
                    response.setApprovedBy(leave.getApprovedBy());
                    response.setRejectionReason(leave.getRejectionReason());

                    // Fetch day details for this leave
                    List<LeaveDayDetails> dayDetails = leaveDayDetailsRepository.findByLeaveId(leave.getId());
                    response.setDayDetails(dayDetails);

                    return response;
                }).toList();
    }

    // Apply Leave from user and save in "leave" table
    @Override
    public void applyLeave(ApplyLeaveRequest applyLeaveRequest) {

        String managerEmail = applyLeaveRequest.getManagerEmail();
        String employeeEmail = applyLeaveRequest.getEmailId();

        String resolvedManagerEmail =
                (managerEmail == null || managerEmail.isBlank()) ? null : managerEmail;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isManager = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + ROLE_MANAGER));

        boolean managerRaisedLeave = isManager && isSelfManagedLeave(applyLeaveRequest, resolvedManagerEmail);

        // Creating leave model from frontend payload & saving in "leave" table
        Leave leave = new Leave();
        leave.setUserId(applyLeaveRequest.getUserId());
        leave.setLeaveType(applyLeaveRequest.getLeaveType());
        leave.setFromDate(applyLeaveRequest.getFromDate());
        leave.setToDate(applyLeaveRequest.getToDate());
        leave.setEmailId(applyLeaveRequest.getEmailId());
        leave.setReason(applyLeaveRequest.getReason());
        leave.setComments(applyLeaveRequest.getComments());
        leave.setStatus(STATUS_PENDING);
        leave.setEditable(true);
        leave.setCreatedAt(LocalDate.now());
        leave.setManagerEmail(resolvedManagerEmail);
        leave.setManagerId(applyLeaveRequest.getManagerId());

        Leave savedLeave = applyLeaveRepository.save(leave);

        // saving per day details into "leave_day_details" table
        List<LeaveDayDetails> dayDetailsList = applyLeaveRequest.getDayDetails()
                .stream()
                .map(day -> {
                    LeaveDayDetails details = new LeaveDayDetails();
                    details.setLeaveId(savedLeave.getId());
                    details.setLeaveDate(day.getLeaveDate());
                    details.setDayType(day.getDayType());
                    details.setHalfDaySession(day.getHalfDaySession());
                    return details;
                }).toList();

        leaveDayDetailsRepository.saveAll(dayDetailsList);

        // Flowable starting after employee applied leave & then we are starting for Manager Review

        // Step - 1 : Creating context after Leave Request raised
        Map<String, Object> variables = new HashMap<>();
        variables.put(USER_ID, savedLeave.getUserId());
        variables.put(USER_FULL_NAME, applyLeaveRequest.getFullName());
        variables.put(LEAVE_ID, savedLeave.getId());
        variables.put(MANAGER_EMAIL, resolvedManagerEmail != null ? resolvedManagerEmail : employeeEmail);
        variables.put(MANAGER_NAME, applyLeaveRequest.getManagerName());
        variables.put(EMPLOYEE_EMAIL, savedLeave.getEmailId());
        variables.put(LEAVE_TYPE, savedLeave.getLeaveType());
        variables.put(HR_EMAIL, hrEmail);

        // Step - 2 : for calculating reminder Date
        variables.put(FROM_DATE, savedLeave.getFromDate());
        variables.put(TO_DATE, savedLeave.getToDate());
        variables.put(EMP_LEAVE_REASON, savedLeave.getReason());

        // main trail
        List<Map<String, Object>> trail = new ArrayList<>();

        // creating model for entry in trail (will be different in each step)
        Map<String, Object> entry = new HashMap<>();
        if (isManager) {
            entry.put(ROLE, ROLE_MANAGER);
            entry.put(ACTION, ACTION_MANAGER_APPLIED);
            entry.put(ACTOR_NAME, (applyLeaveRequest.getManagerName() != null && !applyLeaveRequest.getManagerName().isBlank())
                    ? applyLeaveRequest.getManagerName() : applyLeaveRequest.getFullName());
        } else {
            entry.put(ROLE, ROLE_EMPLOYEE);
            entry.put(ACTION, ACTION_LEAVE_APPLIED);
            entry.put(ACTOR_NAME, applyLeaveRequest.getFullName());
        }
        entry.put(TIME, LocalDateTime.now());
        entry.put(REASON, applyLeaveRequest.getReason());
        entry.put(IS_CURRENT_STEP, true);

        trail.add(entry);

        variables.put(TRAIL, trail);

        savedLeave.setTrail(objectMapper.valueToTree(trail));
        applyLeaveRepository.save(savedLeave);

        // Leave details (Frontend) + Current step trail (Audit) = Final Leave Details (variables)

        // RuntimeService starts the Process Instance with leave details (variables)
        ProcessInstance processInstance =
                runtimeService.startProcessInstanceByKey(LEAVE_APPROVAL_FLOW_PROCESS_ID, variables);

        if (managerRaisedLeave && taskService != null) {
            Task managerReviewTask = taskService.createTaskQuery()
                    .processInstanceId(processInstance.getId())
                    .taskDefinitionKey("managerReview")
                    .singleResult();

            if (managerReviewTask != null) {
                Map<String, Object> autoApproveVars = new HashMap<>();
                autoApproveVars.put(MANAGER_APPROVED, Boolean.TRUE);
                taskService.complete(managerReviewTask.getId(), autoApproveVars);
            }
        }
    }

    private boolean isSelfManagedLeave(ApplyLeaveRequest applyLeaveRequest, String resolvedManagerEmail) {
        if (resolvedManagerEmail == null || resolvedManagerEmail.isBlank()) {
            return true;
        }

        String employeeEmail = applyLeaveRequest.getEmailId();
        if (employeeEmail != null && employeeEmail.equalsIgnoreCase(resolvedManagerEmail)) {
            return true;
        }

        Long userId = applyLeaveRequest.getUserId();
        Long managerId = applyLeaveRequest.getManagerId();
        return userId != null && userId.equals(managerId);
    }

    // Delete Leave Request (Until it is not approved by next level)
    @Override
    @Transactional
    public void deletePendingLeave(Long userId, Long leaveId) {
        Leave leave = applyLeaveRepository.findById(leaveId)
                .orElseThrow(() -> new CustomException("Leave request not found!", 404));

        if (!userId.equals(leave.getUserId())) {
            throw new CustomException("You can delete only your own leave request!", 403);
        }

        if (!STATUS_PENDING.equalsIgnoreCase(leave.getStatus())) {
            throw new CustomException("Only pending leave requests can be deleted!", 400);
        }

        cancelActiveWorkflowInstances(leaveId);
        leave.setStatus(STATUS_DELETED);
        leave.setEditable(false);
        leave.setUpdatedAt(LocalDate.now());

        Map<String, Object> trailEntry = new HashMap<>();
        trailEntry.put(ROLE, ROLE_EMPLOYEE);
        trailEntry.put(ACTION, "Deleted");
        trailEntry.put(ACTOR_NAME, leave.getEmailId());
        trailEntry.put(TIME, LocalDateTime.now());
        trailEntry.put(REASON, leave.getReason());
        trailEntry.put(IS_CURRENT_STEP, false);

        JsonNode existingTrail = leave.getTrail();
        List<Map<String, Object>> trail = new ArrayList<>();
        if (existingTrail != null && existingTrail.isArray()) {
            trail = objectMapper.convertValue(existingTrail, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, Map.class));
        }
        trail.forEach(step -> step.put(IS_CURRENT_STEP, false));
        trail.add(trailEntry);

        leave.setTrail(objectMapper.valueToTree(trail));
        applyLeaveRepository.save(leave);

        log.info("Marked pending leave request as deleted leaveId={} for userId={}", leaveId, userId);
    }

    private void cancelActiveWorkflowInstances(Long leaveId) {
        if (runtimeService == null) {
            return;
        }

        List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                .variableValueEquals(LEAVE_ID, leaveId)
                .list();

        for (ProcessInstance processInstance : processInstances) {
            runtimeService.deleteProcessInstance(
                    processInstance.getId(),
                    "Deleted by employee while leave request is pending"
            );
        }

        if (!processInstances.isEmpty()) {
            log.info("Cancelled {} active workflow instance(s) for leaveId={}", processInstances.size(), leaveId);
        }
    }

}
