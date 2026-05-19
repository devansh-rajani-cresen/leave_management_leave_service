package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.LeaveEmailContext;
import com.cresensolutions.leaveservice.entity.Leave;
import com.cresensolutions.leaveservice.repository.ApplyLeaveRepository;
import com.cresensolutions.leaveservice.service.impl.EmailServiceImpl;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mail.javamail.JavaMailSender;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private ApplyLeaveRepository applyLeaveRepository;

    @Mock
    private ResourceLoader resourceLoader;

    @Mock
    private Resource resource;

    @InjectMocks
    private EmailServiceImpl service;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "fromEmail", "test@mail.com");
    }

    // mock template
    private void mockTemplate() throws Exception {
        when(resourceLoader.getResource(any())).thenReturn(resource);
        when(resource.getContentAsString(StandardCharsets.UTF_8)).thenReturn("test-template");
    }

    private void mockLeave() {
        Leave leave = new Leave();
        leave.setId(1L);
        when(applyLeaveRepository.findById(1L)).thenReturn(Optional.of(leave));
    }

    @Test
    void sendLeaveNotificationToManager_success() throws Exception {
        LeaveEmailContext ctx = new LeaveEmailContext();
        ctx.setManagerEmail("manager@mail.com");
        ctx.setLeaveId(1L);
        ctx.setEmployeeEmail("emp@mail.com");
        ctx.setLeaveType("SICK");
        ctx.setFullName("Dev");

        MimeMessage message = mock(MimeMessage.class);

        mockTemplate();
        mockLeave();
        when(mailSender.createMimeMessage()).thenReturn(message);

        service.sendLeaveNotificationToManager(ctx);

        verify(mailSender).send(message);
    }

    @Test
    void sendLeaveNotificationToManager_noManagerEmail() {
        LeaveEmailContext ctx = new LeaveEmailContext();
        ctx.setManagerEmail(null);

        service.sendLeaveNotificationToManager(ctx);

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendReminderToManager_success() throws Exception {
        MimeMessage message = mock(MimeMessage.class);

        mockTemplate();
        mockLeave();
        when(mailSender.createMimeMessage()).thenReturn(message);

        service.sendReminderToManager("manager@mail.com", 1L, "Dev");

        verify(mailSender).send(message);
    }

    @Test
    void sendReminderToManager_skip() {
        service.sendReminderToManager(null, 1L, "Dev");

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendManagerRejectionMailToEmployee_success() throws Exception {
        MimeMessage message = mock(MimeMessage.class);

        mockTemplate();
        mockLeave();
        when(mailSender.createMimeMessage()).thenReturn(message);

        service.sendManagerRejectionMailToEmployee("emp@mail.com", 1L, "reason");

        verify(mailSender).send(message);
    }

    @Test
    void sendManagerRejectionMailToEmployee_skip() {
        service.sendManagerRejectionMailToEmployee(null, 1L, "reason");

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendLeaveForHRReview_success() throws Exception {
        MimeMessage message = mock(MimeMessage.class);

        mockTemplate();
        mockLeave();
        when(mailSender.createMimeMessage()).thenReturn(message);

        service.sendLeaveForHRReview("hr@mail.com", 1L, "Dev");

        verify(mailSender).send(message);
    }

    @Test
    void sendLeaveForHRReview_skip() {
        service.sendLeaveForHRReview(null, 1L, "Dev");

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendHRApprovalMail_success() throws Exception {
        MimeMessage message = mock(MimeMessage.class);

        mockTemplate();
        mockLeave();
        when(mailSender.createMimeMessage()).thenReturn(message);

        service.sendHRApprovalMail("emp@mail.com", "manager@mail.com", 1L, "Dev");

        verify(mailSender, atLeastOnce()).send(message);
    }

    @Test
    void sendHRRejectionMail_success() throws Exception {
        MimeMessage message = mock(MimeMessage.class);

        mockTemplate();
        mockLeave();
        when(mailSender.createMimeMessage()).thenReturn(message);

        service.sendHRRejectionMail("emp@mail.com", "manager@mail.com", 1L, "reason", "Dev");

        verify(mailSender, atLeastOnce()).send(message);
    }

    @Test
    void sendMail_exceptionHandled() throws Exception {
        mockTemplate();
        mockLeave();
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException());

        assertDoesNotThrow(() ->
                service.sendHRApprovalMail("emp@mail.com", "manager@mail.com", 1L, "Dev"));
    }
}