package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.MyEmpLeaveHistory;
import java.util.List;
import java.util.Map;

public interface ManagerService {
    List<Map<String, Object>> getManagerTasks(String managerEmail);
    void handleManagerAction(Map<String, Object> request);
    List<MyEmpLeaveHistory> getEmployeeLeaveHistory(String managerEmail);
}
