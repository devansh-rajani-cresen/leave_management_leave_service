package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.PublicHolidayRequest;
import com.cresensolutions.leaveservice.dto.PublicHolidayResponse;
import com.cresensolutions.leaveservice.entity.PublicHoliday;
import com.cresensolutions.leaveservice.exception.CustomException;
import com.cresensolutions.leaveservice.repository.PublicHolidayRepository;
import com.cresensolutions.leaveservice.service.impl.PublicHolidayServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicHolidayServiceImplTest {

    @Mock
    private PublicHolidayRepository repository;

    @InjectMocks
    private PublicHolidayServiceImpl service;

    @Test
    void createHoliday_success() {
        PublicHolidayRequest req = new PublicHolidayRequest();
        req.setHolidayDate(LocalDate.now());
        req.setFestivalName("Diwali");

        service.createHoliday(req);

        verify(repository).save(any());
    }

    @Test
    void getAllHolidays_success() {
        PublicHoliday h = new PublicHoliday();
        h.setId(1L);
        h.setHolidayDate(LocalDate.now());
        h.setFestivalName("Holi");

        when(repository.findAll()).thenReturn(List.of(h));

        List<PublicHolidayResponse> res = service.getAllHolidays();

        assertEquals(1, res.size());
        assertEquals("Holi", res.get(0).getFestivalName());
    }

    @Test
    void getAllHolidays_empty() {
        when(repository.findAll()).thenReturn(List.of());

        List<PublicHolidayResponse> res = service.getAllHolidays();

        assertTrue(res.isEmpty());
    }

    @Test
    void updateHoliday_success() {
        PublicHolidayRequest req = new PublicHolidayRequest();
        req.setHolidayDate(LocalDate.now());
        req.setFestivalName("Updated");

        PublicHoliday holiday = new PublicHoliday();
        holiday.setId(1L);

        when(repository.findById(1L)).thenReturn(Optional.of(holiday));

        service.updateHoliday(1L, req);

        verify(repository).save(holiday);
        assertEquals("Updated", holiday.getFestivalName());
    }

    @Test
    void updateHoliday_notFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> service.updateHoliday(1L, new PublicHolidayRequest()));
    }

    @Test
    void deleteHoliday_success() {
        when(repository.existsById(1L)).thenReturn(true);

        service.deleteHoliday(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void deleteHoliday_notFound() {
        when(repository.existsById(1L)).thenReturn(false);

        CustomException ex = assertThrows(CustomException.class,
                () -> service.deleteHoliday(1L));

        assertEquals("Holiday not found with id: 1", ex.getMessage());
    }
}