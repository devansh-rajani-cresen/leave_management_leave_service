package com.cresensolutions.leaveservice.service.impl;

import com.cresensolutions.leaveservice.common.LeaveConstants;
import com.cresensolutions.leaveservice.dto.LeaveEmailContext;
import com.cresensolutions.leaveservice.entity.Leave;
import com.cresensolutions.leaveservice.repository.ApplyLeaveRepository;
import com.cresensolutions.leaveservice.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.cresensolutions.leaveservice.common.LeaveConstants.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final ApplyLeaveRepository applyLeaveRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.mail.from:demo@email.com}")
    private String fromEmail;

    // 1. Sending mail to Manager from default mail that employee raised leave req.
    @Override
    public void sendLeaveNotificationToManager(LeaveEmailContext context) {
        String managerEmail = context.getManagerEmail();
        String fullName = context.getFullName();
        Long leaveId = context.getLeaveId();
        String employeeEmail = context.getEmployeeEmail();
        String leaveType = context.getLeaveType();

        if (managerEmail == null || managerEmail.isBlank()) {
            log.warn("Manager email missing. Skipping start notification for leaveId: {}", leaveId);
            return;
        }

        Leave leave = applyLeaveRepository.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Leave not found for id: {}" + leaveId));

        try {
            // set up mail content
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail);
            helper.setTo(managerEmail);
            helper.setSubject("Leave request pending approval - Leave ID " + leaveId);
            helper.setText(notifyManagerMailTemplate(leave, employeeEmail, leaveType, fullName), true);

            mailSender.send(mimeMessage);
        } catch (Exception ex) {
            log.error("Failed to send manager notification from {} to {} for leaveId: {}",
                    fromEmail, managerEmail, leaveId, ex);
        }
    }

    // 2. Send Reminder mail to Manager before 2 days of fromDate (If no actions taken)
    @Override
    public void sendReminderToManager(String managerEmail, Long leaveId, String fullName) {
        if (managerEmail == null || managerEmail.isBlank()) {
            log.warn("Manager email missing. Skipping reminder for leaveId: {}", leaveId);
            return;
        }
        Leave leave = applyLeaveRepository.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Leave not found for id: " + leaveId));
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

            helper.setFrom(fromEmail);
            helper.setTo(managerEmail);
            helper.setSubject("Reminder: Leave request pending approval - Leave ID " + leaveId);
            helper.setText(reminderMailTemplate(leave, fullName), true);

            mailSender.send(mimeMessage);
        } catch (Exception ex) {
            log.error("Failed to send reminder email to {} for leaveId: {}", managerEmail, leaveId, ex);
        }
    }

    // 3. Send mail to Employee that your leave rejected by Manager
    @Override
    public void sendManagerRejectionMailToEmployee(String employeeEmail, Long leaveId, String reason) {
        if (employeeEmail == null || employeeEmail.isBlank()) {
            log.warn("Employee email missing. Skipping rejection mail for leaveId: {}", leaveId);
            return;
        }

        Leave leave = applyLeaveRepository.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Leave not found for id: " + leaveId));

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

            helper.setFrom(fromEmail);
            helper.setTo(employeeEmail);
            helper.setSubject("Leave Request Rejected - Leave ID " + leaveId);
            helper.setText(managerRejectionMailTemplate(leave, reason), true);

            mailSender.send(mimeMessage);
        } catch (Exception ex) {
            log.error("Failed to send rejection mail to {} for leaveId: {}", employeeEmail, leaveId, ex);
        }
    }

    @Override
    public void sendLeaveForHRReview(String hrEmail, Long leaveId, String fullName) {
        if (hrEmail == null || hrEmail.isBlank()){
            log.warn("HR email missing. Skipping mail for leaveId: {}", leaveId);
            return;
        }
        Leave leave = applyLeaveRepository.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Leave not found for leaveId: " + leaveId));

        try{
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

            helper.setFrom(fromEmail);
            helper.setTo(hrEmail);
            helper.setSubject("Leave Request for HR Approval - Leave ID : "+leaveId);
            helper.setText(notifyToHRTemplate(leave, fullName), true);
            mailSender.send(mimeMessage);
            log.info("Leave sent to HR for final Approval");
        } catch (Exception e){
            log.error("Error sending mail to HR for leaveId: {}", leaveId, e);
        }
    }

    // 5. Send mail to both Manager & Employee that HR accepted leave Req.
    @Override
    public void sendHRApprovalMail(String employeeEmail, String managerEmail, Long leaveId, String fullName) {

        Leave leave = applyLeaveRepository.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Leave not found for id: " + leaveId));

        sendMailIfPresent(
                employeeEmail,
                "Leave Approved - Leave ID " + leaveId,
                hrApprovalMailTemplate(leave, fullName),
                "employee",
                leaveId
        );
        sendMailIfPresent(
                managerEmail,
                "Employee Leave Approved by HR - Leave ID " + leaveId,
                hrApprovalMailTemplate(leave, fullName),
                "manager",
                leaveId
        );
    }

    // 6. Send mail to both Manager & Employee that HR Rejected leave Req.
    @Override
    public void sendHRRejectionMail(String employeeEmail, String managerEmail, Long leaveId, String reason, String fullName) {

        Leave leave = applyLeaveRepository.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Leave not found for id: " + leaveId));

        sendMailIfPresent(
                employeeEmail,
                "Your Leave Request Rejected by HR",
                hrRejectionMailTemplate(leave, reason, fullName),
                "employee",
                leaveId
        );
        sendMailIfPresent(
                managerEmail,
                "Employee Leave Rejected by HR",
                hrRejectionMailTemplate(leave, reason, fullName),
                "manager",
                leaveId
        );
    }

    // Method for Mail Template of Notify Manager mail
    private String notifyManagerMailTemplate(Leave leave, String employeeEmail, String leaveType, String fullName) throws IOException {
        Resource resource = resourceLoader.getResource(LeaveConstants.TEMPLATE_LEAVE_REQ_MANAGER);
        String template = resource.getContentAsString(StandardCharsets.UTF_8);

        return template
                .replace(PH_EMPLOYEE_EMAIL, safeValue(employeeEmail))
                .replace(PH_LEAVE_TYPE, safeValue(leaveType))
                .replace(PH_FROM_DATE, safeValue(leave.getFromDate()))
                .replace(PH_TO_DATE, safeValue(leave.getToDate()))
                .replace(PH_REASON, safeValue(leave.getReason()))
                .replace(PH_USER_FULL_NAME, safeValue(fullName))
                .replace(PH_ACTION_URL, "#");
    }

    // Method for Mail template of Reminder Mail to Manager
    private String reminderMailTemplate(Leave leave, String fullName) throws IOException {

        Resource resource = resourceLoader.getResource(LeaveConstants.TEMPLATE_REMINDER_MANAGER);
        String template = resource.getContentAsString(StandardCharsets.UTF_8);

        return template
                .replace(PH_USER_FULL_NAME, safeValue(fullName))
                .replace(PH_EMPLOYEE_EMAIL, safeValue(leave.getEmailId()))
                .replace(PH_LEAVE_TYPE, safeValue(leave.getLeaveType()))
                .replace(PH_FROM_DATE, safeValue(leave.getFromDate()))
                .replace(PH_TO_DATE, safeValue(leave.getToDate()))
                .replace(PH_REASON, safeValue(leave.getReason()))
                .replace(PH_ACTION_URL, "#");
    }

    // Updating mail template for Manager rejected leave mail
    private String managerRejectionMailTemplate(Leave leave, String reason) throws IOException {

        Resource resource = resourceLoader.getResource(LeaveConstants.TEMPLATE_MANAGER_REJECTION);
        String template = resource.getContentAsString(StandardCharsets.UTF_8);

        return template
                .replace(PH_LEAVE_TYPE, safeValue(leave.getLeaveType()))
                .replace(PH_FROM_DATE, safeValue(leave.getFromDate()))
                .replace(PH_TO_DATE, safeValue(leave.getToDate()))
                .replace(PH_REASON, safeValue(leave.getReason()))
                .replace(PH_REJECTION_REASON, safeValue(reason));
    }

    private void sendMailIfPresent(String toEmail, String subject, String body, String recipientType, Long leaveId) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("{} email missing. Skipping mail for leaveId: {}", recipientType, leaveId);
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(mimeMessage);
        } catch (Exception ex) {
            log.error("Failed to send mail: {}", String.valueOf(ex));
        }
    }

    // Send Req to HR after Manager Approval
    private String notifyToHRTemplate(Leave leave, String fullName) {

        try {
            Resource resource = resourceLoader.getResource(TEMPLATE_NOTIFY_HR);
            String template = resource.getContentAsString(StandardCharsets.UTF_8);

            return template
                    .replace(PH_USER_FULL_NAME, safeValue(fullName))
                    .replace(PH_EMPLOYEE_EMAIL, safeValue(leave.getEmailId()))
                    .replace(PH_LEAVE_TYPE, safeValue(leave.getLeaveType()))
                    .replace(PH_FROM_DATE, safeValue(leave.getFromDate()))
                    .replace(PH_TO_DATE, safeValue(leave.getToDate()))
                    .replace(PH_REASON, safeValue(leave.getReason()));
        } catch (Exception e) {
            log.error("Error loading HR template", e);
            return "<p>Error generating email</p>";
        }
    }

    // HR Approval mail template
    private String hrApprovalMailTemplate(Leave leave, String fullName) {
        try {
            Resource resource = resourceLoader.getResource(LeaveConstants.TEMPLATE_HR_APPROVAL);
            String template = resource.getContentAsString(StandardCharsets.UTF_8);

            return template
                    .replace(PH_USER_FULL_NAME, safeValue(fullName))
                    .replace(LeaveConstants.PH_LEAVE_TYPE, safeValue(leave.getLeaveType()))
                    .replace(LeaveConstants.PH_FROM_DATE, safeValue(leave.getFromDate()))
                    .replace(LeaveConstants.PH_TO_DATE, safeValue(leave.getToDate()));

        } catch (IOException e) {
            log.error("Error loading HR approval mail template", e);
            return "<p>Error generating email template</p>";
        }
    }

    // HR Rejected mail template
    private String hrRejectionMailTemplate(Leave leave, String reason, String fullName) {
        try {
            Resource resource = resourceLoader.getResource(LeaveConstants.TEMPLATE_HR_REJECTION);
            String template = resource.getContentAsString(StandardCharsets.UTF_8);

            return template
                    .replace(PH_USER_FULL_NAME, safeValue(fullName))
                    .replace(PH_LEAVE_TYPE, safeValue(leave.getLeaveType()))
                    .replace(PH_FROM_DATE, safeValue(leave.getFromDate()))
                    .replace(PH_TO_DATE, safeValue(leave.getToDate()))
                    .replace(PH_REJECTION_REASON, safeValue(reason));
        } catch (IOException e) {
            log.error("Error loading HR rejection mail template", e);
            return "<p>Error generating email template</p>";
        }
    }

    // Helper function
    private String safeValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
