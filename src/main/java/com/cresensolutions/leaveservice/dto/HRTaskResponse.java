package com.cresensolutions.leaveservice.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class HRTaskResponse {

    private String taskId;
    private Long leaveId;
    private String employeeName;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String leaveType;
    private String reason;
    private List<Map<String, Object>> dayDetails;
    private List<Map<String, Object>> trail;
}
