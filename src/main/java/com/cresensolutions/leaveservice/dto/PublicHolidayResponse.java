package com.cresensolutions.leaveservice.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class PublicHolidayResponse {
    private Long id;
    private LocalDate holidayDate;
    private String festivalName;
}
