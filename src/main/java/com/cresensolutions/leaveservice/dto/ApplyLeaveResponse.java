package com.cresensolutions.leaveservice.dto;

import com.cresensolutions.leaveservice.entity.LeaveDayDetails;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class ApplyLeaveResponse {
    private Long id;
    private String leaveType;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String emailId;
    private String reason;
    private Object trail;  // added to send trail after login (reducing api call)
    private Boolean editable;
    private LocalDate createdAt;
    private String status;
    private String comments;
    private String approvedBy;
    private String rejectionReason;
    private LocalDate updatedAt;
    private List<LeaveDayDetails> dayDetails;
}
