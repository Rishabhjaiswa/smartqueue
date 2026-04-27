package com.smartqueue.backend.repository;

import com.smartqueue.backend.entity.DoctorAssignmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DoctorAssignmentHistoryRepository extends JpaRepository<DoctorAssignmentHistory, Long> {

    /** All visits for a patient, most recent first. */
    List<DoctorAssignmentHistory> findByPatientIdOrderByAssignedAtDesc(Long patientId);

    /** Most recent assignment for a given patient (for preferred doctor lookup). */
    Optional<DoctorAssignmentHistory> findTopByPatientIdOrderByAssignedAtDesc(Long patientId);

    /** All assignments for a specific doctor. */
    List<DoctorAssignmentHistory> findByDoctorIdOrderByAssignedAtDesc(Long doctorId);

    /** Most frequent doctor for a patient — used to suggest preferred specialist. */
    @Query("""
            SELECT h.doctorId FROM DoctorAssignmentHistory h
            WHERE h.patientId = :patientId
            GROUP BY h.doctorId
            ORDER BY COUNT(h.doctorId) DESC
            LIMIT 1
            """)
    Optional<Long> findMostFrequentDoctorIdForPatient(@Param("patientId") Long patientId);
}
