// here I managed to Save user Leave Request to DB & to display previous leaves

package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.ApplyLeaveRequest;
import com.cresensolutions.leaveservice.dto.ApplyLeaveResponse;
import com.cresensolutions.leaveservice.entity.Leave;
import com.cresensolutions.leaveservice.entity.LeaveDayDetails;
import com.cresensolutions.leaveservice.repository.ApplyLeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveDayDetailsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.flowable.engine.RuntimeService;
import java.util.HashMap;
import java.util.Map;

import java.time.LocalDate;
import java.util.List;

@Service
@Slf4j
public class ApplyLeaveServiceImpl implements ApplyLeaveService {

    private final ApplyLeaveRepository applyLeaveRepository;
    private final LeaveDayDetailsRepository leaveDayDetailsRepository;
    private final RuntimeService runtimeService;

    public ApplyLeaveServiceImpl(ApplyLeaveRepository applyLeaveRepository,
                                 LeaveDayDetailsRepository leaveDayDetailsRepository,
                                 ObjectProvider<RuntimeService> runtimeServiceProvider) {
        this.applyLeaveRepository = applyLeaveRepository;
        this.leaveDayDetailsRepository = leaveDayDetailsRepository;
        this.runtimeService = runtimeServiceProvider.getIfAvailable();
    }

    // Returns all leaves for particular user id
    @Override
    public List<ApplyLeaveResponse> getAllMyLeaves(Long userId) {
        return applyLeaveRepository.findByUserId(userId)
                .stream()
                .map(leave -> {
                    ApplyLeaveResponse response = new ApplyLeaveResponse();
                    response.setId(leave.getId());
                    response.setLeaveType(leave.getLeaveType());
                    response.setFromDate(leave.getFromDate());
                    response.setToDate(leave.getToDate());
                    response.setEmailId(leave.getEmailId());
                    response.setReason(leave.getReason());
                    response.setComments(leave.getComments());
                    response.setStatus(leave.getStatus());
                    response.setEditable(leave.getEditable());
                    response.setCreatedAt(leave.getCreatedAt());
                    response.setApprovedBy(leave.getApprovedBy());
                    response.setRejectionReason(leave.getRejectionReason());

                    // Fetch day details for this leave
                    List<LeaveDayDetails> dayDetails = leaveDayDetailsRepository.findByLeaveId(leave.getId());
                    response.setDayDetails(dayDetails);

                    return response;
                }).toList();
    }

    // Apply Leave from user and save in "leave" table
    @Override
    public void applyLeave(ApplyLeaveRequest applyLeaveRequest) {

        // Save main leave record (saving in "leave" table)
        Leave leave = new Leave();
        leave.setUserId(applyLeaveRequest.getUserId());
        leave.setLeaveType(applyLeaveRequest.getLeaveType());
        leave.setFromDate(applyLeaveRequest.getFromDate());
        leave.setToDate(applyLeaveRequest.getToDate());
        leave.setEmailId(applyLeaveRequest.getEmailId());
        leave.setReason(applyLeaveRequest.getReason());
        leave.setComments(applyLeaveRequest.getComments());
        leave.setStatus("PENDING");
        leave.setEditable(true);
        leave.setCreatedAt(LocalDate.now());
        leave.setManagerEmail(applyLeaveRequest.getManagerEmail());
        leave.setManagerId(applyLeaveRequest.getManagerId());

        Leave savedLeave = applyLeaveRepository.save(leave);

        // Save per day details
        List<LeaveDayDetails> dayDetailsList = applyLeaveRequest.getDayDetails()
                .stream()
                .map(day -> {
                    LeaveDayDetails details = new LeaveDayDetails();
                    details.setLeaveId(savedLeave.getId());
                    details.setLeaveDate(day.getLeaveDate());
                    details.setDayType(day.getDayType());
                    details.setHalfDaySession(day.getHalfDaySession());
                    return details;
                }).toList();

        leaveDayDetailsRepository.saveAll(dayDetailsList);
        log.info("Leave applied successfully for userId: {}", applyLeaveRequest.getUserId());
        log.info("Leave Request : {}", applyLeaveRequest);

        // Flowable starting
        Map<String, Object> variables = new HashMap<>();
        variables.put("leaveId", savedLeave.getId());
        variables.put("managerEmail", savedLeave.getManagerEmail());
        variables.put("employeeEmail", savedLeave.getEmailId());
        variables.put("leaveType", savedLeave.getLeaveType());
        variables.put("hrEmail", "hr@company.com");

        if (runtimeService != null) {
            runtimeService.startProcessInstanceByKey("leaveProcess", variables);
            log.info("Flowable process started for leaveId: {}", savedLeave.getId());
        } else {
            log.warn("Flowable RuntimeService bean not available. Skipping workflow start for leaveId: {}", savedLeave.getId());
        }
    }

}
