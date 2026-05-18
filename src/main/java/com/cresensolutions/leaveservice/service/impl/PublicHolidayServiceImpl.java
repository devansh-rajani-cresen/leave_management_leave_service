package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.PublicHolidayRequest;
import com.cresensolutions.leaveservice.dto.PublicHolidayResponse;
import com.cresensolutions.leaveservice.entity.PublicHoliday;
import com.cresensolutions.leaveservice.repository.PublicHolidayRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@Slf4j
public class PublicHolidayServiceImpl implements PublicHolidayService {

    private final PublicHolidayRepository publicHolidayRepository;

    public PublicHolidayServiceImpl(PublicHolidayRepository publicHolidayRepository) {
        this.publicHolidayRepository = publicHolidayRepository;
    }

    // ADMIN will create Public Holiday
    @Override
    public void createHoliday(PublicHolidayRequest request) {
        PublicHoliday holiday = new PublicHoliday();
        holiday.setHolidayDate(request.getHolidayDate());
        holiday.setFestivalName(request.getFestivalName());
        holiday.setCreatedAt(OffsetDateTime.now());
        publicHolidayRepository.save(holiday);
    }

    // Anyone can get public holidays
    @Override
    public List<PublicHolidayResponse> getAllHolidays() {
        return publicHolidayRepository.findAll()
                .stream()
                .map(h -> {
                    PublicHolidayResponse response = new PublicHolidayResponse();
                    response.setId(h.getId());
                    response.setHolidayDate(h.getHolidayDate());
                    response.setFestivalName(h.getFestivalName());
                    return response;
                }).toList();
    }

    // Update Public Leave by ADMIN
    @Override
    public void updateHoliday(Long id, PublicHolidayRequest request) {
        PublicHoliday existing = publicHolidayRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Holiday not found with id: " + id));
        existing.setHolidayDate(request.getHolidayDate());
        existing.setFestivalName(request.getFestivalName());
        existing.setUpdatedAt(OffsetDateTime.now());
        publicHolidayRepository.save(existing);
        log.info("Holiday updated successfully for id: {}", id);
    }

    // ADMIN can delete public holiday with id
    @Override
    public void deleteHoliday(Long id) {
        log.info("Deleting public holiday with id: {}", id);
        if (!publicHolidayRepository.existsById(id)) {
            throw new RuntimeException("Holiday not found with id: " + id);
        }
        publicHolidayRepository.deleteById(id);
    }

    // Sends public holidays to frontend to display
//    @Override
//    public List<LocalDate> getHolidayDatesBetween(LocalDate startDate, LocalDate endDate) {
//        return publicHolidayRepository.findByHolidayDateBetween(startDate, endDate)
//                .stream()
//                .map(PublicHoliday::getHolidayDate)
//                .toList();
//    }

}