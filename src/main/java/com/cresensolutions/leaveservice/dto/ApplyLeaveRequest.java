package com.cresensolutions.leaveservice.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class ApplyLeaveRequest {
    private Long userId;
    private String fullName;
    private String leaveType;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String emailId;
    private String managerName;
    private Long managerId;
    private String managerEmail;
    private String reason;
    private String comments;
    private List<LeaveDayDetailsRequest> dayDetails;
}
