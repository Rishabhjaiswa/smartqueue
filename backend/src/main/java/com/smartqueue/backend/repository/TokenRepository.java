package com.smartqueue.backend.repository;

import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.TokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
