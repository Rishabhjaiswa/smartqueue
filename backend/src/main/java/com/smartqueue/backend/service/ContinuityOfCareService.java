package com.smartqueue.backend.service;

import com.smartqueue.backend.entity.DoctorAssignmentHistory;
import com.smartqueue.backend.entity.Doctor;
import com.smartqueue.backend.entity.Patient;
import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.repository.DoctorAssignmentHistoryRepository;
import com.smartqueue.backend.repository.DoctorRepository;
import com.smartqueue.backend.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Phase 3 — Doctor-Specialist Continuity of Care.
 *
 * Responsibilities:
 *  - Record every patient-doctor assignment in history
 *  - Update patient.preferredDoctorId on token completion
 *  - Suggest the preferred doctor when a returning patient checks in
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContinuityOfCareService {

    private final DoctorAssignmentHistoryRepository historyRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    /**
     * Called immediately after a token is assigned to a doctor.
     * Records the assignment in history for audit and continuity.
     */
    @Transactional
    public void recordAssignment(Token token, Doctor doctor, Patient patient) {
        if (patient == null || doctor == null) return;

        DoctorAssignmentHistory entry = DoctorAssignmentHistory.builder()
                .patientId(patient.getId())
                .doctorId(doctor.getId())
                .tokenId(token.getId())
                .assignedAt(LocalDateTime.now())
                .specialization(doctor.getSpecialization())
                .visitType(token.getVisitType() != null ? token.getVisitType().name() : null)
                .chiefComplaint(token.getChiefComplaint())
                .build();

        historyRepository.save(entry);
        log.info("Assignment recorded: patient={} doctor={} ({})",
                patient.getId(), doctor.getId(), doctor.getSpecialization());
    }

    /**
     * Called when a token reaches COMPLETED status.
     * Updates patient.preferredDoctorId to the most frequent doctor
     * (or the most recent one if only 1 visit so far).
     */
    @Transactional
    public void updatePreferredDoctor(Long patientId) {
        Optional<Long> preferredId = historyRepository
                .findMostFrequentDoctorIdForPatient(patientId);

        if (preferredId.isEmpty()) return;

        patientRepository.findById(patientId).ifPresent(p -> {
            if (!preferredId.get().equals(p.getPreferredDoctorId())) {
                p.setPreferredDoctorId(preferredId.get());
                patientRepository.save(p);
                log.info("Preferred doctor updated: patient={} preferredDoctor={}",
                        patientId, preferredId.get());
            }
        });
    }

    /**
     * Returns the suggested doctor for a returning patient.
     * Used by the check-in flow to pre-populate doctorId.
     */
    public Optional<Doctor> suggestDoctorForPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .filter(p -> p.getPreferredDoctorId() != null)
                .flatMap(p -> doctorRepository.findById(p.getPreferredDoctorId()))
                .filter(Doctor::isAvailable);
    }

    /**
     * Full visit history for a patient (for frontend patient card / Telegram).
     */
    public List<DoctorAssignmentHistory> getPatientHistory(Long patientId) {
        return historyRepository.findByPatientIdOrderByAssignedAtDesc(patientId);
    }
}
