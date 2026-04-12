package com.smartqueue.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "doctors")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Doctor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String specialization;

    private String roomNumber;

    @Column(name = "is_available")
    private boolean available = true;

    @Column(name = "avg_consult_mins")
    private Integer avgConsultMins = 10;

    @Column(name = "max_queue_size")
    private Integer maxQueueSize = 25;
}