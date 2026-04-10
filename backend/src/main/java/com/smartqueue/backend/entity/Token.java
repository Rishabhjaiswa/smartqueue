package com.smartqueue.backend.entity;

import com.smartqueue.backend.enums.ServiceType;
import com.smartqueue.backend.enums.TokenStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;
import com.smartqueue.backend.entity.Patient;
import com.smartqueue.backend.enums.VisitType;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type")
    private VisitType visitType;

    @Column(name = "severity_score")
    private Integer severityScore = 0;

    @Column(name = "dynamic_score")
    private Long dynamicScore;

    @Column(name = "actual_consultation_minutes")
    private Integer actualConsultationMinutes;

    @Column(name = "last_score_update")
    private LocalDateTime lastScoreUpdate;
}
