package com.smartqueue.backend.repository;

import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.TokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {

    List<Token> findByOfficeIdAndStatusOrderByPriorityScoreAsc(
            Integer officeId, TokenStatus status
    );

    long countByOfficeIdAndStatus(Integer officeId, TokenStatus status);

    Optional<Token> findTopByOfficeIdAndStatusOrderByPriorityScoreAsc(
            Integer officeId, TokenStatus status
    );

    @Query(value = """
    SELECT t.consult_duration_mins
    FROM tokens t
    WHERE t.doctor_id = :doctorId
      AND t.status = 'COMPLETED'
      AND t.consult_duration_mins IS NOT NULL
    ORDER BY t.consultation_end DESC
    LIMIT :limit
    """, nativeQuery = true)
    List<Integer> findRecentConsultDurations(
            @Param("doctorId") Long doctorId,
            @Param("limit") int limit
    );
}


