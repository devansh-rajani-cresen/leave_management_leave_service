package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.entity.Leave;
import com.cresensolutions.leaveservice.repository.ApplyLeaveRepository;
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

@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final ApplyLeaveRepository applyLeaveRepository;
    private final ResourceLoader resourceLoader;
    private final String fromEmail;

    public EmailServiceImpl(JavaMailSender mailSender,
                            ApplyLeaveRepository applyLeaveRepository,
                            ResourceLoader resourceLoader,
                            @Value("${app.mail.from:abc@abc.com}") String fromEmail) {
        this.mailSender = mailSender;
        this.applyLeaveRepository = applyLeaveRepository;
        this.resourceLoader = resourceLoader;
        this.fromEmail = fromEmail;
    }

    @Override
    public void sendLeaveNotificationToManager(String managerEmail, String employeeEmail, String leaveType, Long leaveId) {
        if (managerEmail == null || managerEmail.isBlank()) {
            log.warn("Manager email missing. Skipping start notification for leaveId: {}", leaveId);
            return;
        }

        Leave leave = applyLeaveRepository.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Leave not found for id: " + leaveId));

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail);
            helper.setTo(managerEmail);
            helper.setSubject("Leave request pending approval - Leave ID " + leaveId);
            helper.setText(buildManagerNotificationHtml(leave, employeeEmail, leaveType), true);

            mailSender.send(mimeMessage);
            log.info("Manager notification sent from {} to {} for leaveId: {}", fromEmail, managerEmail, leaveId);
        } catch (Exception ex) {
            log.error("Failed to send manager notification from {} to {} for leaveId: {}",
                    fromEmail, managerEmail, leaveId, ex);
        }
    }

    @Override
    public void sendReminderToManager(String managerEmail, Long leaveId) {
        log.info("REMINDER EMAIL -> Manager: {} | LeaveId: {}", managerEmail, leaveId);
    }

    @Override
    public void sendRejectionMailToEmployee(String employeeEmail, Long leaveId, String reason) {
        log.info("REJECTION EMAIL -> Employee: {} | LeaveId: {} | Reason: {}",
                employeeEmail, leaveId, reason);
    }

    @Override
    public void sendLeaveToHR(String hrEmail, Long leaveId) {
        log.info("EMAIL -> HR: {} | LeaveId: {}", hrEmail, leaveId);
    }

    @Override
    public void sendApprovalMail(String employeeEmail, String managerEmail, Long leaveId) {
        log.info("APPROVAL EMAIL -> Employee: {}, Manager: {} | LeaveId: {}",
                employeeEmail, managerEmail, leaveId);
    }

    @Override
    public void sendHRRejectionMail(String employeeEmail, String managerEmail, Long leaveId, String reason) {
        log.info("HR REJECTION EMAIL -> Employee: {}, Manager: {} | LeaveId: {} | Reason: {}",
                employeeEmail, managerEmail, leaveId, reason);
    }

    private String buildManagerNotificationHtml(Leave leave, String employeeEmail, String leaveType) throws IOException {
        Resource resource = resourceLoader.getResource("classpath:templates/leaveReqforManager.html");
        String template = resource.getContentAsString(StandardCharsets.UTF_8);

        return template
                .replace("{{userId}}", String.valueOf(leave.getUserId()))
                .replace("{{employeeEmail}}", safeValue(employeeEmail))
                .replace("{{leaveType}}", safeValue(leaveType))
                .replace("{{fromDate}}", safeValue(leave.getFromDate()))
                .replace("{{toDate}}", safeValue(leave.getToDate()))
                .replace("{{reason}}", safeValue(leave.getReason()))
                .replace("{{actionUrl}}", "#");
    }

    private String safeValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
