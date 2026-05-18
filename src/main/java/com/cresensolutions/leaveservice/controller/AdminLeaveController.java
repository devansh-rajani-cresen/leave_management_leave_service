// In this, all leaves are ADMIN created like CL/SL/ML etc. (For displaying available types of leaves)

package com.cresensolutions.leaveservice.controller;

import com.cresensolutions.leaveservice.dto.CreateLeaveRequest;
import com.cresensolutions.leaveservice.dto.CreateLeaveResponse;
import com.cresensolutions.leaveservice.dto.SuccessResponse;
import com.cresensolutions.leaveservice.service.AdminLeaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/leaves")
@CrossOrigin("*")
@RequiredArgsConstructor
public class AdminLeaveController {

    private final AdminLeaveService adminLeaveService;

    @GetMapping("/get-leaves")
    public List<CreateLeaveResponse> getAllLeavesReq() {
        return adminLeaveService.getLeaves();
    }

    // Only ADMIN can perform below actions for Leaves

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/create-leave")
    public ResponseEntity<SuccessResponse> createLeaveReq(@RequestBody CreateLeaveRequest createLeaveRequest) {
        adminLeaveService.createLeave(createLeaveRequest);
        return ResponseEntity.ok(
                new SuccessResponse("Leave created!")
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/update-leave")
    public ResponseEntity<SuccessResponse> updateLeaveReq(@RequestBody CreateLeaveResponse updateLeaveRequest) {
        adminLeaveService.updateLeave(updateLeaveRequest);
        return ResponseEntity.ok(
                new SuccessResponse("Leave updated!")
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/delete-leave/{id}")
    public ResponseEntity<SuccessResponse> deleteLeaveReq(@PathVariable int id) {
        adminLeaveService.deleteLeave(id);
        return ResponseEntity.ok(
                new SuccessResponse("Leave deleted!")
        );
    }

}
