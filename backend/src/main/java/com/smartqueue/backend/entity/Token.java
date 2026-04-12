package com.smartqueue.backend.entity;

import com.smartqueue.backend.enums.ServiceType;
import com.smartqueue.backend.enums.TokenStatus;
import com.smartqueue.backend.enums.VisitType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Token {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_number", nullable = false)
    private String tokenNumber;

    @Column(name = "patient_name")
    private String patientName;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false)
    private ServiceType serviceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenStatus status;

    @Column(name = "priority_score", nullable = false)
    private Long priorityScore;

    @Column(name = "dynamic_score")
    private Double dynamicScore;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type")
    private VisitType visitType;

    @Column(name = "severity_score")
    private Integer severityScore = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "called_at")
    private LocalDateTime calledAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "actual_consultation_minutes")
    private Integer actualConsultationMinutes;
}