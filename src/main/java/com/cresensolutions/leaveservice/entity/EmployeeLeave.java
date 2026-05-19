package com.cresensolutions.leaveservice.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "employee_leave", schema = "leaves")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeLeave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Foreign Key (user_profile.id)
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "email_id", unique = true)
    private String emailId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "leaves", columnDefinition = "jsonb")
    private String leaves;

    @Column(name = "gender")
    private String gender;
}
