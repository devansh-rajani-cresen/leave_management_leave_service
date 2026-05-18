package com.cresensolutions.leaveservice.controller;

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
