package com.cresensolutions.leaveservice.dto;

import lombok.Data;

@Data
public class EmployeeLeaveRequest {
    private Long userId;
    private String fullName;
    private String emailId;
    private String gender;
}
