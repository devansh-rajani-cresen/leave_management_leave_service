package com.cresensolutions.leaveservice.service;

import org.flowable.engine.delegate.DelegateExecution;

public interface LeaveFlowableService {
    void notifyManager(DelegateExecution execution);
    void scheduleReminder(DelegateExecution execution);
    void sendReminderMail(DelegateExecution execution);
    void sendManagerRejectionMail(DelegateExecution execution);
    void hrReview(DelegateExecution execution);
    void deductLeaveBalance(DelegateExecution execution);
    void sendHRApprovalMail(DelegateExecution execution);
    void sendHRRejectionMail(DelegateExecution execution);
}
