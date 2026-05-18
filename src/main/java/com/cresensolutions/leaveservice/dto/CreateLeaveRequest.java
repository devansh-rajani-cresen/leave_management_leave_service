// Frontend payload to create Leave

package com.cresensolutions.leaveservice.dto;
import lombok.Data;

@Data
public class CreateLeaveRequest {
    private String leaveName;
    private String uniqueLeaveName;
    private String description;
    private int maxDays;
}
