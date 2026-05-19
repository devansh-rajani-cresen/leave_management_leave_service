package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.EmployeeLeaveRequest;
import com.cresensolutions.leaveservice.entity.EmployeeLeave;
import com.cresensolutions.leaveservice.entity.LeaveType;
import com.cresensolutions.leaveservice.repository.EmployeeLeaveRepository;
import com.cresensolutions.leaveservice.repository.LeaveRepository;
import com.cresensolutions.leaveservice.service.impl.EmployeeLeaveServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeLeaveServiceImplTest {

    @Mock
    private EmployeeLeaveRepository employeeLeaveRepository;

    @Mock
    private LeaveRepository leaveRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EmployeeLeaveServiceImpl service;

    // --- CREATE EMPLOYEE LEAVE TESTS ---

    @Test
    void createEmployeeLeave_success() throws Exception {
        // Arrange
        EmployeeLeaveRequest request = new EmployeeLeaveRequest();
        request.setUserId(1L);
        request.setFullName("John Doe");
        request.setEmailId("john@example.com");
        request.setGender("Male");

        LeaveType lt1 = new LeaveType();
        lt1.setUniqueLeaveName("CL");
        lt1.setMaxDays(12);

        when(leaveRepository.findAll()).thenReturn(List.of(lt1));
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{\"CL\":12.0}");

        // Act
        service.createEmployeeLeave(request);

        // Assert
        ArgumentCaptor<EmployeeLeave> captor = ArgumentCaptor.forClass(EmployeeLeave.class);
        verify(employeeLeaveRepository).save(captor.capture());

        EmployeeLeave saved = captor.getValue();
        assertEquals(1L, saved.getUserId());
        assertEquals("{\"CL\":12.0}", saved.getLeaves());
        assertEquals("John Doe", saved.getFullName());
    }

    @Test
    void createEmployeeLeave_noLeaveTypesFound() throws Exception {
        // Arrange
        EmployeeLeaveRequest request = new EmployeeLeaveRequest();
        request.setUserId(2L);

        // Covering the case where the list is empty
        when(leaveRepository.findAll()).thenReturn(Collections.emptyList());
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

        // Act
        service.createEmployeeLeave(request);

        // Assert
        ArgumentCaptor<EmployeeLeave> captor = ArgumentCaptor.forClass(EmployeeLeave.class);
        verify(employeeLeaveRepository).save(captor.capture());
        assertEquals("{}", captor.getValue().getLeaves());
    }

    @Test
    void createEmployeeLeave_serializationError_fallsBackToEmptyJson() throws Exception {
        // Arrange
        EmployeeLeaveRequest request = new EmployeeLeaveRequest();
        request.setUserId(3L);

        when(leaveRepository.findAll()).thenReturn(List.of(new LeaveType()));
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("JSON Error"));

        // Act
        service.createEmployeeLeave(request);

        // Assert
        ArgumentCaptor<EmployeeLeave> captor = ArgumentCaptor.forClass(EmployeeLeave.class);
        verify(employeeLeaveRepository).save(captor.capture());
        assertEquals("{}", captor.getValue().getLeaves()); // Verify fallback
    }

    // --- GET LEAVE BALANCE TESTS ---

    @Test
    void getLeaveBalance_success() throws Exception {
        // Arrange
        Long userId = 101L;
        EmployeeLeave entity = new EmployeeLeave();
        entity.setLeaves("{\"SL\":10.0}");

        when(employeeLeaveRepository.findByUserId(userId)).thenReturn(Optional.of(entity));
        when(objectMapper.readValue(eq("{\"SL\":10.0}"), any(TypeReference.class)))
                .thenReturn(Map.of("SL", 10.0));

        // Act
        Map<String, Double> result = service.getLeaveBalance(userId);

        // Assert
        assertNotNull(result);
        assertEquals(10.0, result.get("SL"));
        verify(objectMapper, times(1)).readValue(anyString(), any(TypeReference.class));
    }

    @Test
    void getLeaveBalance_recordNotFound_throwsException() {
        // Arrange
        when(employeeLeaveRepository.findByUserId(anyLong())).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.getLeaveBalance(999L));
        assertTrue(ex.getMessage().contains("not found for userId: 999"));
    }

    @Test
    void getLeaveBalance_emptyOrNullJson_returnsEmptyMap() {
        // Arrange
        EmployeeLeave entityNull = new EmployeeLeave();
        entityNull.setLeaves(null);

        EmployeeLeave entityBlank = new EmployeeLeave();
        entityBlank.setLeaves("  ");

        when(employeeLeaveRepository.findByUserId(1L)).thenReturn(Optional.of(entityNull));
        when(employeeLeaveRepository.findByUserId(2L)).thenReturn(Optional.of(entityBlank));

        // Act & Assert
        assertTrue(service.getLeaveBalance(1L).isEmpty());
        assertTrue(service.getLeaveBalance(2L).isEmpty());

        // Ensure mapper was never called for empty strings
        verifyNoInteractions(objectMapper);
    }

    @Test
    void getLeaveBalance_parsingError_returnsEmptyMap() throws Exception {
        // Arrange
        EmployeeLeave entity = new EmployeeLeave();
        entity.setLeaves("{\"corrupt\": json}");

        when(employeeLeaveRepository.findByUserId(1L)).thenReturn(Optional.of(entity));
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenThrow(new RuntimeException("Jackson Error"));

        // Act
        Map<String, Double> result = service.getLeaveBalance(1L);

        // Assert
        assertTrue(result.isEmpty());
    }
}