package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.MyEmpLeaveHistory;
import com.cresensolutions.leaveservice.entity.Leave;
import com.cresensolutions.leaveservice.entity.LeaveDayDetails;
import com.cresensolutions.leaveservice.repository.ApplyLeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveDayDetailsRepository;
import com.cresensolutions.leaveservice.service.impl.ManagerServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static com.cresensolutions.leaveservice.common.LeaveConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManagerServiceImplTest {

    @Mock private TaskService taskService;
    @Mock private RuntimeService runtimeService;
    @Mock private ApplyLeaveRepository leaveRepository;
    @Mock private LeaveDayDetailsRepository dayRepo;
    @Mock private EmailService emailService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private ManagerServiceImpl service;

    // TASK RETRIEVAL

    @Test
    void getManagerTasks_Success() {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task1");
        when(task.getExecutionId()).thenReturn("exec");

        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskAssignee(anyString())).thenReturn(query);
        when(query.list()).thenReturn(List.of(task));

        when(runtimeService.getVariables("exec")).thenReturn(Map.of(LEAVE_ID, 1L, USER_FULL_NAME, "Dev"));

        Leave leave = new Leave();
        leave.setId(1L);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        LeaveDayDetails d = new LeaveDayDetails();
        d.setId(10L);
        when(dayRepo.findByLeaveId(1L)).thenReturn(List.of(d));

        List<Map<String, Object>> res = service.getManagerTasks("mgr@mail.com");
        assertEquals(1, res.size());
        assertEquals("task1", res.get(0).get(TASK_ID));
    }

    // ACTION HANDLING: FULL REJECT

    @Test
    void handleManagerAction_Reject_WithReasonFallback() {
        Map<String, Object> req = new HashMap<>();
        req.put(TASK_ID, "task1");
        req.put(MANAGER_APPROVED, false);
        req.put(MANAGER_NAME, ""); // Trigger fallback to workflow name
        // Root rejectionReason is missing, will fallback to rejectedDayReasons map
        req.put("rejectedDayReasons", Map.of("10", "Busy Day"));

        Task task = mock(Task.class);
        when(task.getExecutionId()).thenReturn("exec");
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId("task1")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);

        when(runtimeService.getVariables("exec")).thenReturn(Map.of(LEAVE_ID, 1L));
        when(runtimeService.getVariable("exec", TRAIL)).thenReturn(null);
        when(runtimeService.getVariable("exec", MANAGER_NAME)).thenReturn("WorkflowManager");

        Leave leave = new Leave();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(dayRepo.findByLeaveId(1L)).thenReturn(List.of());
        when(objectMapper.valueToTree(any())).thenReturn(mock(JsonNode.class));

        service.handleManagerAction(req);

        // Verifies the reason was successfully extracted from the day-level map
        verify(taskService).complete(
                eq("task1"),
                argThat((Map<String, Object> vars) ->
                        "Busy Day".equals(vars.get(REJECTION_REASON))
                )
        );

        verify(leaveRepository).save(
                argThat(l -> STATUS_REJECTED.equals(l.getStatus()))
        );        verify(leaveRepository).save(argThat(l -> l.getStatus().equals(STATUS_REJECTED)));
    }

    // --- ACTION HANDLING: PARTIAL APPROVAL ---

    @Test
    void handleManagerAction_PartialApproval_MultipleRejectedDays() {
        Map<String, Object> req = new HashMap<>();
        req.put(TASK_ID, "task1");
        req.put(MANAGER_APPROVED, true);
        req.put(PARTIAL_APPROVED_DAY_IDS, List.of("101")); // String-to-Long conversion check
        req.put(MANAGER_NAME, "Mgr");

        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task1");
        when(task.getExecutionId()).thenReturn("exec");
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId("task1")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);

        when(runtimeService.getVariables("exec")).thenReturn(Map.of(LEAVE_ID, 1L));
        when(runtimeService.getVariable("exec", TRAIL)).thenReturn(new ArrayList<>());

        Leave original = new Leave();
        original.setId(1L);
        original.setEmailId("emp@mail.com");
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(original));
        when(leaveRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // 3 days total: 1 approved (101), 2 rejected (102, 103)
        LeaveDayDetails d1 = new LeaveDayDetails(); d1.setId(101L); d1.setLeaveDate(LocalDate.now());
        LeaveDayDetails d2 = new LeaveDayDetails(); d2.setId(102L); d2.setLeaveDate(LocalDate.now().plusDays(1));
        LeaveDayDetails d3 = new LeaveDayDetails(); d3.setId(103L); d3.setLeaveDate(LocalDate.now().plusDays(2));
        when(dayRepo.findByLeaveId(1L)).thenReturn(List.of(d1, d2, d3));

        when(objectMapper.valueToTree(any())).thenReturn(mock(JsonNode.class));

        service.handleManagerAction(req);

        // Verifies 4 saves: 1 for approved portion, 1 for original updated to rejected, 2 for additional rejected days
        verify(leaveRepository, times(4)).save(any(Leave.class));
        verify(emailService, times(2)).sendManagerRejectionMailToEmployee(anyString(), any(), anyString());
        verify(taskService).complete(eq("task1"), anyMap());
    }

    // --- HISTORY & UTILITIES ---

    @Test
    void getEmployeeLeaveHistory_WithTrailExtraction() {
        Leave leave = new Leave();

        // Use real Mapper to create a valid JsonNode structure
        ObjectMapper realMapper = new ObjectMapper();
        ArrayNode trailArray = realMapper.createArrayNode();
        ObjectNode entry = realMapper.createObjectNode();
        entry.put(ACTOR_NAME, "Tester Actor");
        trailArray.add(entry);
        leave.setTrail(trailArray);

        when(leaveRepository.findByManagerEmailOrderByCreatedAtDesc(anyString())).thenReturn(List.of(leave));

        List<MyEmpLeaveHistory> history = service.getEmployeeLeaveHistory("mgr@mail.com");
        assertEquals("Tester Actor", history.get(0).getEmployeeName());
    }

    @Test
    void toLong_ParsingEdgeCases() {
        // Test toLong logic via getManagerTasks mock
        Task task = mock(Task.class);
        when(task.getExecutionId()).thenReturn("ex");
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskAssignee(any())).thenReturn(query);
        when(query.list()).thenReturn(List.of(task));

        Map<String, Object> vars = new HashMap<>();
        vars.put(LEAVE_ID, " 999 "); // String with spaces
        when(runtimeService.getVariables("ex")).thenReturn(vars);
        assertThrows(RuntimeException.class, () -> service.getManagerTasks("test"));
    }

    @Test
    void normalizeDayIds_InvalidInput() {
        Map<String, Object> req = new HashMap<>();
        req.put(TASK_ID, "task1");
        req.put(MANAGER_APPROVED, true);
        req.put(PARTIAL_APPROVED_DAY_IDS, "invalid_type"); // Not a collection

        Task task = mock(Task.class);
        when(task.getExecutionId()).thenReturn("ex");
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId("task1")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);

        when(runtimeService.getVariables("ex")).thenReturn(Map.of(LEAVE_ID, 1L));
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(new Leave()));
        when(dayRepo.findByLeaveId(1L)).thenReturn(List.of(new LeaveDayDetails()));

        service.handleManagerAction(req);
        // If isPartial becomes false due to invalid IDs, it proceeds to handleFullDecision
        verify(taskService).complete(eq("task1"), anyMap());
    }
}