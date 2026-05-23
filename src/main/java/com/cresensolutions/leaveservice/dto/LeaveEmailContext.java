package com.cresensolutions.leaveservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveEmailContext {
    private Long leaveId;
    private String fullName;
    private Long userId;
    private String employeeEmail;
    private String managerEmail;
    private String hrEmail;
    private String leaveType;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String reason;
    private String rejectionReason;
}
