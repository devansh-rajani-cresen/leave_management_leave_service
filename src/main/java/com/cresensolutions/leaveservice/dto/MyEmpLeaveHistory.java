package com.cresensolutions.leaveservice.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class MyEmpLeaveHistory {
    private Long leaveId;
    private String employeeName;
    private String employeeEmail;
    private String managerEmail;
    private String leaveType;
    private LocalDate fromDate;
    private LocalDate toDate;
    private LocalDate createdAt;
    private String reason;
    private Object trail;
    private String status;
    private String approvedBy;
    private String rejectionReason;
}
