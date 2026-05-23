package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.HRTaskResponse;
import com.cresensolutions.leaveservice.dto.MyEmpLeaveHistory;
import com.cresensolutions.leaveservice.entity.Leave;
import com.cresensolutions.leaveservice.entity.LeaveDayDetails;
import com.cresensolutions.leaveservice.repository.ApplyLeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveDayDetailsRepository;
import com.cresensolutions.leaveservice.service.impl.HRServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static com.cresensolutions.leaveservice.common.LeaveConstants.HR_APPROVED;
import static com.cresensolutions.leaveservice.common.LeaveConstants.LEAVE_ID;
import static com.cresensolutions.leaveservice.common.LeaveConstants.PARTIAL_APPROVAL;
import static com.cresensolutions.leaveservice.common.LeaveConstants.REJECTION_REASON;
import static com.cresensolutions.leaveservice.common.LeaveConstants.TASK_ID;
import static com.cresensolutions.leaveservice.common.LeaveConstants.TRAIL;
import static com.cresensolutions.leaveservice.common.LeaveConstants.USER_FULL_NAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HRServiceImplTest {

    private final TaskService taskService = mock(TaskService.class);
    private final RuntimeService runtimeService = mock(RuntimeService.class);
    private final ApplyLeaveRepository applyLeaveRepository = mock(ApplyLeaveRepository.class);
    private final LeaveDayDetailsRepository leaveDayDetailsRepository = mock(LeaveDayDetailsRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);

    private final HRServiceImpl service = new HRServiceImpl(
            taskService,
            runtimeService,
            applyLeaveRepository,
            leaveDayDetailsRepository,
            emailService,
            objectMapper
    );

    @Test
    void getHRTasks_success() {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task1");
        when(task.getExecutionId()).thenReturn("exec1");

        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskAssignee("hr@mail.com")).thenReturn(query);
        when(query.list()).thenReturn(List.of(task));

        // Fix: Ensure we provide variables in the way the Service impl expects them
        Map<String, Object> vars = new HashMap<>();
        vars.put(LEAVE_ID, 1L);
        vars.put(USER_FULL_NAME, "Dev");

        when(runtimeService.getVariables("exec1")).thenReturn(vars);
        when(runtimeService.getVariable("exec1", TRAIL)).thenReturn(new ArrayList<>());

        Leave leave = new Leave();
        leave.setId(1L);
        leave.setLeaveType("SICK");
        leave.setFromDate(LocalDate.now());
        leave.setToDate(LocalDate.now());

        when(applyLeaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(leaveDayDetailsRepository.findByLeaveId(1L)).thenReturn(new ArrayList<>());

        List<HRTaskResponse> response = service.getHRTasks("hr@mail.com");

        assertFalse(response.isEmpty());
        assertEquals("Dev", response.get(0).getEmployeeName());
        assertEquals("task1", response.get(0).getTaskId());
    }

    @Test
    void getHRTasks_invalidLeaveId() {

        Task task = mock(Task.class);

        when(task.getExecutionId()).thenReturn("exec1");

        TaskQuery query = mock(TaskQuery.class);

        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskAssignee("hr@mail.com")).thenReturn(query);
        when(query.list()).thenReturn(List.of(task));

        Map<String, Object> vars = new HashMap<>();
        vars.put(LEAVE_ID, "abc");

        when(runtimeService.getVariables("exec1")).thenReturn(vars);

        List<HRTaskResponse> response =
                service.getHRTasks("hr@mail.com");

        assertTrue(response.isEmpty());
    }

    @Test
    void getHRTasks_leaveNotFound() {

        Task task = mock(Task.class);

        when(task.getExecutionId()).thenReturn("exec1");

        TaskQuery query = mock(TaskQuery.class);

        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskAssignee("hr@mail.com")).thenReturn(query);
        when(query.list()).thenReturn(List.of(task));

        Map<String, Object> vars = new HashMap<>();
        vars.put(LEAVE_ID, 1L);

        when(runtimeService.getVariables("exec1")).thenReturn(vars);

        when(applyLeaveRepository.findById(1L))
                .thenReturn(Optional.empty());

        List<HRTaskResponse> response =
                service.getHRTasks("hr@mail.com");

        assertTrue(response.isEmpty());
    }

    @Test
    void getHRTasks_trailFallbackFromDb() {

        Task task = mock(Task.class);

        when(task.getId()).thenReturn("task1");
        when(task.getExecutionId()).thenReturn("exec1");

        TaskQuery query = mock(TaskQuery.class);

        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskAssignee("hr@mail.com")).thenReturn(query);
        when(query.list()).thenReturn(List.of(task));

        Map<String, Object> vars = new HashMap<>();
        vars.put(LEAVE_ID, 1L);
        vars.put(USER_FULL_NAME, "Dev");

        when(runtimeService.getVariables("exec1")).thenReturn(vars);

        Leave leave = new Leave();
        leave.setId(1L);

        JsonNode node = mock(JsonNode.class);

        leave.setTrail(node);

        when(node.isNull()).thenReturn(false);

        when(applyLeaveRepository.findById(1L))
                .thenReturn(Optional.of(leave));

        when(runtimeService.getVariable("exec1", TRAIL))
                .thenReturn(null);

        when(objectMapper.convertValue(
                eq(node),
                any(com.fasterxml.jackson.core.type.TypeReference.class)
        )).thenReturn(new ArrayList<>());

        when(leaveDayDetailsRepository.findByLeaveId(1L))
                .thenReturn(List.of());

        List<HRTaskResponse> response =
                service.getHRTasks("hr@mail.com");

        assertEquals(1, response.size());
    }

    @Test
    void handleHRAction_approved() {

        Map<String, Object> request = new HashMap<>();

        request.put(TASK_ID, "task1");
        request.put(HR_APPROVED, true);
        request.put("hrName", "HR");

        Task task = mock(Task.class);

        when(task.getExecutionId()).thenReturn("exec1");

        TaskQuery query = mock(TaskQuery.class);

        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId("task1")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);

        when(runtimeService.getVariable("exec1", TRAIL))
                .thenReturn(new ArrayList<Map<String, Object>>());

        when(runtimeService.getVariable("exec1", LEAVE_ID))
                .thenReturn(1L);

        when(leaveDayDetailsRepository.findByLeaveId(1L))
                .thenReturn(List.of());

        Leave leave = new Leave();

        when(applyLeaveRepository.findById(1L))
                .thenReturn(Optional.of(leave));

        JsonNode node = mock(JsonNode.class);

        when(objectMapper.valueToTree(any()))
                .thenReturn(node);

        service.handleHRAction(request);

        verify(taskService).complete(
                eq("task1"),
                argThat((Map<String, Object> map) ->
                        Boolean.TRUE.equals(map.get(HR_APPROVED))
                )
        );
    }

    @Test
    void handleHRAction_rejected() {

        Map<String, Object> request = new HashMap<>();

        request.put(TASK_ID, "task1");
        request.put(HR_APPROVED, false);
        request.put("hrName", "HR");
        request.put(REJECTION_REASON, "Rejected");

        Task task = mock(Task.class);

        when(task.getExecutionId()).thenReturn("exec1");

        TaskQuery query = mock(TaskQuery.class);

        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId("task1")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);

        when(runtimeService.getVariable("exec1", TRAIL))
                .thenReturn(new ArrayList<Map<String, Object>>());

        when(runtimeService.getVariable("exec1", LEAVE_ID))
                .thenReturn(1L);

        when(runtimeService.getVariable("exec1", USER_FULL_NAME))
                .thenReturn("Dev");

        Leave leave = new Leave();

        leave.setId(1L);
        leave.setEmailId("emp@mail.com");
        leave.setManagerEmail("manager@mail.com");

        when(applyLeaveRepository.findById(1L))
                .thenReturn(Optional.of(leave));

        LeaveDayDetails day = new LeaveDayDetails();

        day.setId(1L);
        day.setLeaveDate(LocalDate.now());

        when(leaveDayDetailsRepository.findByLeaveId(1L))
                .thenReturn(List.of(day));

        JsonNode node = mock(JsonNode.class);

        when(objectMapper.valueToTree(any()))
                .thenReturn(node);

        service.handleHRAction(request);

        verify(taskService).complete(
                eq("task1"),
                argThat((Map<String, Object> map) ->
                        Boolean.FALSE.equals(map.get(HR_APPROVED))
                )
        );
    }

    @Test
    void handleHRAction_partialApproval() {

        Map<String, Object> request = new HashMap<>();

        request.put(TASK_ID, "task1");
        request.put(HR_APPROVED, true);
        request.put("hrName", "HR");
        request.put("partialApprovedDayIds", List.of(11L));

        Task task = mock(Task.class);

        when(task.getId()).thenReturn("task1");
        when(task.getExecutionId()).thenReturn("exec1");

        TaskQuery query = mock(TaskQuery.class);

        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId("task1")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);

        when(runtimeService.getVariable("exec1", TRAIL))
                .thenReturn(new ArrayList<Map<String, Object>>());

        when(runtimeService.getVariable("exec1", LEAVE_ID))
                .thenReturn(1L);

        when(runtimeService.getVariable("exec1", USER_FULL_NAME))
                .thenReturn("Dev");

        Leave leave = new Leave();

        leave.setId(1L);
        leave.setUserId(1L);
        leave.setEmailId("emp@mail.com");
        leave.setManagerEmail("manager@mail.com");
        leave.setLeaveType("SICK");

        when(applyLeaveRepository.findById(1L))
                .thenReturn(Optional.of(leave));

        when(applyLeaveRepository.save(any()))
                .thenAnswer(invocation -> {
                    Leave l = invocation.getArgument(0);
                    if (l.getId() == null) {
                        l.setId(2L);
                    }
                    return l;
                });

        LeaveDayDetails d1 = new LeaveDayDetails();
        d1.setId(11L);
        d1.setLeaveDate(LocalDate.now());

        LeaveDayDetails d2 = new LeaveDayDetails();
        d2.setId(12L);
        d2.setLeaveDate(LocalDate.now().plusDays(1));

        when(leaveDayDetailsRepository.findByLeaveId(1L))
                .thenReturn(List.of(d1, d2));

        JsonNode node = mock(JsonNode.class);

        when(objectMapper.valueToTree(any()))
                .thenReturn(node);

        service.handleHRAction(request);

        verify(taskService).complete(
                eq("task1"),
                argThat((Map<String, Object> map) ->
                        Boolean.TRUE.equals(map.get(HR_APPROVED))
                                && Boolean.TRUE.equals(map.get(PARTIAL_APPROVAL))
                )
        );

        verify(runtimeService).setVariable("exec1", LEAVE_ID, 2L);
    }

    @Test
    void getAllLeaveHistory_success() {

        Leave leave = new Leave();

        leave.setId(1L);
        leave.setEmailId("emp@mail.com");
        leave.setManagerEmail("manager@mail.com");
        leave.setLeaveType("SICK");
        leave.setStatus("APPROVED");

        JsonNode trail = mock(JsonNode.class);
        JsonNode actor = mock(JsonNode.class);

        leave.setTrail(trail);

        when(trail.isArray()).thenReturn(true);
        when(trail.isEmpty()).thenReturn(false);
        when(trail.get(0)).thenReturn(actor);
        when(actor.path("actorName")).thenReturn(actor);
        when(actor.asText(null)).thenReturn("Dev");

        when(applyLeaveRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(leave));

        List<MyEmpLeaveHistory> response =
                service.getAllLeaveHistory();

        assertEquals(1, response.size());
        assertEquals("Dev", response.get(0).getEmployeeName());
    }

    @Test
    void getAllLeaveHistory_nullTrail() {

        Leave leave = new Leave();

        leave.setTrail(null);

        when(applyLeaveRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(leave));

        List<MyEmpLeaveHistory> response =
                service.getAllLeaveHistory();

        assertNull(response.get(0).getEmployeeName());
    }

    @Test
    void getAllLeaveHistory_emptyTrail() {

        Leave leave = new Leave();

        JsonNode trail = mock(JsonNode.class);

        leave.setTrail(trail);

        when(trail.isArray()).thenReturn(true);
        when(trail.isEmpty()).thenReturn(true);

        when(applyLeaveRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(leave));

        List<MyEmpLeaveHistory> response =
                service.getAllLeaveHistory();

        assertNull(response.get(0).getEmployeeName());
    }

    @Test
    void handleHRAction_rejected_WithRejectedDayReasons() {

        Map<String, Object> request = new HashMap<>();

        request.put(TASK_ID, "task1");
        request.put(HR_APPROVED, false);
        request.put("hrName", "HR");
        request.put("rejectedDayReasons", Map.of("1", "Invalid"));

        Task task = mock(Task.class);

        when(task.getExecutionId()).thenReturn("exec1");

        TaskQuery query = mock(TaskQuery.class);

        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId("task1")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);

        when(runtimeService.getVariable("exec1", TRAIL))
                .thenReturn(new ArrayList<Map<String, Object>>());

        when(runtimeService.getVariable("exec1", LEAVE_ID))
                .thenReturn(1L);

        when(runtimeService.getVariable("exec1", USER_FULL_NAME))
                .thenReturn("Dev");

        Leave leave = new Leave();

        leave.setId(1L);
        leave.setEmailId("emp@mail.com");
        leave.setManagerEmail("manager@mail.com");

        when(applyLeaveRepository.findById(1L))
                .thenReturn(Optional.of(leave));

        LeaveDayDetails day = new LeaveDayDetails();

        day.setId(1L);
        day.setLeaveDate(LocalDate.now());

        when(leaveDayDetailsRepository.findByLeaveId(1L))
                .thenReturn(List.of(day));

        when(objectMapper.valueToTree(any()))
                .thenReturn(mock(JsonNode.class));

        service.handleHRAction(request);

        verify(emailService).sendHRRejectionMail(
                anyString(),
                anyString(),
                anyLong(),
                eq("Invalid"),
                eq("Dev")
        );
    }

    @Test
    void handleHRAction_partialApproval_EmptyTrail() {

        Map<String, Object> request = new HashMap<>();

        request.put(TASK_ID, "task1");
        request.put(HR_APPROVED, true);
        request.put("hrName", "HR");
        request.put("partialApprovedDayIds", List.of(1L));

        Task task = mock(Task.class);

        when(task.getId()).thenReturn("task1");
        when(task.getExecutionId()).thenReturn("exec1");

        TaskQuery query = mock(TaskQuery.class);

        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId("task1")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);

        when(runtimeService.getVariable("exec1", TRAIL))
                .thenReturn(null);

        when(runtimeService.getVariable("exec1", LEAVE_ID))
                .thenReturn(1L);

        when(runtimeService.getVariable("exec1", USER_FULL_NAME))
                .thenReturn("Dev");

        Leave leave = new Leave();

        leave.setId(1L);
        leave.setUserId(1L);

        when(applyLeaveRepository.findById(1L))
                .thenReturn(Optional.of(leave));

        when(applyLeaveRepository.save(any()))
                .thenAnswer(invocation -> {
                    Leave l = invocation.getArgument(0);
                    if (l.getId() == null) {
                        l.setId(2L);
                    }
                    return l;
                });

        LeaveDayDetails d1 = new LeaveDayDetails();
        d1.setId(1L);
        d1.setLeaveDate(LocalDate.now());

        LeaveDayDetails d2 = new LeaveDayDetails();
        d2.setId(2L);
        d2.setLeaveDate(LocalDate.now().plusDays(1));

        when(leaveDayDetailsRepository.findByLeaveId(1L))
                .thenReturn(List.of(d1, d2));

        when(objectMapper.valueToTree(any()))
                .thenReturn(mock(JsonNode.class));

        service.handleHRAction(request);

        verify(taskService).complete(
                eq("task1"),
                anyMap()
        );
    }

    @Test
    void getHRTasks_NullLeaveId() {

        Task task = mock(Task.class);

        when(task.getExecutionId()).thenReturn("exec1");

        TaskQuery query = mock(TaskQuery.class);

        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskAssignee("hr@mail.com")).thenReturn(query);
        when(query.list()).thenReturn(List.of(task));

        when(runtimeService.getVariables("exec1"))
                .thenReturn(new HashMap<>());

        List<HRTaskResponse> response =
                service.getHRTasks("hr@mail.com");

        assertTrue(response.isEmpty());
    }
}
