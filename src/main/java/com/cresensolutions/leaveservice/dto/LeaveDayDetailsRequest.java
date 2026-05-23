package com.cresensolutions.leaveservice.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class LeaveDayDetailsRequest {
    private LocalDate leaveDate;
    private String dayType;        // FULL_DAY, HALF_DAY
    private String halfDaySession; // FIRST_HALF, SECOND_HALF, null
}
