package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.HRTaskResponse;
import com.cresensolutions.leaveservice.dto.MyEmpLeaveHistory;
import java.util.List;
import java.util.Map;

public interface HRService {
    List<HRTaskResponse> getHRTasks(String hrEmail);
    void handleHRAction(Map<String, Object> request);
    List<MyEmpLeaveHistory> getAllLeaveHistory();
}
