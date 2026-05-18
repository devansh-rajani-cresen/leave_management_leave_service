package com.cresensolutions.leaveservice.dto;

import lombok.Data;

@Data
public class CreateLeaveResponse {
    private int id;
    private String leaveName;
    private String uniqueLeaveName;
    private String description;
    private int maxDays;
}
