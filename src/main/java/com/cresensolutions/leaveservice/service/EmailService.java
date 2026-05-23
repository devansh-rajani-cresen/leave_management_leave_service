package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.LeaveEmailContext;

public interface EmailService {
    void sendLeaveNotificationToManager(LeaveEmailContext context);
    void sendReminderToManager(String managerEmail, Long leaveId, String fullName);
    void sendManagerRejectionMailToEmployee(String employeeEmail, Long leaveId, String reason);
    void sendLeaveForHRReview(String hrEmail, Long leaveId, String fullName);
    void sendHRApprovalMail(String employeeEmail, String managerEmail, Long leaveId, String fullName);
    void sendHRRejectionMail(String employeeEmail, String managerEmail, Long leaveId, String reason, String fullName);
}
