package com.smartqueue.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "patients")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Patient {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String phone;

    @Column(nullable = false)
    private Integer age;

    private String gender;
    private String bloodGroup;
    private String abhaId;

    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}