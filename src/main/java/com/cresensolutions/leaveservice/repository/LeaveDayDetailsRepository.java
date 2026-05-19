package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.entity.LeaveDayDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveDayDetailsRepository extends JpaRepository<LeaveDayDetails, Long> {
    List<LeaveDayDetails> findByLeaveId(Long leaveId);
}
