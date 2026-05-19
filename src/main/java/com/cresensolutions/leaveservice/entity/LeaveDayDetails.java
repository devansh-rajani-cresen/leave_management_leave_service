package com.cresensolutions.leaveservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "leave_day_details", schema = "leaves")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaveDayDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leave_id")
    private Long leaveId;

    @Column(name = "leave_date")
    private LocalDate leaveDate;

    @Column(name = "day_type")
    private String dayType;  // FULL_DAY, HALF_DAY

    @Column(name = "half_day_session")
    private String halfDaySession;  // FIRST_HALF, SECOND_HALF, null
}
