package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.PublicHolidayRequest;
import com.cresensolutions.leaveservice.dto.PublicHolidayResponse;
import java.util.List;

public interface PublicHolidayService {
    void createHoliday(PublicHolidayRequest publicHolidayRequest);
    List<PublicHolidayResponse> getAllHolidays();
    void updateHoliday(Long id, PublicHolidayRequest request);
    void deleteHoliday(Long id);
}
