package com.cresensolutions.leaveservice.service.ai.aiservice;

import com.cresensolutions.leaveservice.client.UserClient;
import com.cresensolutions.leaveservice.dto.aidto.BasicUserInfoForAI;
import com.cresensolutions.leaveservice.dto.aidto.TableColumnDTO;
import com.cresensolutions.leaveservice.dto.aidto.TablePayloadDTO;
import com.cresensolutions.leaveservice.entity.EmployeeLeave;
import com.cresensolutions.leaveservice.entity.LeaveType;
import com.cresensolutions.leaveservice.repository.ApplyLeaveRepository;
import com.cresensolutions.leaveservice.repository.EmployeeLeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveRepository;
import com.cresensolutions.leaveservice.repository.PublicHolidayRepository;
import com.cresensolutions.leaveservice.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.cresensolutions.leaveservice.common.LeaveConstants.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveAIToolService {

    private final ApplyLeaveRepository applyLeaveRepository;
    private final PublicHolidayRepository publicHolidayRepository;
    private final EmployeeLeaveRepository employeeLeaveRepository;
    private final ObjectMapper objectMapper;
    private final LeaveRepository leaveRepository;
    private final UserClient userClient;

    // TOOL RESOLVER
    public Object[] getToolsForRole(String role) {
        if (ROLE_ADMIN.equalsIgnoreCase(role)) {
            log.info("[TOOLS] ADMIN tool set loaded");
            return new Object[]{new EmployeeTools(), new AdminTools()};
        }
        log.info("[TOOLS] EMPLOYEE/MANAGER tool set loaded");
        return new Object[]{new EmployeeTools()};
    }

    // HELPERS
    private Long getCurrentUserId() {
        UserPrincipal user = (UserPrincipal) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
        return user.getUserId();
    }

    private void requireAdmin() {
        UserPrincipal user = (UserPrincipal) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
        if (!ROLE_ADMIN.equalsIgnoreCase(user.getRole())) {
            log.warn("[SECURITY] Unauthorized admin tool access by role: {}", user.getRole());
            throw new SecurityException("Access denied: ADMIN role required.");
        }
    }

    public String tryHandleDeterministicQuery(String message, String role) {
        if (message == null || message.isBlank()) {
            return null;
        }

        String normalized = message.toLowerCase(Locale.ROOT).trim();

        boolean asksForCount = containsAny(
                normalized,
                "how many",
                "count",
                "total",
                "number of"
        );

        boolean mentionsApproved = containsAny(
                normalized,
                "approved",
                "accepted"
        );

        boolean mentionsPending = containsAny(
                normalized,
                "pending",
                "remaining for approval"
        );

        boolean mentionsRejected = containsAny(
                normalized,
                "rejected",
                "denied"
        );

        boolean mentionsHistory = containsAny(
                normalized,
                "history",
                "recent leaves",
                "applied leaves",
                "leave history",
                "my leaves"
        );

        boolean mentionsBalance = containsAny(
                normalized,
                "leave balance",
                "remaining leaves",
                "leaves left"
        );

        boolean mentionsHoliday = containsAny(
                normalized,
                "holiday",
                "public holidays"
        );

        boolean mentionsPolicy = containsAny(
                normalized,
                "policy",
                "entitlement",
                "credits"
        );

        boolean mentionsTodayLeave = containsAny(
                normalized,
                "on leave today",
                "absent today",
                "employees on leave"
        );

        // APPROVED
        if (mentionsApproved) {
            return asksForCount
                    ? getApprovedLeaveCountText()
                    : getApprovedLeavesText();
        }

        // PENDING
        if (mentionsPending) {
            return asksForCount
                    ? getPendingLeaveCountText()
                    : getLeavesByStatusText(STATUS_PENDING, "PENDING");
        }

        // REJECTED
        if (mentionsRejected) {
            return asksForCount
                    ? getRejectedLeaveCountText()
                    : getLeavesByStatusText(STATUS_REJECTED, "REJECTED");
        }

        // LEAVE BALANCE
        if (mentionsBalance) {
            return getAvailableLeaveBalanceText();
        }

        // HOLIDAYS
        if (mentionsHoliday) {
            return getUpcomingPublicHolidaysText();
        }

        // POLICY / CREDITS
        if (mentionsPolicy) {
            return getYearlyLeaveCreditsText();
        }

        // HISTORY
        if (mentionsHistory) {
            return getMyLeaveHistoryText();
        }

        // MANAGER QUERY
        if (mentionsTodayLeave &&
                ROLE_MANAGER.equalsIgnoreCase(role)) {

            return getEmployeesOnLeaveTodayText();
        }
        return null;
    }

    public boolean isRawToolCallResponse(String response) {
        if (response == null) {
            return false;
        }
        String normalized = response.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("<|python_tag|>")
                || normalized.startsWith("{\"name\":")
                || (normalized.contains("\"parameters\"") && normalized.contains("\"name\""));
    }

    public TablePayloadDTO buildStructuredTablePayload(String content) {
        TablePayloadDTO tablePayload = parseLeaveRequestTable(content);
        if (tablePayload != null) {
            return tablePayload;
        }

        tablePayload = parseUsersByRoleTable(content);
        if (tablePayload != null) {
            return tablePayload;
        }

        tablePayload = parseUserSearchResultsTable(content);
        if (tablePayload != null) {
            return tablePayload;
        }

        return parseSingleUserDetailsTable(content);
    }

    public String getApprovedLeaveCountText() {
        Long userId = getCurrentUserId();
        log.info("[TOOL CALLED] getApprovedLeaveCount | userId: {}", userId);
        long count = applyLeaveRepository.countByUserIdAndStatus(userId, STATUS_APPROVED);
        if (count == 0) return "You have no any approved leave requests!";
        return "You have " + count + " approved leave request(s).";
    }

    public String getPendingLeaveCountText() {
        Long userId = getCurrentUserId();
        log.info("[TOOL CALLED] getPendingLeaveCount | userId: {}", userId);
        long count = applyLeaveRepository.countByUserIdAndStatus(userId, STATUS_PENDING);
        if (count == 0) return "You have no pending leave requests!";
        return "You have total " + count + " pending leave request(s).";
    }

    public String getRejectedLeaveCountText() {
        Long userId = getCurrentUserId();
        log.info("[TOOL CALLED] getRejectedLeaveCount | userId: {}", userId);
        long count = applyLeaveRepository.countByUserIdAndStatus(userId, STATUS_REJECTED);
        if (count == 0) return "You have no any rejected leave requests!";
        return "You have " + count + " rejected leave request(s), which are either rejected by Manager or HR.";
    }

    public String getMyLeaveHistoryText() {
        Long userId = getCurrentUserId();
        log.info("[TOOL CALLED] getMyLeaveHistory | userId: {}", userId);

        var leaves = applyLeaveRepository.findByUserId(userId);
        if (leaves == null || leaves.isEmpty()) {
            return "You have not applied for any leaves yet!";
        }

        StringBuilder sb = new StringBuilder("Your leave history:\n");
        for (var l : leaves) {
            sb.append("- ")
                    .append(l.getLeaveType())
                    .append(" | ")
                    .append(l.getFromDate())
                    .append(" to ")
                    .append(l.getToDate())
                    .append(" | Status: ")
                    .append(l.getStatus())
                    .append("\n");
        }
        return sb.toString().trim();
    }

    public String getApprovedLeavesText() {
        return getLeavesByStatusText(STATUS_APPROVED, "APPROVED");
    }

    public String getAvailableLeaveBalanceText() {
        Long userId = getCurrentUserId();
        log.info("[TOOL CALLED] getAvailableLeaveBalance | userId: {}", userId);

        Optional<EmployeeLeave> optional = employeeLeaveRepository.findByUserId(userId);
        if (optional.isEmpty() || optional.get().getLeaves() == null) {
            return "No leave balance records found for you.";
        }
        try {
            Map<String, Integer> balances = objectMapper.readValue(
                    optional.get().getLeaves(), new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });

            if (balances.isEmpty()) return "No leave balance records found for you.";

            StringBuilder sb = new StringBuilder("Your remaining leave balance:\n");
            balances.forEach((type, days) ->
                    sb.append("- ").append(type).append(": ").append(days).append(" day(s)\n"));
            return sb.toString().trim();

        } catch (Exception e) {
            log.error("[TOOL] Error parsing leave balance for userId: {}", userId, e);
            return "Unable to fetch leave balance at this time.";
        }
    }

    public String getUpcomingPublicHolidaysText() {
        log.info("[TOOL CALLED] getUpcomingPublicHolidays");
        LocalDate today = LocalDate.now();
        var holidays = publicHolidayRepository.findByHolidayDateBetween(today, today.plusMonths(1));
        if (holidays == null || holidays.isEmpty()) {
            return "There are no public holidays in the next month.";
        }
        StringBuilder sb = new StringBuilder("Upcoming public holidays (Next 1 month):\n");
        for (var h : holidays) {
            sb.append("- ").append(h.getFestivalName()).append(" on ").append(h.getHolidayDate()).append("\n");
        }
        return sb.toString().trim();
    }

    public String getYearlyLeaveCreditsText() {
        log.info("[TOOL CALLED] getYearlyLeaveCredits");
        List<LeaveType> leaveTypes = leaveRepository.findAll();
        if (leaveTypes.isEmpty()) return "No leave types are configured in the system.";

        StringBuilder sb = new StringBuilder("Yearly leave entitlements:\n");
        for (LeaveType lt : leaveTypes) {
            sb.append("- ").append(lt.getLeaveName()).append(": ").append(lt.getMaxDays()).append(" day(s)\n");
        }
        return sb.toString().trim();
    }

    public String getTotalEmployeesCountText() {
        requireAdmin();
        log.info("[TOOL CALLED] getTotalEmployeesCount");
        Long count = userClient.getEmployeeCount();
        if (count == null) return "Unable to fetch employee count at this time.";
        return "There are " + count + " employee(s) in the organization.";
    }

    public String getUsersByRoleText(String role) {
        requireAdmin();
        log.info("[TOOL CALLED] getUsersByRole | role: {}", role);
        List<BasicUserInfoForAI> users = userClient.getUserByRole(role);
        if (users == null || users.isEmpty()) return "No users found with role: " + role;

        StringBuilder sb = new StringBuilder("Users with role " + role + ":\n");
        for (BasicUserInfoForAI u : users) {
            sb.append("- ").append(u.getFullName())
                    .append(" (").append(u.getEmail()).append(")\n");
        }
        return sb.toString().trim();
    }

    public String getTotalPendingLeavesText() {
        requireAdmin();
        log.info("[TOOL CALLED] getTotalPendingLeaves");
        long count = applyLeaveRepository.countLeaveByStatus(STATUS_PENDING);
        if (count == 0) return "There are no pending leave requests across the organization.";
        return "There are total " + count + " leave request(s) which are pending for approval!";
    }

    public String getEmployeesOnLeaveTodayText() {
        log.info("[TOOL CALLED] getEmployeesOnLeaveToday");

        LocalDate today = LocalDate.now();

        long count = applyLeaveRepository
                .countByStatusAndFromDateLessThanEqualAndToDateGreaterThanEqual(
                        STATUS_APPROVED,
                        today,
                        today
                );

        if (count == 0) {
            return "No employees are on leave today.";
        }

        return count + " employee(s) are on leave today.";
    }

    public String getTotalLeaveCountText() {
        long userId = getCurrentUserId();
        log.info("[TOOL CALLED] getTotalLeaveCount | userId: {}", userId);
        long count = applyLeaveRepository.countByUserId(userId);
        if (count == 0) {
            return "You have not applied for any leaves yet.";
        }
        return "You have applied for a total of " + count + " leave request(s).";
    }

    public String getTotalApprovedLeavesText() {
        requireAdmin();
        log.info("[TOOL CALLED] getTotalApprovedLeaves");
        long count = applyLeaveRepository.countLeaveByStatus(STATUS_APPROVED);
        if (count == 0) return "There are no approved leave requests across the organization.";
        return "There are " + count + " approved leave request(s) across the organization.";
    }

    public String getTotalRejectedLeavesText() {
        requireAdmin();
        log.info("[TOOL CALLED] getTotalRejectedLeaves");
        long count = applyLeaveRepository.countLeaveByStatus(STATUS_REJECTED);
        if (count == 0) return "There are no rejected leave requests across the organization.";
        return "There are total " + count + " rejected leave request(s) across the organization.";
    }

    private String getLeavesByStatusText(String status, String label) {
        Long userId = getCurrentUserId();
        log.info("[TOOL CALLED] get{}Leaves | userId: {}", label.substring(0, 1).toUpperCase(Locale.ROOT) + label.substring(1), userId);

        var leaves = applyLeaveRepository.findByUserIdAndStatusOrderByFromDateDesc(userId, status);
        if (leaves == null || leaves.isEmpty()) {
            return "You have no " + label + " leave requests.";
        }

        StringBuilder sb = new StringBuilder("Your " + label + " leave requests:\n");
        for (var l : leaves) {
            sb.append("- ")
                    .append(l.getLeaveType())
                    .append(" | ")
                    .append(l.getFromDate())
                    .append(" to ")
                    .append(l.getToDate());
            if (l.getApprovedBy() != null && !l.getApprovedBy().isBlank()) {
                sb.append(" | Approved by: ").append(l.getApprovedBy());
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private TablePayloadDTO parseLeaveRequestTable(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        String trimmed = content.trim();
        String lowerTrimmed = trimmed.toLowerCase(Locale.ROOT);
        String status;
        if (lowerTrimmed.startsWith("your pending leave requests:")) {
            status = "pending";
        } else if (lowerTrimmed.startsWith("your approved leave requests:")) {
            status = "approved";
        } else if (lowerTrimmed.startsWith("your rejected leave requests:")) {
            status = "rejected";
        } else {
            return null;
        }

        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : trimmed.split("\\R")) {
            String normalizedLine = line.trim();
            if (!normalizedLine.startsWith("- ")) {
                continue;
            }

            String[] segments = normalizedLine.substring(2).split("\\|");
            if (segments.length < 2) {
                continue;
            }

            String leaveType = segments[0].trim();
            String[] dates = segments[1].trim().split("\\s+to\\s+");
            if (leaveType.isBlank() || dates.length < 2) {
                continue;
            }

            Map<String, String> row = new LinkedHashMap<>();
            row.put("leaveType", leaveType);
            row.put("fromDate", dates[0].trim());
            row.put("toDate", dates[1].trim());
            row.put("approvedBy", "-");

            if (segments.length > 2) {
                row.put("approvedBy", segments[2].replaceFirst("(?i)^Approved by:\\s*", "").trim());
            }

            rows.add(row);
        }

        if (rows.isEmpty()) {
            return null;
        }

        return new TablePayloadDTO(
                "Below are the details about your " + status + " leave requests.",
                "View Data",
                capitalize(status) + " Leave Requests",
                status + "-leave-requests.csv",
                List.of(
                        new TableColumnDTO("leaveType", "Leave Type"),
                        new TableColumnDTO("fromDate", "From Date"),
                        new TableColumnDTO("toDate", "To Date"),
                        new TableColumnDTO("approvedBy", "Approved By")
                ),
                rows
        );
    }

    private TablePayloadDTO parseUsersByRoleTable(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        String trimmed = content.trim();
        String prefix = "Users with role ";
        if (!trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return null;
        }

        int colonIndex = trimmed.indexOf(':');
        if (colonIndex < 0) {
            return null;
        }

        String roleName = trimmed.substring(prefix.length(), colonIndex).trim();
        if (roleName.isBlank()) {
            return null;
        }

        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : trimmed.split("\\R")) {
            String normalizedLine = line.trim();
            if (!normalizedLine.startsWith("- ")) {
                continue;
            }

            int openParen = normalizedLine.lastIndexOf('(');
            int closeParen = normalizedLine.lastIndexOf(')');
            if (openParen < 3 || closeParen <= openParen) {
                continue;
            }

            Map<String, String> row = new LinkedHashMap<>();
            row.put("fullName", normalizedLine.substring(2, openParen).trim());
            row.put("email", normalizedLine.substring(openParen + 1, closeParen).trim());
            rows.add(row);
        }

        if (rows.isEmpty()) {
            return null;
        }

        String roleLabel = capitalize(roleName.toLowerCase(Locale.ROOT));
        return new TablePayloadDTO(
                "Below is the " + roleLabel.toLowerCase(Locale.ROOT) + " list available for your request.",
                "View Data",
                roleLabel + " List",
                roleName.toLowerCase(Locale.ROOT) + "-list.csv",
                List.of(
                        new TableColumnDTO("fullName", "Full Name"),
                        new TableColumnDTO("email", "Email")
                ),
                rows
        );
    }

    private TablePayloadDTO parseUserSearchResultsTable(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        String trimmed = content.trim();
        String lowerTrimmed = trimmed.toLowerCase(Locale.ROOT);
        if (!lowerTrimmed.startsWith("search results for \"")) {
            return null;
        }

        int firstQuote = trimmed.indexOf('"');
        int secondQuote = trimmed.indexOf('"', firstQuote + 1);
        if (firstQuote < 0 || secondQuote <= firstQuote) {
            return null;
        }

        String searchTerm = trimmed.substring(firstQuote + 1, secondQuote).trim();
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : trimmed.split("\\R")) {
            String normalizedLine = line.trim();
            if (!normalizedLine.startsWith("- ")) {
                continue;
            }

            String[] segments = normalizedLine.substring(2).split("\\|");
            if (segments.length < 3) {
                continue;
            }

            Map<String, String> row = new LinkedHashMap<>();
            row.put("fullName", segments[0].trim());
            row.put("role", segments[1].replaceFirst("(?i)^Role:\\s*", "").trim());
            row.put("email", segments[2].replaceFirst("(?i)^Email:\\s*", "").trim());
            rows.add(row);
        }

        if (rows.isEmpty()) {
            return null;
        }

        return new TablePayloadDTO(
                "Below are the employee details matching \"" + searchTerm + "\".",
                "View Data",
                "Search Results: " + searchTerm,
                "employee-search-" + slugify(searchTerm) + ".csv",
                List.of(
                        new TableColumnDTO("fullName", "Full Name"),
                        new TableColumnDTO("role", "Role"),
                        new TableColumnDTO("email", "Email")
                ),
                rows
        );
    }

    private TablePayloadDTO parseSingleUserDetailsTable(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        List<String> lines = new ArrayList<>();
        for (String line : content.trim().split("\\R")) {
            String normalizedLine = line.trim();
            if (!normalizedLine.isBlank()) {
                lines.add(normalizedLine);
            }
        }

        if (lines.size() < 2) {
            return null;
        }

        List<TableColumnDTO> columns = new ArrayList<>();
        Map<String, String> row = new LinkedHashMap<>();
        for (String line : lines) {
            int separatorIndex = line.indexOf(':');
            if (separatorIndex <= 0) {
                continue;
            }

            String label = line.substring(0, separatorIndex).trim();
            String value = line.substring(separatorIndex + 1).trim();
            if (label.isBlank() || value.isBlank()) {
                continue;
            }

            String key = toCamelKey(label);
            columns.add(new TableColumnDTO(key, label));
            row.put(key, value);
        }

        if (columns.size() < 2) {
            return null;
        }

        String tableTitle = lines.get(0).replaceFirst("[:\\-]\\s*$", "").trim();
        return new TablePayloadDTO(
                "Below are the details available for this employee.",
                "View details",
                tableTitle.isBlank() ? "Employee Details" : tableTitle,
                "employee-details-" + slugify(tableTitle.isBlank() ? "record" : tableTitle) + ".csv",
                columns,
                List.of(row)
        );
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private String slugify(String value) {
        if (value == null || value.isBlank()) {
            return "data";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private String toCamelKey(String label) {
        String[] parts = label.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]+", " ")
                .trim()
                .split("\\s+");
        if (parts.length == 0) {
            return "value";
        }

        StringBuilder key = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            key.append(parts[i], 0, 1).append(parts[i].substring(1));
        }
        return key.toString();
    }

    // HELPER for checking keyword
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    // EMPLOYEE & MANAGER TOOLS
    public class EmployeeTools {

        @Tool(
                name = "getTotalLeaveCount",
                description = "Use this when the user asks how many total leaves they have applied, their total leave count, or how many times they took leave."
        )
        public String getTotalLeaveCount() {
            return getTotalLeaveCountText();
        }

        @Tool(
                name = "getMyLeaveHistory",
                description = "Use this when the user asks about their leave history, recent leaves, past leaves, or what leaves they have applied."
        )
        public String getMyLeaveHistory() {
            return getMyLeaveHistoryText();
        }

        @Tool(
                name = "getPendingLeaveCount",
                description = "Use this when the user asks how many of their leaves are pending, awaiting approval, or not yet decided."
        )
        public String getPendingLeaveCount() {
            return getPendingLeaveCountText();
        }

        @Tool(
                name = "getApprovedLeaveCount",
                description = "Use this when the user asks how many of their leaves have been approved or accepted."
        )
        public String getApprovedLeaveCount() {
            return getApprovedLeaveCountText();
        }

        @Tool(
                name = "getRejectedLeaveCount",
                description = "Use this when the user asks how many of their leaves have been rejected or denied."
        )
        public String getRejectedLeaveCount() {
            return getRejectedLeaveCountText();
        }

        @Tool(
                name = "getApprovedLeaves",
                description = "Use this when the user asks to show, list, or give their approved leaves instead of only a count."
        )
        public String getApprovedLeaves() {
            return getApprovedLeavesText();
        }

        @Tool(
                name = "getUpcomingPublicHolidays",
                description = "Use this when the user asks about upcoming holidays, public holidays, or days off in the next month."
        )
        public String getUpcomingPublicHolidays() {
            return getUpcomingPublicHolidaysText();
        }

        @Tool(
                name = "getAvailableLeaveBalance",
                description = "Use this when the user asks how many leaves they have left, their remaining leave balance, or how many more days they can take off."
        )
        public String getAvailableLeaveBalance() {
            return getAvailableLeaveBalanceText();
        }

        @Tool(
                name = "getYearlyLeaveCredits",
                description = "Use this when the user asks about total yearly leave entitlements, how many leaves each type allows per year, or the company leave policy."
        )
        public String getYearlyLeaveCredits() {
            return getYearlyLeaveCreditsText();
        }

        @Tool(
                name = "getEmployeesOnLeaveToday",
                description = "Use this when Manager asks how many employees are on leave today, absent today, or currently on leave."
        )
        public String getEmployeesOnLeaveToday() {
            return getEmployeesOnLeaveTodayText();
        }
    }

    // ADMIN TOOLS
    public class AdminTools {

        @Tool(
                name = "getTotalEmployeesCount",
                description = "Use this when Admin asks how many total employees are in the company or organization."
        )
        public String getTotalEmployeesCount() {
            return getTotalEmployeesCountText();
        }

        @Tool(
                name = "getUsersByRole",
                description = "Use this when Admin asks for a list of employees or managers, or wants to see users by a specific role like MANAGER or EMPLOYEE."
        )
        public String getUsersByRole(String role) {
            return getUsersByRoleText(role);
        }

        @Tool(
                name = "searchUsersByName",
                description = "Use this when Admin wants to search for a specific employee or person by their name."
        )
        public String searchUsersByName(String name) {
            requireAdmin();
            log.info("[TOOL CALLED] searchUsersByName | name: {}", name);
            List<BasicUserInfoForAI> users = userClient.searchUsers(name);
            if (users == null || users.isEmpty()) return "No users found matching: " + name;

            StringBuilder sb = new StringBuilder("Search results for \"" + name + "\":\n");
            for (BasicUserInfoForAI u : users) {
                sb.append("- ").append(u.getFullName())
                        .append(" | Role: ").append(u.getRole())
                        .append(" | Email: ").append(u.getEmail()).append("\n");
            }
            return sb.toString().trim();
        }

        @Tool(
                name = "getTotalPendingLeaves",
                description = "Use this when Admin asks how many leave requests are currently pending across all employees."
        )
        public String getTotalPendingLeaves() {
            return getTotalPendingLeavesText();
        }

        @Tool(
                name = "getTotalApprovedLeaves",
                description = "Use this when Admin asks how many leave requests have been approved across all employees."
        )
        public String getTotalApprovedLeaves() {
            return getTotalApprovedLeavesText();
        }

        @Tool(
                name = "getTotalRejectedLeaves",
                description = "Use this when Admin asks how many leave requests have been rejected across all employees."
        )
        public String getTotalRejectedLeaves() {
            return getTotalRejectedLeavesText();
        }
    }
}
