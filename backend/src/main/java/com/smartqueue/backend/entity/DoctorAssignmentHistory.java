package com.smartqueue.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Phase 3 — Continuity of Care.
 * Records every doctor assignment per patient visit, enabling:
 *  - "Last seen by" lookups
 *  - Specialist referral history
 *  - Preferred doctor auto-suggestion
 */
@Entity
@Table(name = "doctor_assignment_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorAssignmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "token_id")
    private Long tokenId;

    @Column(name = "assigned_at", nullable = false)
    @Builder.Default
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Column(name = "specialization", length = 64)
    private String specialization;

    @Column(name = "visit_type", length = 32)
    private String visitType;

    @Column(name = "chief_complaint", columnDefinition = "TEXT")
    private String chiefComplaint;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
