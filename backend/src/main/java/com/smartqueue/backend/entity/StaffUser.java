package com.smartqueue.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "staff_users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "office_id", nullable = false)
    private Integer officeId;

    @Column(nullable = false)
    private String role;

    @Column(name = "doctor_id")
    private Long doctorId;
}
