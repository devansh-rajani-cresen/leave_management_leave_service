package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.ApplyLeaveRequest;
import com.cresensolutions.leaveservice.dto.ApplyLeaveResponse;
import java.util.List;

public interface ApplyLeaveService {
    List<ApplyLeaveResponse> getAllMyLeaves(Long userId);
    void applyLeave(ApplyLeaveRequest applyLeaveRequest);
}
