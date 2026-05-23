package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.ApplyLeaveRequest;
import com.cresensolutions.leaveservice.dto.LeaveDayDetailsRequest;
import com.cresensolutions.leaveservice.entity.Leave;
import com.cresensolutions.leaveservice.repository.ApplyLeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveDayDetailsRepository;
import com.cresensolutions.leaveservice.service.impl.ApplyLeaveServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.cresensolutions.leaveservice.common.LeaveConstants.LEAVE_APPROVAL_FLOW_PROCESS_ID;
import static com.cresensolutions.leaveservice.common.LeaveConstants.MANAGER_APPROVED;
import static com.cresensolutions.leaveservice.common.LeaveConstants.ROLE_MANAGER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplyLeaveServiceImplTest {

    @Mock
    private ApplyLeaveRepository applyLeaveRepository;

    @Mock
    private LeaveDayDetailsRepository leaveDayDetailsRepository;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private TaskService taskService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void applyLeave_managerApplyingOwnLeave_autoRoutesToHrEvenWhenManagerEmailMatchesEmployee() {
        ApplyLeaveServiceImpl service = new ApplyLeaveServiceImpl(
                applyLeaveRepository,
                leaveDayDetailsRepository,
                runtimeService,
                taskService,
                objectMapper
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "manager@mail.com",
                        "password",
                        List.of(new SimpleGrantedAuthority("ROLE_" + ROLE_MANAGER))
                )
        );

        ApplyLeaveRequest request = new ApplyLeaveRequest();
        request.setUserId(7L);
        request.setFullName("Manager User");
        request.setEmailId("manager@mail.com");
        request.setManagerEmail("manager@mail.com");
        request.setManagerId(7L);
        request.setManagerName("Manager User");
        request.setLeaveType("SICK");
        request.setFromDate(LocalDate.of(2026, 5, 23));
        request.setToDate(LocalDate.of(2026, 5, 23));
        request.setReason("Medical");

        LeaveDayDetailsRequest day = new LeaveDayDetailsRequest();
        day.setLeaveDate(LocalDate.of(2026, 5, 23));
        day.setDayType("FULL_DAY");
        request.setDayDetails(List.of(day));

        Leave savedLeave = new Leave();
        savedLeave.setId(101L);
        when(applyLeaveRepository.save(any(Leave.class))).thenReturn(savedLeave);

        ProcessInstance processInstance = mock(ProcessInstance.class);
        when(processInstance.getId()).thenReturn("proc-1");
        when(runtimeService.startProcessInstanceByKey(eq(LEAVE_APPROVAL_FLOW_PROCESS_ID), anyMap()))
                .thenReturn(processInstance);

        TaskQuery taskQuery = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("proc-1")).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey("managerReview")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);

        service.applyLeave(request);

        verify(runtimeService).startProcessInstanceByKey(eq(LEAVE_APPROVAL_FLOW_PROCESS_ID), anyMap());
        verify(taskService).complete(
                eq("task-1"),
                argThat((Map<String, Object> vars) -> Boolean.TRUE.equals(vars.get(MANAGER_APPROVED)))
        );
        verify(applyLeaveRepository, times(2)).save(any(Leave.class));
    }
}
