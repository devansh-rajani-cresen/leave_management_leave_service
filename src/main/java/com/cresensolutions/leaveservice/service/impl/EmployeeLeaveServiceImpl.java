package com.cresensolutions.leaveservice.service.impl;

import com.cresensolutions.leaveservice.dto.EmployeeLeaveRequest;
import com.cresensolutions.leaveservice.entity.EmployeeLeave;
import com.cresensolutions.leaveservice.entity.LeaveType;
import com.cresensolutions.leaveservice.exception.CustomException;
import com.cresensolutions.leaveservice.repository.EmployeeLeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveRepository;
import com.cresensolutions.leaveservice.service.EmployeeLeaveService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmployeeLeaveServiceImpl implements EmployeeLeaveService {

    private final EmployeeLeaveRepository employeeLeaveRepository;
    private final LeaveRepository leaveRepository;
    private final ObjectMapper objectMapper;

    // Initialize leave balance when employee registers
    @Override
    public void createEmployeeLeave(EmployeeLeaveRequest request) {

        EmployeeLeave employeeLeave = new EmployeeLeave();

        employeeLeave.setUserId(request.getUserId());
        employeeLeave.setFullName(request.getFullName());
        employeeLeave.setEmailId(request.getEmailId());
        employeeLeave.setGender(request.getGender());

        try {
            List<LeaveType> allLeaveTypes = leaveRepository.findAll();
            Map<String, Double> balanceMap = new HashMap<>();
            for (LeaveType leaveType : allLeaveTypes) {
                balanceMap.put(
                        leaveType.getUniqueLeaveName(),
                        (double) leaveType.getMaxDays()
                );
            }
            employeeLeave.setLeaves(objectMapper.writeValueAsString(balanceMap));
        } catch (Exception e) {
            log.error("Failed to initialize leave balance for userId={}",
                    request.getUserId(), e);
            employeeLeave.setLeaves("{}");
        }
        employeeLeaveRepository.save(employeeLeave);
    }

    // Return current leave balance
    @Override
    public Map<String, Double> getLeaveBalance(Long userId) {
        EmployeeLeave employeeLeave = employeeLeaveRepository.findByUserId(userId)
                .orElseThrow(() ->
                        new CustomException(
                                "Employee leave record not found for userId: " + userId,
                                400
                        )
                );
        try {
            String leavesJson = employeeLeave.getLeaves();
            if (leavesJson == null || leavesJson.isBlank()) {
                return Collections.emptyMap();
            }
            return objectMapper.readValue(
                    leavesJson,
                    new TypeReference<Map<String, Double>>() {}
            );
        } catch (Exception e) {
            log.error("Failed to parse leave balance for userId={}",
                    userId, e);
            return Collections.emptyMap();
        }
    }
}
