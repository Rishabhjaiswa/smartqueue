package com.smartqueue.backend.repository;

import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.TokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {

    // 🔹 Count waiting tokens
    long countByDoctorIdAndStatus(Long doctorId, TokenStatus status);

    // 🔹 Get next token (priority-based)
    Optional<Token> findTopByDoctorIdAndStatusOrderByPriorityScoreAsc(
            Long doctorId,
            TokenStatus status
    );

    // 🔥 NEW — REQUIRED for Phase 4

    List<Token> findByDoctorIdAndStatusOrderByDynamicScoreAsc(
            Long doctorId,
            TokenStatus status
    );

    Token findFirstByDoctorIdAndStatus(
            Long doctorId,
            TokenStatus status
    );
    List<Token> findByDoctorId(Long doctorId);
}