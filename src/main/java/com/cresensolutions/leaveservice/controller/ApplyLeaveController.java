package com.cresensolutions.leaveservice.controller;

import com.cresensolutions.leaveservice.dto.ApplyLeaveRequest;
import com.cresensolutions.leaveservice.dto.ApplyLeaveResponse;
import com.cresensolutions.leaveservice.dto.SuccessResponse;
import com.cresensolutions.leaveservice.service.ApplyLeaveService;
import com.cresensolutions.leaveservice.util.JwtRequestUtil;
import com.cresensolutions.leaveservice.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/leaves")
@CrossOrigin("*")
public class ApplyLeaveController {

    private final ApplyLeaveService applyLeaveService;
    private final JwtUtil jwtUtil;
    private final JwtRequestUtil jwtRequestUtil;

    // Apply leave
    @PostMapping("/apply-leave")
    public ResponseEntity<SuccessResponse> applyLeaveReq(@Valid @RequestBody ApplyLeaveRequest applyLeaveRequest){
        applyLeaveService.applyLeave(applyLeaveRequest);
        return ResponseEntity.ok(
                new SuccessResponse("Leave applied!")
        );
    }

    // Leave History
    @GetMapping("/my-leaves")
    public List<ApplyLeaveResponse> getMyLeaves(HttpServletRequest request) {
        String token = jwtRequestUtil.extractToken(request);
        Long userId = jwtUtil.extractUserId(token);
        return applyLeaveService.getAllMyLeaves(userId);
    }

    // Delete leave
    @DeleteMapping("/my-leaves/{leaveId}")
    public ResponseEntity<SuccessResponse> deleteMyPendingLeave(@PathVariable Long leaveId, HttpServletRequest request) {
        String token = jwtRequestUtil.extractToken(request);
        Long userId = jwtUtil.extractUserId(token);
        applyLeaveService.deletePendingLeave(userId, leaveId);
        return ResponseEntity.ok(
                new SuccessResponse("Leave request deleted!")
        );
    }
}
