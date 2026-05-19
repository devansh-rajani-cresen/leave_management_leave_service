// For operations related to Applying leave

package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.entity.Leave;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface ApplyLeaveRepository extends JpaRepository<Leave, Long> {
    List<Leave> findByUserId(Long userId);
    List<Leave> findByUserIdAndStatusOrderByFromDateDesc(Long userId, String status);

    // Manager
    List<Leave> findByManagerEmailOrderByCreatedAtDesc(String managerEmail);

    // HR (all data)
    List<Leave> findAllByOrderByCreatedAtDesc();

    // ----- AI Services -----

    // count applied leaves
    Long countByUserId(Long userId);

    // count leaves (common for PENDING, APPROVED, REJECTED)
    Long countByUserIdAndStatus(Long userId, String status);

    // count number of leaves of any status
    Long countLeaveByStatus(String status);

    // Employee on Leave Today
    long countByStatusAndFromDateLessThanEqualAndToDateGreaterThanEqual(
            String status,
            LocalDate fromDate,
            LocalDate toDate
    );
}
