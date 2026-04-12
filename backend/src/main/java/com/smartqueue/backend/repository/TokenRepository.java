package com.smartqueue.backend.repository;

import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.TokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {

    long countByDoctorIdAndStatus(Long doctorId, TokenStatus status);

    Optional<Token> findTopByDoctorIdAndStatusOrderByPriorityScoreAsc(
            Long doctorId,
            TokenStatus status
    );
}