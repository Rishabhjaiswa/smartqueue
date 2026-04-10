package com.smartqueue.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;

@Entity
@Table(name = "doctors")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String specialization;

    private Boolean isAvailable;

    private Integer avgConsultationMinutes;

    private LocalTime sessionStartTime;

    private Integer delayMinutes;
}