package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LeaveRepository extends JpaRepository<LeaveType, Integer> {
    Optional<LeaveType> findByUniqueLeaveName(String uniqueLeaveName);
}
