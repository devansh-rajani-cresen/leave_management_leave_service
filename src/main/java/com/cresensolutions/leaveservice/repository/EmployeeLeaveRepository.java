package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.entity.EmployeeLeave;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeLeaveRepository extends JpaRepository<EmployeeLeave, Long> {
    Optional<EmployeeLeave> findByUserId(Long userId);
}
