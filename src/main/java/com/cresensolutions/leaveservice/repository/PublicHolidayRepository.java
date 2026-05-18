package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.entity.PublicHoliday;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {
    List<PublicHoliday> findByHolidayDateBetween(LocalDate startDate, LocalDate endDate);
}