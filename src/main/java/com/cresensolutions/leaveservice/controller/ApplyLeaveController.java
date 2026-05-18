package com.cresensolutions.leaveservice.controller;

import com.cresensolutions.leaveservice.dto.ApplyLeaveRequest;
import com.cresensolutions.leaveservice.dto.ApplyLeaveResponse;
import com.cresensolutions.leaveservice.dto.SuccessResponse;
import com.cresensolutions.leaveservice.service.ApplyLeaveService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/leaves")
@CrossOrigin("*")
public class ApplyLeaveController {
    private final ApplyLeaveService applyLeaveService;

    public ApplyLeaveController(ApplyLeaveService applyLeaveService) {
        this.applyLeaveService = applyLeaveService;
    }

    // Apply leave
    @PostMapping("/apply-leave")
    public ResponseEntity<SuccessResponse> applyLeaveReq(@RequestBody ApplyLeaveRequest applyLeaveRequest){
        log.info("Applying leave for employee for req: {}", applyLeaveRequest);
        applyLeaveService.applyLeave(applyLeaveRequest);
        return ResponseEntity.ok(
                new SuccessResponse("Leave applied successfully!")
        );
    }

    // Leave History
    @GetMapping("/my-leaves")
    public List<ApplyLeaveResponse> getAllMyLeavesReq(@RequestParam Long userId) {
        log.info("Returning all leaves from DB with user ID: {}", userId);
        return applyLeaveService.getAllMyLeaves(userId);
    }
}
