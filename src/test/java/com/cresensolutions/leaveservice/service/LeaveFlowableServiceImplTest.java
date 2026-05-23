package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.entity.*;
import com.cresensolutions.leaveservice.repository.*;
import com.cresensolutions.leaveservice.service.impl.LeaveFlowableServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.*;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveFlowableServiceImplTest {

    @Mock
    private EmailService emailService;

    @Mock
    private ApplyLeaveRepository leaveRepo;

    @Mock
    private EmployeeLeaveRepository empRepo;

    @Mock
    private LeaveDayDetailsRepository dayRepo;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private DelegateExecution execution;

    @InjectMocks
    private LeaveFlowableServiceImpl service;

    @Test
    void notifyManager_success() {
        // mock vars
        when(execution.getVariable("leaveId")).thenReturn(1L);
        when(execution.getVariable("fullName")).thenReturn("Dev");
        when(execution.getVariable("managerEmail")).thenReturn("m@mail.com");
        when(execution.getVariable("employeeEmail")).thenReturn("e@mail.com");
        when(execution.getVariable("leaveType")).thenReturn("SICK");
        when(execution.getVariable("userId")).thenReturn(1L);

        service.notifyManager(execution);

        verify(emailService).sendLeaveNotificationToManager(any());
    }

    @Test
    void scheduleReminder_success() {
        // set from date
        LocalDate from = LocalDate.now();
        when(execution.getVariable("fromDate")).thenReturn(from);

        service.scheduleReminder(execution);

        verify(execution).setVariable(eq("TRIGGER_MAIL_DATE"), any());
    }

    @Test
    void sendReminderMail_success() {
        // reminder mail
        when(execution.getVariable("managerEmail")).thenReturn("m@mail.com");
        when(execution.getVariable("leaveId")).thenReturn(1L);
        when(execution.getVariable("fullName")).thenReturn("Dev");

        service.sendReminderMail(execution);

        verify(emailService).sendReminderToManager("m@mail.com", 1L, "Dev");
    }

    @Test
    void sendManagerRejectionMail_success() {
        // rejection flow
        Leave leave = new Leave();
        when(execution.getVariable("employeeEmail")).thenReturn("e@mail.com");
        when(execution.getVariable("leaveId")).thenReturn(1L);
        when(execution.getVariable("rejectionReason")).thenReturn("No");

        when(leaveRepo.findById(1L)).thenReturn(Optional.of(leave));

        service.sendManagerRejectionMail(execution);

        verify(leaveRepo).save(leave);
        verify(emailService).sendManagerRejectionMailToEmployee("e@mail.com", 1L, "No");
    }

    @Test
    void hrReview_success() {
        // hr review
        when(execution.getVariable("hrEmail")).thenReturn("hr@mail.com");
        when(execution.getVariable("leaveId")).thenReturn(1L);
        when(execution.getVariable("fullName")).thenReturn("Dev");

        service.hrReview(execution);

        verify(emailService).sendLeaveForHRReview("hr@mail.com", 1L, "Dev");
    }

    @Test
    void deductLeaveBalance_success() throws Exception {
        // leave
        Leave leave = new Leave();
        leave.setLeaveType("CL");

        LeaveDayDetails d = new LeaveDayDetails();
        d.setDayType("FULL_DAY");

        EmployeeLeave emp = new EmployeeLeave();
        emp.setLeaves("{\"CL\":10.0}");

        when(execution.getVariable("leaveId")).thenReturn(1L);
        when(execution.getVariable("userId")).thenReturn(1L);
        when(leaveRepo.findById(1L)).thenReturn(Optional.of(leave));
        when(dayRepo.findByLeaveId(1L)).thenReturn(List.of(d));
        when(empRepo.findByUserId(1L)).thenReturn(Optional.of(emp));

        Map<String, Double> map = new HashMap<>();
        map.put("CL", 10.0);

        when(objectMapper.readValue(
                anyString(),
                any(com.fasterxml.jackson.core.type.TypeReference.class)
        )).thenReturn(map);

        when(objectMapper.writeValueAsString(any()))
                .thenReturn("{\"CL\":9.0}");

        service.deductLeaveBalance(execution);

        verify(empRepo).save(emp);
    }

    @Test
    void deductLeaveBalance_leaveNotFound() {
        // leave null
        when(execution.getVariable("leaveId")).thenReturn(1L);

        when(leaveRepo.findById(1L)).thenReturn(Optional.empty());

        service.deductLeaveBalance(execution);

        verifyNoInteractions(empRepo);
    }

    @Test
    void deductLeaveBalance_noDayDetails() {
        // no day details
        Leave leave = new Leave();

        when(execution.getVariable("leaveId")).thenReturn(1L);
        when(leaveRepo.findById(1L)).thenReturn(Optional.of(leave));
        when(dayRepo.findByLeaveId(1L)).thenReturn(List.of());

        service.deductLeaveBalance(execution);

        verifyNoInteractions(empRepo);
    }

    @Test
    void sendHRApprovalMail_success() {
        // approval
        Leave leave = new Leave();

        when(execution.getVariable("employeeEmail")).thenReturn("e@mail.com");
        when(execution.getVariable("managerEmail")).thenReturn("m@mail.com");
        when(execution.getVariable("leaveId")).thenReturn(1L);
        when(execution.getVariable("fullName")).thenReturn("Dev");

        when(leaveRepo.findById(1L)).thenReturn(Optional.of(leave));

        service.sendHRApprovalMail(execution);

        verify(leaveRepo).save(leave);
        verify(emailService).sendHRApprovalMail("e@mail.com", "m@mail.com", 1L, "Dev");
    }

    @Test
    void sendHRRejectionMail_success() {
        // rejection
        Leave leave = new Leave();

        when(execution.getVariable("employeeEmail")).thenReturn("e@mail.com");
        when(execution.getVariable("managerEmail")).thenReturn("m@mail.com");
        when(execution.getVariable("leaveId")).thenReturn(1L);
        when(execution.getVariable("rejectionReason")).thenReturn("No");
        when(execution.getVariable("fullName")).thenReturn("Dev");

        when(leaveRepo.findById(1L)).thenReturn(Optional.of(leave));

        service.sendHRRejectionMail(execution);

        verify(leaveRepo).save(leave);
        verify(emailService).sendHRRejectionMail("e@mail.com", "m@mail.com", 1L, "No", "Dev");
    }
}