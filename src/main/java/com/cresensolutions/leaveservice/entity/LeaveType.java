package com.cresensolutions.leaveservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity
@Table(name = "leave_types", schema = "leaves")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaveType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "leave_name", nullable = false)
    private String leaveName;

    @Column(name = "leave_unique_name", unique = true, nullable = false)
    private String uniqueLeaveName;

    @Column(name = "description")
    private String description;

    @Column(name = "max_days", nullable = false)
    private int maxDays;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
