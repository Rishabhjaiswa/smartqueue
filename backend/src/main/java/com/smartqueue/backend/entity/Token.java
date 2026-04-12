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

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false)
    private ServiceType serviceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenStatus status;

    @Column(name = "priority_score", nullable = false)
    private Long priorityScore;

    @Column(name = "office_id", nullable = false)
    private Integer officeId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "called_at")
    private LocalDateTime calledAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(name = "doctor_id")
    private Long doctorId;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type", nullable = false)
    private VisitType visitType = VisitType.WALK_IN;

    @Column(name = "chief_complaint")
    private String chiefComplaint;

    @Column(name = "severity_score")
    private Integer severityScore = 0;

    @Column(name = "dynamic_score")
    private Long dynamicScore;

    @Column(name = "last_score_update")
    private LocalDateTime lastScoreUpdate;

    @Column(name = "consultation_start")
    private LocalDateTime consultationStart;

    @Column(name = "consultation_end")
    private LocalDateTime consultationEnd;

    @Column(name = "consult_duration_mins")
    private Integer consultDurationMins;
}