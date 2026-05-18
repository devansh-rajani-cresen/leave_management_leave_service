package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.EmployeeLeaveRequest;
import com.cresensolutions.leaveservice.entity.EmployeeLeave;
import com.cresensolutions.leaveservice.repository.EmployeeLeaveRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmployeeLeaveServiceImpl implements EmployeeLeaveService {

    private final EmployeeLeaveRepository employeeLeaveRepository;

    public EmployeeLeaveServiceImpl(EmployeeLeaveRepository employeeLeaveRepository) {
        this.employeeLeaveRepository = employeeLeaveRepository;
    }

    @Override
    public void createEmployeeLeave(EmployeeLeaveRequest request) {
        EmployeeLeave employeeLeave = new EmployeeLeave();
        employeeLeave.setUserId(request.getUserId());
        employeeLeave.setFullName(request.getFullName());
        employeeLeave.setEmailId(request.getEmailId());
        employeeLeave.setGender(request.getGender());
        employeeLeaveRepository.save(employeeLeave);
        log.info("Employee leave record created for userId: {}", request.getUserId());
    }
}
