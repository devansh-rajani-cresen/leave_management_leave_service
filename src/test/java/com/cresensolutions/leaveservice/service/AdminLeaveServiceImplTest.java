package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.dto.CreateLeaveRequest;
import com.cresensolutions.leaveservice.dto.CreateLeaveResponse;
import com.cresensolutions.leaveservice.entity.LeaveType;
import com.cresensolutions.leaveservice.exception.CustomException;
import com.cresensolutions.leaveservice.repository.LeaveRepository;
import com.cresensolutions.leaveservice.service.impl.AdminLeaveServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminLeaveServiceImplTest {

    @Mock
    private LeaveRepository leaveRepository;

    @InjectMocks
    private AdminLeaveServiceImpl service;

    //  GET LEAVES 

    @Test
    void getLeaves_success() {
        LeaveType leave = new LeaveType();
        leave.setId(1);
        leave.setLeaveName("Sick");
        leave.setUniqueLeaveName("SICK");
        leave.setDescription("desc");
        leave.setMaxDays(10);

        when(leaveRepository.findAll()).thenReturn(List.of(leave));

        List<CreateLeaveResponse> result = service.getLeaves();

        assertEquals(1, result.size());
        assertEquals("Sick", result.get(0).getLeaveName());
    }

    @Test
    void getLeaves_empty() {
        when(leaveRepository.findAll()).thenReturn(List.of());

        List<CreateLeaveResponse> result = service.getLeaves();

        assertTrue(result.isEmpty());
    }

    //  CREATE LEAVE 

    @Test
    void createLeave_success() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setLeaveName("Sick");
        req.setUniqueLeaveName("SICK");
        req.setDescription("desc");
        req.setMaxDays(10);

        when(leaveRepository.findByUniqueLeaveName("SICK"))
                .thenReturn(Optional.empty());

        service.createLeave(req);

        verify(leaveRepository).save(any());
    }

    @Test
    void createLeave_alreadyExists() {
        CreateLeaveRequest req = new CreateLeaveRequest();
        req.setUniqueLeaveName("SICK");

        when(leaveRepository.findByUniqueLeaveName("SICK"))
                .thenReturn(Optional.of(new LeaveType()));

        CustomException ex = assertThrows(CustomException.class,
                () -> service.createLeave(req));

        assertEquals("Leave already exists!", ex.getMessage());
    }

    //  UPDATE LEAVE 

    @Test
    void updateLeave_success() {
        CreateLeaveResponse req = new CreateLeaveResponse();
        req.setId(1);
        req.setLeaveName("Updated");
        req.setUniqueLeaveName("UPDATED");
        req.setDescription("desc");
        req.setMaxDays(5);

        LeaveType leave = new LeaveType();
        leave.setId(1);

        when(leaveRepository.findById(1)).thenReturn(Optional.of(leave));

        service.updateLeave(req);

        verify(leaveRepository).save(leave);
        assertEquals("Updated", leave.getLeaveName());
    }

    @Test
    void updateLeave_notFound() {
        CreateLeaveResponse req = new CreateLeaveResponse();
        req.setId(1);

        when(leaveRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> service.updateLeave(req));
    }

    //  DELETE LEAVE 

    @Test
    void deleteLeave_success() {
        when(leaveRepository.existsById(1)).thenReturn(true);

        service.deleteLeave(1);

        verify(leaveRepository).deleteById(1);
    }

    @Test
    void deleteLeave_notFound() {
        when(leaveRepository.existsById(1)).thenReturn(false);

        CustomException ex = assertThrows(CustomException.class,
                () -> service.deleteLeave(1));

        assertEquals("Leave not found with the id!", ex.getMessage());
    }
}