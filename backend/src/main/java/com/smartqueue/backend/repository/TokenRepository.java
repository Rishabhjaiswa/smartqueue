package com.smartqueue.backend.repository;

import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.TokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    List<Token> findByStatusAndCalledAtBefore(
            TokenStatus status,
            LocalDateTime time
    );

    List<Token> findByDoctorIdAndStatusOrderByPriorityScoreAsc(Long doctorId, TokenStatus status);

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

    Optional<Token> findTopByDoctorIdAndStatusOrderByCalledAtDesc(
            Long doctorId,
            TokenStatus status
    );
    Optional<Token> findTopByDoctorIdAndStatusInOrderByCalledAtDesc(
            Long doctorId,
            List<TokenStatus> statuses
    );

    @Query("select t from Token t join fetch t.patient where t.id = :id")
    Optional<Token> findByIdWithPatient(@Param("id") Long id);

    @Query("select count(t) from Token t where t.createdAt >= :start and t.createdAt < :end")
    long countCreatedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select avg(t.consultDurationMins) from Token t where t.status = com.smartqueue.backend.enums.TokenStatus.COMPLETED and t.consultationEnd >= :start and t.consultationEnd < :end and t.consultDurationMins is not null")
    Double averageConsultDurationBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select avg(t.consultDurationMins) from Token t where t.status = com.smartqueue.backend.enums.TokenStatus.COMPLETED and t.doctorId = :doctorId and t.consultationEnd >= :start and t.consultationEnd < :end and t.consultDurationMins is not null")
    Double averageConsultDurationForDoctorBetween(@Param("doctorId") Long doctorId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = """
            SELECT AVG(EXTRACT(EPOCH FROM (t.called_at - t.created_at)) / 60.0)
            FROM tokens t
            WHERE t.called_at IS NOT NULL
              AND t.created_at >= :start
              AND t.created_at < :end
            """, nativeQuery = true)
    Double averageWaitMinutesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Token> findTop20ByStatusOrderByConsultationEndDesc(TokenStatus status);

    @Query("""
            select t from Token t
            left join fetch t.patient
            where t.status = :status
            order by t.consultationEnd desc
            """)
    List<Token> findTop20ByStatusOrderByConsultationEndDescWithPatient(@Param("status") TokenStatus status);

    Optional<Token> findTopByPatientIdAndStatusInOrderByCreatedAtDesc(Long patientId, List<TokenStatus> statuses);

    boolean existsByPatientIdAndStatusIn(Long patientId, List<TokenStatus> statuses);

    List<Token> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    List<Token> findByStatusInOrderByCreatedAtDesc(List<TokenStatus> statuses);

    @Query("select distinct t from Token t left join fetch t.patient where t.status in :statuses order by t.createdAt desc")
    List<Token> findByStatusInOrderByCreatedAtDescWithPatient(@Param("statuses") List<TokenStatus> statuses);

    Optional<Token> findFirstByPatientIdAndStatusIn(Long id, List<TokenStatus> waiting);

    Optional<Token> findTopByPatientIdOrderByCreatedAtDesc(Long patientId);
}
