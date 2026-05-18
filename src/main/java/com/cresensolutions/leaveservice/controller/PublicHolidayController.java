package com.cresensolutions.leaveservice.controller;

import com.cresensolutions.leaveservice.dto.PublicHolidayRequest;
import com.cresensolutions.leaveservice.dto.PublicHolidayResponse;
import com.cresensolutions.leaveservice.dto.SuccessResponse;
import com.cresensolutions.leaveservice.service.PublicHolidayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@Slf4j
@RequestMapping("/leaves")
@CrossOrigin("*")
@RequiredArgsConstructor
public class PublicHolidayController {

    private final PublicHolidayService publicHolidayService;

    // Available for all employees
    @GetMapping("/public-holidays")
    public List<PublicHolidayResponse> getAllHolidays() {
        return publicHolidayService.getAllHolidays();
    }

    // Only ADMIN can create and delete holidays
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/create-public-holiday")
    public ResponseEntity<SuccessResponse> createHoliday(@RequestBody PublicHolidayRequest publicHolidayRequest) {
        publicHolidayService.createHoliday(publicHolidayRequest);
        return ResponseEntity.ok(
                new SuccessResponse("Public holiday created!")
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/update-public-holiday/{id}")
    public ResponseEntity<SuccessResponse> updateHoliday(@PathVariable Long id,
                                                         @RequestBody PublicHolidayRequest request) {
        publicHolidayService.updateHoliday(id, request);
        return ResponseEntity.ok(
                new SuccessResponse("Public holiday updated!")
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/delete-public-holiday/{id}")
    public ResponseEntity<SuccessResponse> deleteHoliday(@PathVariable Long id) {
        publicHolidayService.deleteHoliday(id);
        return ResponseEntity.ok(
                new SuccessResponse("Public holiday deleted!")
        );
    }
}
