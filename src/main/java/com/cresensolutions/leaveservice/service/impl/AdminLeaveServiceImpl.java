package com.cresensolutions.leaveservice.service.impl;

import com.cresensolutions.leaveservice.dto.CreateLeaveRequest;
import com.cresensolutions.leaveservice.dto.CreateLeaveResponse;
import com.cresensolutions.leaveservice.entity.LeaveType;
import com.cresensolutions.leaveservice.exception.CustomException;
import com.cresensolutions.leaveservice.repository.LeaveRepository;
import com.cresensolutions.leaveservice.service.AdminLeaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminLeaveServiceImpl implements AdminLeaveService {

    private final LeaveRepository leaveRepository;

    // GET - Return all leaves from DB
    @Override
    public List<CreateLeaveResponse> getLeaves() {
        List<LeaveType> leaves = leaveRepository.findAll();
        return leaves.stream()
                .map(leave -> {
                    CreateLeaveResponse response = new CreateLeaveResponse();
                    response.setId(leave.getId());
                    response.setLeaveName(leave.getLeaveName());
                    response.setUniqueLeaveName(leave.getUniqueLeaveName());
                    response.setDescription(leave.getDescription());
                    response.setMaxDays(leave.getMaxDays());
                    return response;
                })
                .toList();
    }

    // POST - Create Leave Type in DB
    @Override
    public void createLeave(CreateLeaveRequest createLeaveRequest) {
        String leaveName = createLeaveRequest.getLeaveName();
        String uniqueLeaveName = createLeaveRequest.getUniqueLeaveName();
        String description = createLeaveRequest.getDescription();
        int maxDays = createLeaveRequest.getMaxDays();

        // Check if leave already exists (with Unique leaveName)
        Optional<LeaveType> dbLeave = leaveRepository.findByUniqueLeaveName(uniqueLeaveName);

        if (dbLeave.isPresent()) {
            throw new CustomException("Leave already exists!", 409);
        }

        LeaveType leave = new LeaveType();
        leave.setLeaveName(leaveName);
        leave.setUniqueLeaveName(uniqueLeaveName);
        leave.setDescription(description);
        leave.setMaxDays(maxDays);
        leave.setCreatedAt(OffsetDateTime.now());
        leaveRepository.save(leave);
    }

    // PUT - Update Leave Type
    @Override
    public void updateLeave(CreateLeaveResponse updateLeaveRequest) {
        LeaveType existingLeave = leaveRepository.findById(updateLeaveRequest.getId())
                .orElseThrow(() -> new RuntimeException("Leave not found with id: " + updateLeaveRequest.getId()));

        // Update leave with new payload
        existingLeave.setLeaveName(updateLeaveRequest.getLeaveName());
        existingLeave.setUniqueLeaveName(updateLeaveRequest.getUniqueLeaveName());
        existingLeave.setDescription(updateLeaveRequest.getDescription());
        existingLeave.setMaxDays(updateLeaveRequest.getMaxDays());
        existingLeave.setUpdatedAt(OffsetDateTime.now());
        leaveRepository.save(existingLeave);
    }

    // DELETE - Delete Leave from DB
    @Override
    public void deleteLeave(int id) {
        if (!leaveRepository.existsById(id)) {
            throw new CustomException("Leave not found with the id!", 404);
        }
        leaveRepository.deleteById(id);
    }
}
