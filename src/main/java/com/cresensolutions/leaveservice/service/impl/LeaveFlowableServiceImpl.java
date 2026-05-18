package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.repository.ApplyLeaveRepository;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

@Component("leaveFlowableService")
@Slf4j
public class LeaveFlowableServiceImpl implements LeaveFlowableService {

    private final EmailService emailService;
    private final ApplyLeaveRepository applyLeaveRepository;

    public LeaveFlowableServiceImpl(EmailService emailService,
                                    ApplyLeaveRepository applyLeaveRepository) {
        this.emailService = emailService;
        this.applyLeaveRepository = applyLeaveRepository;
    }

    @Override
    public void notifyManager(DelegateExecution execution) {
        String managerEmail = (String) execution.getVariable("managerEmail");
        String employeeEmail = (String) execution.getVariable("employeeEmail");
        String leaveType = (String) execution.getVariable("leaveType");
        Long leaveId = (Long) execution.getVariable("leaveId");

        log.info("Notifying manager: {} for leave: {}", managerEmail, leaveId);
        emailService.sendLeaveNotificationToManager(managerEmail, employeeEmail, leaveType, leaveId);
    }

    @Override
    public void scheduleReminder(DelegateExecution execution) {
        log.info("Reminder scheduled for leaveId: {}", execution.getVariable("leaveId"));
    }

    @Override
    public void sendReminderMail(DelegateExecution execution) {
        String managerEmail = (String) execution.getVariable("managerEmail");
        Long leaveId = (Long) execution.getVariable("leaveId");

        log.info("Sending reminder to manager: {}", managerEmail);
        emailService.sendReminderToManager(managerEmail, leaveId);
    }

    @Override
    public void sendManagerRejectionMail(DelegateExecution execution) {
        String employeeEmail = (String) execution.getVariable("employeeEmail");
        Long leaveId = (Long) execution.getVariable("leaveId");
        String rejectionReason = (String) execution.getVariable("rejectionReason");

        applyLeaveRepository.findById(leaveId).ifPresent(leave -> {
            leave.setStatus("REJECTED");
            leave.setRejectionReason(rejectionReason);
            applyLeaveRepository.save(leave);
        });

        emailService.sendRejectionMailToEmployee(employeeEmail, leaveId, rejectionReason);
    }

    @Override
    public void sendToHR(DelegateExecution execution) {
        String hrEmail = (String) execution.getVariable("hrEmail");
        Long leaveId = (Long) execution.getVariable("leaveId");

        log.info("Sending leave to HR: {}", hrEmail);
        emailService.sendLeaveToHR(hrEmail, leaveId);
    }

    @Override
    public void sendApprovalMail(DelegateExecution execution) {
        String employeeEmail = (String) execution.getVariable("employeeEmail");
        String managerEmail = (String) execution.getVariable("managerEmail");
        Long leaveId = (Long) execution.getVariable("leaveId");

        applyLeaveRepository.findById(leaveId).ifPresent(leave -> {
            leave.setStatus("APPROVED");
            leave.setApprovedBy("HR");
            applyLeaveRepository.save(leave);
        });

        emailService.sendApprovalMail(employeeEmail, managerEmail, leaveId);
    }

    @Override
    public void sendHRRejectionMail(DelegateExecution execution) {
        String employeeEmail = (String) execution.getVariable("employeeEmail");
        String managerEmail = (String) execution.getVariable("managerEmail");
        Long leaveId = (Long) execution.getVariable("leaveId");
        String rejectionReason = (String) execution.getVariable("rejectionReason");

        applyLeaveRepository.findById(leaveId).ifPresent(leave -> {
            leave.setStatus("REJECTED");
            leave.setRejectionReason(rejectionReason);
            applyLeaveRepository.save(leave);
        });

        emailService.sendHRRejectionMail(employeeEmail, managerEmail, leaveId, rejectionReason);
    }
}