// when new employee is created, then we are initializing leaves for the year (from leave_type ADMIN created leaves)

package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.EmployeeLeaveRequest;
import java.util.Map;

public interface EmployeeLeaveService {
    void createEmployeeLeave(EmployeeLeaveRequest request);
    Map<String, Double> getLeaveBalance(Long userId);
}
