package com.cresensolutions.leaveservice.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class PublicHolidayRequest {
    private LocalDate holidayDate;
    private String festivalName;
}
