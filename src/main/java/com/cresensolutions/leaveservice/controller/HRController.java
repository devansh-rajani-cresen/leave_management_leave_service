package com.cresensolutions.leaveservice.controller;

import com.cresensolutions.leaveservice.dto.HRTaskResponse;
import com.cresensolutions.leaveservice.dto.MyEmpLeaveHistory;
import com.cresensolutions.leaveservice.service.HRService;
import com.cresensolutions.leaveservice.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/hr")
@AllArgsConstructor
public class HRController {

    private final HRService hrService;
    private final JwtUtil jwtUtil;

    @GetMapping("/my-tasks")
    public List<HRTaskResponse> getHRTasks(HttpServletRequest request) {

        // Extracting hrEmail from JWT token, instead of passing hrEmail in Query Param
        String token = request.getHeader("Authorization").substring(7);
        String hrEmail = jwtUtil.extractEmail(token);

        log.info("Request came with Email of HR: {}", hrEmail);
        return hrService.getHRTasks(hrEmail);
    }

    @PostMapping("/action")
    public void handleHRAction(@RequestBody Map<String, Object> request,
                               HttpServletRequest httpRequest) {
        String authHeader = httpRequest.getHeader("Authorization");
        String token = authHeader.substring(7);

        String hrName = jwtUtil.extractfullName(token);
        request.put("hrName", hrName);

        hrService.handleHRAction(request);
    }

    // Returning all employees leave history along with trail (reducing API call)
    @GetMapping("/emp-leave-history")
    public List<MyEmpLeaveHistory> getAllLeaveHistory(){
        return hrService.getAllLeaveHistory();
    }
}
