package com.cresensolutions.leaveservice.service.impl;

import com.cresensolutions.leaveservice.dto.LeaveEmailContext;
import com.cresensolutions.leaveservice.entity.Leave;
import com.cresensolutions.leaveservice.repository.ApplyLeaveRepository;
import com.cresensolutions.leaveservice.service.EmailService;
import com.cresensolutions.leaveservice.service.LeaveFlowableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;
import com.cresensolutions.leaveservice.entity.EmployeeLeave;
import com.cresensolutions.leaveservice.entity.LeaveDayDetails;
import com.cresensolutions.leaveservice.repository.EmployeeLeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveDayDetailsRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import static com.cresensolutions.leaveservice.common.LeaveConstants.*;

@Component("leaveFlowableService")
@Slf4j
@RequiredArgsConstructor
public class LeaveFlowableServiceImpl implements LeaveFlowableService {

    private final EmailService emailService;
    private final ApplyLeaveRepository applyLeaveRepository;
    private final EmployeeLeaveRepository employeeLeaveRepository;
    private final LeaveDayDetailsRepository leaveDayDetailsRepository;
    private final ObjectMapper objectMapper;

    // Step - 1 : Notify Manager via mail
    @Override
    public void notifyManager(DelegateExecution execution) {

        LeaveEmailContext context = LeaveEmailContext.builder()
                .leaveId((Long) execution.getVariable(LEAVE_ID))
                .fullName((String) execution.getVariable(USER_FULL_NAME))
                .managerEmail((String) execution.getVariable(MANAGER_EMAIL))
                .employeeEmail((String) execution.getVariable(EMPLOYEE_EMAIL))
                .leaveType((String) execution.getVariable(LEAVE_TYPE))
                .userId((Long) execution.getVariable(USER_ID))
                .build();
        emailService.sendLeaveNotificationToManager(context);
    }

    // Step - 2 : Calculate date for reminder mail (from_date - 2 days)
    @Override
    public void scheduleReminder(DelegateExecution execution) {
        LocalDate fromDate = (LocalDate) execution.getVariable(FROM_DATE);
        LocalDate triggeringMailDate = fromDate.minusDays(2);
        execution.setVariable(TRIGGER_MAIL_DATE, triggeringMailDate);
    }

    // Step - 3 : If only 2 Days remaining for leave starting and no action taken by Manager
    @Override
    public void sendReminderMail(DelegateExecution execution) {
        String managerEmail = (String) execution.getVariable(MANAGER_EMAIL);
        Long leaveId = (Long) execution.getVariable(LEAVE_ID);
        String fullName = (String) execution.getVariable(USER_FULL_NAME);
        emailService.sendReminderToManager(managerEmail, leaveId, fullName);
    }

    // Step - 4 : If Manager rejected, then directly send Rejection mail to Employee
    @Override
    public void sendManagerRejectionMail(DelegateExecution execution) {
        String employeeEmail = (String) execution.getVariable(EMPLOYEE_EMAIL);
        Long leaveId = (Long) execution.getVariable(LEAVE_ID);
        String rejectionReason = (String) execution.getVariable(REJECTION_REASON);

        // Update DB leave: If manager rejected then REJECTED staus with Rejection Reason
        applyLeaveRepository.findById(leaveId).ifPresent(leave -> {
            leave.setStatus(STATUS_REJECTED);
            leave.setRejectionReason(rejectionReason);
            applyLeaveRepository.save(leave);
        });
        emailService.sendManagerRejectionMailToEmployee(employeeEmail, leaveId, rejectionReason);
    }

    // Step - 5 : Manger approved -> notifying HR for Review via mail
    @Override
    public void hrReview(DelegateExecution execution) {
        String hrEmail = (String) execution.getVariable(HR_EMAIL);
        Long leaveId = (Long) execution.getVariable(LEAVE_ID);
        String fullName = (String) execution.getVariable(USER_FULL_NAME);
        emailService.sendLeaveForHRReview(hrEmail, leaveId, fullName);
    }

    // Deduct no. of leave days from Leave Balance
    @Override
    public void deductLeaveBalance(DelegateExecution execution) {

        Long leaveId = (Long) execution.getVariable(LEAVE_ID);
        Long userId  = (Long) execution.getVariable(USER_ID);

        Leave leave = applyLeaveRepository.findById(leaveId).orElse(null);

        if (leave == null) {
            log.warn("deductLeaveBalance: Leave not found for leaveId={}", leaveId);
            return;
        }

        String leaveType = leave.getLeaveType();

        List<LeaveDayDetails> dayDetails = leaveDayDetailsRepository.findByLeaveId(leaveId);
        if (dayDetails.isEmpty()) {
            log.warn("deductLeaveBalance: No day-details found for leaveId={}", leaveId);
            return;
        }

        // Count total deduction: FULL_DAY = 1.0, HALF_DAY = 0.5
        double totalDeduction = dayDetails.stream()
                .mapToDouble(d -> DAY_TYPE_HALF_DAY.equalsIgnoreCase(d.getDayType())
                        ? DEDUCTION_HALF_DAY
                        : DEDUCTION_FULL_DAY)
                .sum();

        EmployeeLeave employeeLeave = employeeLeaveRepository.findByUserId(userId).orElse(null);
        if (employeeLeave == null) {
            log.warn("deductLeaveBalance: EmployeeLeave not found for userId={}", userId);
            return;
        }

        try {
            Map<String, Double> balanceMap = (employeeLeave.getLeaves() == null
                    || employeeLeave.getLeaves().isBlank())
                    ? new java.util.HashMap<>()
                    : objectMapper.readValue(employeeLeave.getLeaves(),
                    new TypeReference<>() {});

            double current = balanceMap.getOrDefault(leaveType, 0.0);
            if (current < totalDeduction) {
                log.warn("deductLeaveBalance: Insufficient balance userId={} leaveType={} " +
                                "available={} needed={} — deducting to 0",
                        userId, leaveType, current, totalDeduction);
            }

            balanceMap.put(leaveType, Math.max(0.0, current - totalDeduction));

            employeeLeave.setLeaves(objectMapper.writeValueAsString(balanceMap));
            employeeLeaveRepository.save(employeeLeave);
        } catch (Exception e) {
            log.error("deductLeaveBalance: Failed for userId={} leaveId={}", userId, leaveId, e);
        }
    }

    // Step - 6 : Send HR Approval mail to Manager & Employee
    @Override
    public void sendHRApprovalMail(DelegateExecution execution) {
        String employeeEmail = (String) execution.getVariable(EMPLOYEE_EMAIL);
        String managerEmail = (String) execution.getVariable(MANAGER_EMAIL);
        Long leaveId = (Long) execution.getVariable(LEAVE_ID);
        String fullName = (String) execution.getVariable(USER_FULL_NAME);

        // Update Status to APPROVED after HR Approval
        applyLeaveRepository.findById(leaveId).ifPresent(leave -> {
            leave.setStatus(STATUS_APPROVED);
            leave.setApprovedBy(APPROVED_BY_HR);
            applyLeaveRepository.save(leave);
        });
        emailService.sendHRApprovalMail(employeeEmail, managerEmail, leaveId, fullName);
    }

    @Override
    public void sendHRRejectionMail(DelegateExecution execution) {
        String employeeEmail = (String) execution.getVariable(EMPLOYEE_EMAIL);
        String managerEmail = (String) execution.getVariable(MANAGER_EMAIL);
        Long leaveId = (Long) execution.getVariable(LEAVE_ID);
        String rejectionReason = (String) execution.getVariable(REJECTION_REASON);
        String fullName = (String) execution.getVariable(USER_FULL_NAME);

        applyLeaveRepository.findById(leaveId).ifPresent(leave -> {
            leave.setStatus(STATUS_REJECTED);
            leave.setRejectionReason(rejectionReason);
            applyLeaveRepository.save(leave);
        });

        emailService.sendHRRejectionMail(employeeEmail, managerEmail, leaveId, rejectionReason, fullName);
    }
}
