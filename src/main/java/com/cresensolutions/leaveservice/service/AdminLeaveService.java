package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.CreateLeaveRequest;
import com.cresensolutions.leaveservice.dto.CreateLeaveResponse;
import java.util.List;

public interface AdminLeaveService {
    List<CreateLeaveResponse> getLeaves();
    void createLeave(CreateLeaveRequest createLeaveRequest);
    void updateLeave(CreateLeaveResponse updateLeaveRequest);
    void deleteLeave(int id);
}
