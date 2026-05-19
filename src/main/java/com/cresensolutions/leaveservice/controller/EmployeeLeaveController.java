package com.cresensolutions.leaveservice.controller;

import com.cresensolutions.leaveservice.dto.EmployeeLeaveRequest;
import com.cresensolutions.leaveservice.dto.SuccessResponse;
import com.cresensolutions.leaveservice.service.EmployeeLeaveService;
import com.cresensolutions.leaveservice.util.JwtRequestUtil;
import com.cresensolutions.leaveservice.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/leaves")
@CrossOrigin("*")
@RequiredArgsConstructor
public class EmployeeLeaveController {

    private final EmployeeLeaveService employeeLeaveService;
    private final JwtUtil jwtUtil;
    private final JwtRequestUtil jwtRequestUtil;

    @PostMapping(value = "/create-employee-leave", consumes = "application/json")
    public ResponseEntity<SuccessResponse> createEmployeeLeave(@RequestBody EmployeeLeaveRequest request) {
        employeeLeaveService.createEmployeeLeave(request);
        return ResponseEntity.ok(
                new SuccessResponse("Employee leave record created!")
        );
    }

    @GetMapping("/leave-balance")
    public Map<String, Double> getLeaveBalance(HttpServletRequest request) {
        String token = jwtRequestUtil.extractToken(request);
        Long userId = jwtUtil.extractUserId(token);
        return employeeLeaveService.getLeaveBalance(userId);
    }
}
