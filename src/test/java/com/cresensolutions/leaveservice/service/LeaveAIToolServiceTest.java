package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.client.UserClient;
import com.cresensolutions.leaveservice.dto.aidto.BasicUserInfoForAI;
import com.cresensolutions.leaveservice.dto.aidto.TablePayloadDTO;
import com.cresensolutions.leaveservice.entity.EmployeeLeave;
import com.cresensolutions.leaveservice.entity.LeaveType;
import com.cresensolutions.leaveservice.repository.ApplyLeaveRepository;
import com.cresensolutions.leaveservice.repository.EmployeeLeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveRepository;
import com.cresensolutions.leaveservice.repository.PublicHolidayRepository;
import com.cresensolutions.leaveservice.security.UserPrincipal;
import com.cresensolutions.leaveservice.service.ai.aiservice.LeaveAIToolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.cresensolutions.leaveservice.common.LeaveConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveAIToolServiceTest {

    @Mock
    private ApplyLeaveRepository applyLeaveRepository;

    @Mock
    private PublicHolidayRepository publicHolidayRepository;

    @Mock
    private EmployeeLeaveRepository employeeLeaveRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private LeaveRepository leaveRepository;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private LeaveAIToolService leaveAIToolService;

    @BeforeEach
    void setup() {
        UserPrincipal principal = mock(UserPrincipal.class);

        lenient().when(principal.getUserId()).thenReturn(1L);
        lenient().when(principal.getRole()).thenReturn("ADMIN");

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null);

        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void testGetToolsForRole_Admin() {
        Object[] tools = leaveAIToolService.getToolsForRole("ADMIN");
        assertEquals(2, tools.length);
    }

    @Test
    void testGetToolsForRole_Employee() {
        Object[] tools = leaveAIToolService.getToolsForRole("EMPLOYEE");
        assertEquals(1, tools.length);
    }

    @Test
    void testTryHandleDeterministicQuery_Null() {
        assertNull(leaveAIToolService.tryHandleDeterministicQuery(null, "EMPLOYEE"));
    }

    @Test
    void testTryHandleDeterministicQuery_ApprovedCount() {
        when(applyLeaveRepository.countByUserIdAndStatus(1L, STATUS_APPROVED))
                .thenReturn(2L);

        String response = leaveAIToolService.tryHandleDeterministicQuery(
                "how many approved leaves",
                "EMPLOYEE"
        );

        assertTrue(response.contains("2"));
    }

    @Test
    void testTryHandleDeterministicQuery_Pending() {
        when(applyLeaveRepository.findByUserIdAndStatusOrderByFromDateDesc(
                1L,
                STATUS_PENDING
        )).thenReturn(List.of());

        String response = leaveAIToolService.tryHandleDeterministicQuery(
                "pending leaves",
                "EMPLOYEE"
        );

        assertTrue(response.contains("no PENDING"));
    }

    @Test
    void testIsRawToolCallResponse_True() {
        assertTrue(
                leaveAIToolService.isRawToolCallResponse("{\"name\":\"abc\",\"parameters\":{}}")
        );
    }

    @Test
    void testIsRawToolCallResponse_False() {
        assertFalse(leaveAIToolService.isRawToolCallResponse("hello"));
    }

    @Test
    void testBuildStructuredTablePayload_LeaveRequest() {
        String content = """
                Your approved leave requests:
                - Casual Leave | 2025-01-01 to 2025-01-02 | Approved by: HR
                """;

        TablePayloadDTO dto = leaveAIToolService.buildStructuredTablePayload(content);

        assertNotNull(dto);
        assertEquals("Approved Leave Requests", dto.getTableTitle());
    }

    @Test
    void testBuildStructuredTablePayload_UsersByRole() {
        String content = """
                Users with role MANAGER:
                - John Doe (john@gmail.com)
                """;

        TablePayloadDTO dto = leaveAIToolService.buildStructuredTablePayload(content);

        assertNotNull(dto);
        assertEquals("Manager List", dto.getTableTitle());
    }

    @Test
    void testBuildStructuredTablePayload_SearchResults() {
        String content = """
                Search results for "john":
                - John Doe | Role: MANAGER | Email: john@gmail.com
                """;

        TablePayloadDTO dto = leaveAIToolService.buildStructuredTablePayload(content);

        assertNotNull(dto);
        assertTrue(dto.getTableTitle().contains("john"));
    }

    @Test
    void testBuildStructuredTablePayload_SingleUser() {
        String content = """
                Employee Details:
                Name: John Doe
                Email: john@gmail.com
                """;

        TablePayloadDTO dto = leaveAIToolService.buildStructuredTablePayload(content);

        assertNotNull(dto);
    }

    @Test
    void testGetApprovedLeaveCountText_Zero() {
        when(applyLeaveRepository.countByUserIdAndStatus(1L, STATUS_APPROVED))
                .thenReturn(0L);

        String response = leaveAIToolService.getApprovedLeaveCountText();

        assertTrue(response.contains("no any approved"));
    }

    @Test
    void testGetApprovedLeaveCountText_Success() {
        when(applyLeaveRepository.countByUserIdAndStatus(1L, STATUS_APPROVED))
                .thenReturn(5L);

        String response = leaveAIToolService.getApprovedLeaveCountText();

        assertTrue(response.contains("5"));
    }

    @Test
    void testGetPendingLeaveCountText() {
        when(applyLeaveRepository.countByUserIdAndStatus(1L, STATUS_PENDING))
                .thenReturn(3L);

        String response = leaveAIToolService.getPendingLeaveCountText();

        assertTrue(response.contains("3"));
    }

    @Test
    void testGetRejectedLeaveCountText() {
        when(applyLeaveRepository.countByUserIdAndStatus(1L, STATUS_REJECTED))
                .thenReturn(1L);

        String response = leaveAIToolService.getRejectedLeaveCountText();

        assertTrue(response.contains("1"));
    }

    @Test
    void testGetMyLeaveHistoryText_Empty() {
        when(applyLeaveRepository.findByUserId(1L))
                .thenReturn(List.of());

        String response = leaveAIToolService.getMyLeaveHistoryText();

        assertTrue(response.contains("not applied"));
    }

    @Test
    void testGetAvailableLeaveBalanceText_NoData() {
        when(employeeLeaveRepository.findByUserId(1L))
                .thenReturn(Optional.empty());

        String response = leaveAIToolService.getAvailableLeaveBalanceText();

        assertTrue(response.contains("No leave balance"));
    }

    @Test
    void testGetAvailableLeaveBalanceText_Success() throws Exception {
        EmployeeLeave employeeLeave = mock(EmployeeLeave.class);

        when(employeeLeave.getLeaves()).thenReturn("{\"CL\":5}");

        when(employeeLeaveRepository.findByUserId(1L))
                .thenReturn(Optional.of(employeeLeave));

        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(Map.of("CL", 5));

        String response = leaveAIToolService.getAvailableLeaveBalanceText();

        assertTrue(response.contains("CL"));
    }

    @Test
    void testGetAvailableLeaveBalanceText_Exception() throws Exception {
        EmployeeLeave employeeLeave = mock(EmployeeLeave.class);

        when(employeeLeave.getLeaves()).thenReturn("invalid");

        when(employeeLeaveRepository.findByUserId(1L))
                .thenReturn(Optional.of(employeeLeave));

        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenThrow(new RuntimeException());

        String response = leaveAIToolService.getAvailableLeaveBalanceText();

        assertTrue(response.contains("Unable"));
    }

    @Test
    void testGetUpcomingPublicHolidaysText_Empty() {
        when(publicHolidayRepository.findByHolidayDateBetween(any(), any()))
                .thenReturn(List.of());

        String response = leaveAIToolService.getUpcomingPublicHolidaysText();

        assertTrue(response.contains("no public holidays"));
    }

    @Test
    void testGetYearlyLeaveCreditsText_Empty() {
        when(leaveRepository.findAll()).thenReturn(List.of());

        String response = leaveAIToolService.getYearlyLeaveCreditsText();

        assertTrue(response.contains("No leave types"));
    }

    @Test
    void testGetYearlyLeaveCreditsText_Success() {
        LeaveType leaveType = mock(LeaveType.class);

        when(leaveType.getLeaveName()).thenReturn("Casual");
        when(leaveType.getMaxDays()).thenReturn(10);

        when(leaveRepository.findAll()).thenReturn(List.of(leaveType));

        String response = leaveAIToolService.getYearlyLeaveCreditsText();

        assertTrue(response.contains("Casual"));
    }

    @Test
    void testGetTotalEmployeesCountText() {
        when(userClient.getEmployeeCount()).thenReturn(50L);

        String response = leaveAIToolService.getTotalEmployeesCountText();

        assertTrue(response.contains("50"));
    }

    @Test
    void testGetUsersByRoleText() {
        BasicUserInfoForAI user = new BasicUserInfoForAI(
                1L,
                "John",
                "MANAGER",
                "john@gmail.com"
        );
        user.setFullName("John");
        user.setEmail("john@gmail.com");

        when(userClient.getUserByRole("MANAGER"))
                .thenReturn(List.of(user));

        String response = leaveAIToolService.getUsersByRoleText("MANAGER");

        assertTrue(response.contains("John"));
    }

    @Test
    void testGetTotalPendingLeavesText() {
        when(applyLeaveRepository.countLeaveByStatus(STATUS_PENDING))
                .thenReturn(4L);

        String response = leaveAIToolService.getTotalPendingLeavesText();

        assertTrue(response.contains("4"));
    }

    @Test
    void testGetEmployeesOnLeaveTodayText() {
        when(applyLeaveRepository
                .countByStatusAndFromDateLessThanEqualAndToDateGreaterThanEqual(
                        eq(STATUS_APPROVED),
                        any(LocalDate.class),
                        any(LocalDate.class)
                )).thenReturn(2L);

        String response = leaveAIToolService.getEmployeesOnLeaveTodayText();

        assertTrue(response.contains("2"));
    }

    @Test
    void testGetTotalLeaveCountText() {
        when(applyLeaveRepository.countByUserId(1L))
                .thenReturn(7L);

        String response = leaveAIToolService.getTotalLeaveCountText();

        assertTrue(response.contains("7"));
    }

    @Test
    void testGetTotalApprovedLeavesText() {
        when(applyLeaveRepository.countLeaveByStatus(STATUS_APPROVED))
                .thenReturn(6L);

        String response = leaveAIToolService.getTotalApprovedLeavesText();

        assertTrue(response.contains("6"));
    }

    @Test
    void testGetTotalRejectedLeavesText() {
        when(applyLeaveRepository.countLeaveByStatus(STATUS_REJECTED))
                .thenReturn(2L);

        String response = leaveAIToolService.getTotalRejectedLeavesText();

        assertTrue(response.contains("2"));
    }

    @Test
    void testAdminToolsMethods() {
        LeaveAIToolService.AdminTools adminTools =
                leaveAIToolService.new AdminTools();

        when(userClient.getEmployeeCount()).thenReturn(10L);

        assertNotNull(adminTools.getTotalEmployeesCount());
    }

    @Test
    void testEmployeeToolsMethods() {
        LeaveAIToolService.EmployeeTools employeeTools =
                leaveAIToolService.new EmployeeTools();

        when(applyLeaveRepository.countByUserId(1L)).thenReturn(1L);

        assertNotNull(employeeTools.getTotalLeaveCount());
    }

    @Test
    void testTryHandleDeterministicQuery_Rejected() {
        when(applyLeaveRepository.countByUserIdAndStatus(1L, STATUS_REJECTED))
                .thenReturn(2L);

        String response = leaveAIToolService.tryHandleDeterministicQuery(
                "how many rejected leaves",
                "EMPLOYEE"
        );

        assertNotNull(response);
    }

    @Test
    void testTryHandleDeterministicQuery_Balance() {
        when(employeeLeaveRepository.findByUserId(1L))
                .thenReturn(Optional.empty());

        String response = leaveAIToolService.tryHandleDeterministicQuery(
                "leave balance",
                "EMPLOYEE"
        );

        assertNotNull(response);
    }

    @Test
    void testTryHandleDeterministicQuery_Holiday() {
        when(publicHolidayRepository.findByHolidayDateBetween(any(), any()))
                .thenReturn(List.of());

        String response = leaveAIToolService.tryHandleDeterministicQuery(
                "public holidays",
                "EMPLOYEE"
        );

        assertNotNull(response);
    }

    @Test
    void testTryHandleDeterministicQuery_Policy() {
        when(leaveRepository.findAll()).thenReturn(List.of());

        String response = leaveAIToolService.tryHandleDeterministicQuery(
                "leave policy",
                "EMPLOYEE"
        );

        assertNotNull(response);
    }

    @Test
    void testTryHandleDeterministicQuery_History() {
        when(applyLeaveRepository.findByUserId(1L))
                .thenReturn(List.of());

        String response = leaveAIToolService.tryHandleDeterministicQuery(
                "my leave history",
                "EMPLOYEE"
        );

        assertNotNull(response);
    }

    @Test
    void testTryHandleDeterministicQuery_ManagerQuery() {
        when(applyLeaveRepository
                .countByStatusAndFromDateLessThanEqualAndToDateGreaterThanEqual(
                        eq(STATUS_APPROVED),
                        any(),
                        any()
                )).thenReturn(1L);

        String response = leaveAIToolService.tryHandleDeterministicQuery(
                "employees on leave today",
                "MANAGER"
        );

        assertNotNull(response);
    }

    @Test
    void testTryHandleDeterministicQuery_NoMatch() {
        String response = leaveAIToolService.tryHandleDeterministicQuery(
                "random query",
                "EMPLOYEE"
        );

        assertNull(response);
    }

    @Test
    void testIsRawToolCallResponse_Null() {
        assertFalse(leaveAIToolService.isRawToolCallResponse(null));
    }

    @Test
    void testBuildStructuredTablePayload_Null() {
        assertNull(leaveAIToolService.buildStructuredTablePayload(null));
    }

    @Test
    void testBuildStructuredTablePayload_Invalid() {
        assertNull(
                leaveAIToolService.buildStructuredTablePayload("random text")
        );
    }

    @Test
    void testGetUsersByRoleText_Empty() {
        when(userClient.getUserByRole("MANAGER"))
                .thenReturn(List.of());

        String response = leaveAIToolService.getUsersByRoleText("MANAGER");

        assertTrue(response.contains("No users found"));
    }

    @Test
    void testGetTotalEmployeesCountText_Null() {
        when(userClient.getEmployeeCount()).thenReturn(null);

        String response = leaveAIToolService.getTotalEmployeesCountText();

        assertTrue(response.contains("Unable"));
    }

    @Test
    void testGetEmployeesOnLeaveTodayText_Zero() {
        when(applyLeaveRepository
                .countByStatusAndFromDateLessThanEqualAndToDateGreaterThanEqual(
                        eq(STATUS_APPROVED),
                        any(),
                        any()
                )).thenReturn(0L);

        String response = leaveAIToolService.getEmployeesOnLeaveTodayText();

        assertTrue(response.contains("No employees"));
    }

    @Test
    void testGetTotalLeaveCountText_Zero() {
        when(applyLeaveRepository.countByUserId(1L))
                .thenReturn(0L);

        String response = leaveAIToolService.getTotalLeaveCountText();

        assertTrue(response.contains("not applied"));
    }

    @Test
    void testGetTotalApprovedLeavesText_Zero() {
        when(applyLeaveRepository.countLeaveByStatus(STATUS_APPROVED))
                .thenReturn(0L);

        String response = leaveAIToolService.getTotalApprovedLeavesText();

        assertTrue(response.contains("no approved"));
    }

    @Test
    void testGetTotalRejectedLeavesText_Zero() {
        when(applyLeaveRepository.countLeaveByStatus(STATUS_REJECTED))
                .thenReturn(0L);

        String response = leaveAIToolService.getTotalRejectedLeavesText();

        assertTrue(response.contains("no rejected"));
    }

    @Test
    void testGetTotalPendingLeavesText_Zero() {
        when(applyLeaveRepository.countLeaveByStatus(STATUS_PENDING))
                .thenReturn(0L);

        String response = leaveAIToolService.getTotalPendingLeavesText();

        assertTrue(response.contains("no pending"));
    }

    @Test
    void testAdminToolMethods() {

        LeaveAIToolService.AdminTools tools =
                leaveAIToolService.new AdminTools();

        when(userClient.getEmployeeCount()).thenReturn(5L);

        assertNotNull(tools.getTotalEmployeesCount());

        when(applyLeaveRepository.countLeaveByStatus(STATUS_PENDING))
                .thenReturn(1L);

        assertNotNull(tools.getTotalPendingLeaves());

        when(applyLeaveRepository.countLeaveByStatus(STATUS_APPROVED))
                .thenReturn(1L);

        assertNotNull(tools.getTotalApprovedLeaves());

        when(applyLeaveRepository.countLeaveByStatus(STATUS_REJECTED))
                .thenReturn(1L);

        assertNotNull(tools.getTotalRejectedLeaves());
    }

    @Test
    void testEmployeeToolMethods_All() {

        LeaveAIToolService.EmployeeTools tools =
                leaveAIToolService.new EmployeeTools();

        when(applyLeaveRepository.countByUserId(1L))
                .thenReturn(1L);

        assertNotNull(tools.getTotalLeaveCount());

        when(applyLeaveRepository.findByUserId(1L))
                .thenReturn(List.of());

        assertNotNull(tools.getMyLeaveHistory());

        when(applyLeaveRepository.countByUserIdAndStatus(1L, STATUS_PENDING))
                .thenReturn(1L);

        assertNotNull(tools.getPendingLeaveCount());

        when(applyLeaveRepository.countByUserIdAndStatus(1L, STATUS_APPROVED))
                .thenReturn(1L);

        assertNotNull(tools.getApprovedLeaveCount());

        when(applyLeaveRepository.countByUserIdAndStatus(1L, STATUS_REJECTED))
                .thenReturn(1L);

        assertNotNull(tools.getRejectedLeaveCount());
    }

    @Test
    void testGetApprovedLeavesText_WithApprovedBy() {

        var leave = mock(com.cresensolutions.leaveservice.entity.Leave.class);

        when(leave.getLeaveType()).thenReturn("CL");
        when(leave.getFromDate()).thenReturn(LocalDate.now());
        when(leave.getToDate()).thenReturn(LocalDate.now());
        when(leave.getApprovedBy()).thenReturn("Manager");

        when(applyLeaveRepository
                .findByUserIdAndStatusOrderByFromDateDesc(1L, STATUS_APPROVED))
                .thenReturn(List.of(leave));

        String response = leaveAIToolService.getApprovedLeavesText();

        assertTrue(response.contains("Approved by"));
    }

    @Test
    void testGetApprovedLeavesText_Empty() {

        when(applyLeaveRepository
                .findByUserIdAndStatusOrderByFromDateDesc(1L, STATUS_APPROVED))
                .thenReturn(List.of());

        String response = leaveAIToolService.getApprovedLeavesText();

        assertTrue(response.contains("no APPROVED"));
    }

    @Test
    void testGetMyLeaveHistoryText_Success() {

        var leave = mock(com.cresensolutions.leaveservice.entity.Leave.class);

        when(leave.getLeaveType()).thenReturn("CL");
        when(leave.getFromDate()).thenReturn(LocalDate.now());
        when(leave.getToDate()).thenReturn(LocalDate.now());
        when(leave.getStatus()).thenReturn("APPROVED");

        when(applyLeaveRepository.findByUserId(1L))
                .thenReturn(List.of(leave));

        String response = leaveAIToolService.getMyLeaveHistoryText();

        assertTrue(response.contains("CL"));
    }

    @Test
    void testGetUpcomingPublicHolidaysText_Success() {

        var holiday = mock(com.cresensolutions.leaveservice.entity.PublicHoliday.class);

        when(holiday.getFestivalName()).thenReturn("Diwali");
        when(holiday.getHolidayDate()).thenReturn(LocalDate.now());

        when(publicHolidayRepository.findByHolidayDateBetween(any(), any()))
                .thenReturn(List.of(holiday));

        String response = leaveAIToolService.getUpcomingPublicHolidaysText();

        assertTrue(response.contains("Diwali"));
    }

    @Test
    void testBuildStructuredTablePayload_InvalidLeaveFormat() {

        String content = """
            Your approved leave requests:
            invalid data
            """;

        TablePayloadDTO dto =
                leaveAIToolService.buildStructuredTablePayload(content);

        assertNull(dto);
    }

    @Test
    void testBuildStructuredTablePayload_InvalidRoleFormat() {

        String content = """
            Users with role MANAGER:
            invalid
            """;

        TablePayloadDTO dto =
                leaveAIToolService.buildStructuredTablePayload(content);

        assertNull(dto);
    }

    @Test
    void testBuildStructuredTablePayload_InvalidSearchFormat() {

        String content = """
            Search results for "john":
            invalid
            """;

        TablePayloadDTO dto =
                leaveAIToolService.buildStructuredTablePayload(content);

        assertNull(dto);
    }

    @Test
    void testBuildStructuredTablePayload_InvalidSingleUser() {

        String content = """
            Name
            """;

        TablePayloadDTO dto =
                leaveAIToolService.buildStructuredTablePayload(content);

        assertNull(dto);
    }

    @Test
    void testSearchUsersByName_Success() {

        LeaveAIToolService.AdminTools tools =
                leaveAIToolService.new AdminTools();

        BasicUserInfoForAI user =
                new BasicUserInfoForAI(
                        1L,
                        "John",
                        "EMPLOYEE",
                        "john@gmail.com"
                );

        when(userClient.searchUsers("john"))
                .thenReturn(List.of(user));

        String response = tools.searchUsersByName("john");

        assertTrue(response.contains("john@gmail.com"));
    }

    @Test
    void testSearchUsersByName_Empty() {

        LeaveAIToolService.AdminTools tools =
                leaveAIToolService.new AdminTools();

        when(userClient.searchUsers("john"))
                .thenReturn(List.of());

        String response = tools.searchUsersByName("john");

        assertTrue(response.contains("No users found"));
    }

    @Test
    void testGetAvailableLeaveBalanceText_EmptyMap() throws Exception {

        EmployeeLeave employeeLeave = mock(EmployeeLeave.class);

        when(employeeLeave.getLeaves()).thenReturn("{}");

        when(employeeLeaveRepository.findByUserId(1L))
                .thenReturn(Optional.of(employeeLeave));

        when(objectMapper.readValue(anyString(),
                any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(Map.of());

        String response =
                leaveAIToolService.getAvailableLeaveBalanceText();

        assertTrue(response.contains("No leave balance"));
    }
}