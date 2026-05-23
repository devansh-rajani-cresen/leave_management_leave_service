package com.cresensolutions.leaveservice.controller;

import com.cresensolutions.leaveservice.dto.MyEmpLeaveHistory;
import com.cresensolutions.leaveservice.service.ManagerService;
import com.cresensolutions.leaveservice.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/manager")
@AllArgsConstructor
@Slf4j
public class ManagerController {

    private final ManagerService managerService;
    private final JwtUtil jwtUtil;

    // Returns all employee-leaves to particular Manager by Manager EmailID
    @GetMapping("/my-tasks")
    public List<Map<String, Object>> getManagerTasks(HttpServletRequest request) {

        // Extracting Manager Email from jwt token instead of passing in Query parameter
        String authHeader = request.getHeader("Authorization");
        String token = authHeader.substring(7);
        String managerEmail = jwtUtil.extractEmail(token);
        return managerService.getManagerTasks(managerEmail);
    }

    // Manager take action on employee-leave and Approve/Reject
    @PostMapping("/action")
    public void handleAction(@RequestBody Map<String, Object> request) {
        log.info("Manager took action to approve/reject leave request: {}", request);
        managerService.handleManagerAction(request);
    }

    // Returning individual Manager's employee leave history (by managerEmail) -> along with emp trail
    @GetMapping("/my-emp-leave-history")
    public List<MyEmpLeaveHistory> getMyEmployeeLeaveHistory(HttpServletRequest request){
        String authHeader = request.getHeader("Authorization");
        String token = authHeader.substring(7);
        String managerEmail = jwtUtil.extractEmail(token);
        log.info("Returning Employee's leave history for Manager: {}", managerEmail);
        return managerService.getEmployeeLeaveHistory(managerEmail);
    }

}
