package com.smartqueue.backend.service;

import com.smartqueue.backend.dto.QueueStateDTO;
import com.smartqueue.backend.dto.TokenRequest;
import com.smartqueue.backend.dto.TokenResponse;
import com.smartqueue.backend.entity.Doctor;
import com.smartqueue.backend.entity.Patient;
import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.TokenStatus;
import com.smartqueue.backend.enums.VisitType;
import com.smartqueue.backend.lock.RedissonLockService;
import com.smartqueue.backend.repository.DoctorRepository;
import com.smartqueue.backend.repository.PatientRepository;
import com.smartqueue.backend.repository.TokenRepository;
import com.smartqueue.backend.service.ContinuityOfCareService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueService {

    private final TokenRepository tokenRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final Optional<RedisTemplate<String, String>> redisTemplate;
    private final WebSocketBroadcastService broadcastService;
    private final RedissonLockService lockService;
    private final MeterRegistry meterRegistry;
    private final PriorityEngine priorityEngine;
    private final DoctorQueueService doctorQueueService;
    private final ContinuityOfCareService continuityOfCareService;

    private static final String QUEUE_KEY = "queue:doctor:";

    @Value("${clinic.open-hour:9}")
    private int clinicOpenHour;

    @Value("${clinic.close-hour:17}")
    private int clinicCloseHour;

    @Value("${clinic.break-start-hour:0}")
    private int clinicBreakStartHour;

    @Value("${clinic.break-end-hour:0}")
    private int clinicBreakEndHour;

    @Value("${app.redis.required:false}")
    private boolean redisRequired;

    private boolean isRedisAvailable(String operationName, boolean isCritical) {
        if (redisTemplate.isEmpty()) {
            if (isCritical && redisRequired) {
                throw new IllegalStateException("Redis required for this operation: " + operationName);
            }
            log.warn("Redis unavailable - falling back for operation: {}", operationName);
            return false;
        }
        return true;
    }

    public TokenResponse generateToken(TokenRequest request) {
        return generateToken(request, null);
    }
    public TokenResponse callNextToken(Integer officeId) {
        return TokenResponse.builder()
                .message("Deprecated - use doctor-based flow")
                .build();
    }

    public void completeToken(Long tokenId) {
        tokenRepository.findById(tokenId).ifPresent(t -> {
            t.setStatus(TokenStatus.COMPLETED);
            t.setCompletedAt(LocalDateTime.now());
            t.setConsultationEnd(LocalDateTime.now());
            if (t.getConsultationStart() != null) {
                long duration = java.time.Duration.between(
                        t.getConsultationStart(),
                        t.getConsultationEnd()
                ).toMinutes();
                t.setConsultDurationMins((int) duration);
            }
            tokenRepository.save(t);

            // Phase 3: Update preferred doctor on completion
            if (t.getPatient() != null) {
                continuityOfCareService.updatePreferredDoctor(t.getPatient().getId());
            }

            if (isRedisAvailable("completeToken", true)) {
                redisTemplate.get().opsForZSet().remove(
                        QUEUE_KEY + t.getDoctorId(),
                        t.getId().toString()
                );
            }

            broadcastService.broadcastDoctorQueue(
                    t.getDoctorId(),
                    doctorQueueService.buildDoctorQueueDTO(t.getDoctorId())
            );
            int completeTokenOfficeId = t.getOfficeId() != null ? t.getOfficeId() : 1;
            broadcastService.broadcastReceptionOverview(
                    completeTokenOfficeId,
                    doctorQueueService.buildReceptionOverview(completeTokenOfficeId)
            );
        });
    }

    public void cancelTokenForPatient(Long tokenId, Long patientId) {
        Token token = tokenRepository.findByIdWithPatient(tokenId)
                .orElseThrow(() -> new RuntimeException("Token not found"));

        if (token.getPatient() == null || !token.getPatient().getId().equals(patientId)) {
            throw new RuntimeException("Token does not belong to this patient");
        }

        if (token.getStatus() != TokenStatus.WAITING && token.getStatus() != TokenStatus.CALLED) {
            throw new RuntimeException("Only WAITING or CALLED tokens can be cancelled");
        }

        token.setStatus(TokenStatus.CANCELLED);
        tokenRepository.save(token);

        if (isRedisAvailable("cancelToken", true)) {
            try {
                redisTemplate.get().opsForZSet().remove(
                        QUEUE_KEY + token.getDoctorId(),
                        token.getId().toString()
                );
            } catch (Exception ignored) {
            }
        }

        broadcastService.broadcastDoctorQueue(
                token.getDoctorId(),
                doctorQueueService.buildDoctorQueueDTO(token.getDoctorId())
        );
        int cancelOfficeId = token.getOfficeId() != null ? token.getOfficeId() : 1;
        broadcastService.broadcastReceptionOverview(
                cancelOfficeId,
                doctorQueueService.buildReceptionOverview(cancelOfficeId)
        );
    }

    public void markNoShow(Long tokenId, Integer officeId) {

        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Token not found"));

        // ✅ Prevent invalid state
        if (token.getStatus() != TokenStatus.WAITING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only WAITING tokens can be marked NO_SHOW"
            );
        }

        token.setStatus(TokenStatus.NO_SHOW);
        tokenRepository.save(token);

        // ✅ Remove from Redis safely
        if (isRedisAvailable("markNoShow", true)) {
            redisTemplate.get().opsForZSet().remove(
                    "queue:doctor:" + token.getDoctorId(),
                    tokenId.toString()
            );
        }

        // ✅ Safe broadcast
        broadcastService.broadcastDoctorQueue(
                token.getDoctorId(),
                doctorQueueService.buildDoctorQueueDTO(token.getDoctorId())// or call service
        );
        int noShowOfficeId = token.getOfficeId() != null ? token.getOfficeId() : 1;
        broadcastService.broadcastReceptionOverview(
                noShowOfficeId,
                doctorQueueService.buildReceptionOverview(noShowOfficeId)
        );
    }

    public void staffOverride(String tokenNumber, Integer officeId) {
        // no-op for now
    }

    public TokenResponse generateToken(TokenRequest request, Patient patient) {
        validateClinicAccess(request);
        Patient persistedPatient = persistPatient(patient);

        // ── Distributed lock per patient ──────────────────────────────────────
        // Prevents two concurrent requests for the same patient both passing
        // the duplicate-check and creating two tokens (race condition fix).
        String lockKey = "lock:token:patient:" + (persistedPatient != null ? persistedPatient.getId() : "anon");
        return lockService.executeWithLock(lockKey, 5, () -> generateTokenLocked(request, persistedPatient));
    }

    private TokenResponse generateTokenLocked(TokenRequest request, Patient persistedPatient) {

        // Phase 3: stamp resolved patientId so assignDoctor can use continuity routing
        if (persistedPatient != null) {
            request.setPatientId(persistedPatient.getId());
        }

        // ✅ ACTIVE TOKEN CHECK — only for identified patients
        if (persistedPatient != null) {
            Optional<Token> activeTokenOpt = tokenRepository
                    .findFirstByPatientIdAndStatusIn(
                            persistedPatient.getId(),
                            List.of(TokenStatus.WAITING, TokenStatus.CALLED, TokenStatus.IN_CONSULTATION)
                    );

            if (activeTokenOpt.isPresent()) {
                Token existing = activeTokenOpt.get();
                Doctor doctor = doctorRepository.findById(existing.getDoctorId()).orElse(null);
                int position = getPosition(existing.getId(), existing.getDoctorId());
                int avg = (doctor != null && doctor.getAvgConsultMins() != null && doctor.getAvgConsultMins() > 0)
                        ? doctor.getAvgConsultMins() : 10;
                return TokenResponse.builder()
                        .id(existing.getId())
                        .tokenNumber(existing.getTokenNumber())
                        .status(existing.getStatus().name())
                        .doctorName(doctor != null ? doctor.getName() : "Assigned")
                        .roomNumber(doctor != null ? doctor.getRoomNumber() : null)
                        .positionInQueue(position)
                        .estimatedWaitMinutes(position * avg)
                        .message("ALREADY_EXISTS")
                        .build();
            }

            // ✅ RECENT TOKEN CHECK — only for identified patients
            Optional<Token> recentTokenOpt = tokenRepository
                    .findTopByPatientIdOrderByCreatedAtDesc(persistedPatient.getId());
            if (recentTokenOpt.isPresent()) {
                Token recent = recentTokenOpt.get();
                if ((recent.getStatus() == TokenStatus.COMPLETED
                        || recent.getStatus() == TokenStatus.CANCELLED
                        || recent.getStatus() == TokenStatus.NO_SHOW)
                        && recent.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(10))) {
                    throw new IllegalArgumentException(
                            "You recently booked a token. Please wait or check /status.");
                }
            }
        }
        Doctor doctor = assignDoctor(request);

        int queueSize = isRedisAvailable("getQueueSize", false) ? Optional.ofNullable(
                redisTemplate.get().opsForZSet().size(QUEUE_KEY + doctor.getId())
        ).map(Long::intValue).orElse(0) : 0;

        Token token = Token.builder()
                .doctorId(doctor.getId())
                .tokenNumber(generateNumber(doctor.getId()))
                .serviceType(request.getServiceType())
                .visitType(
                request.getVisitType() != null
                        ? request.getVisitType()
                        : com.smartqueue.backend.enums.VisitType.WALK_IN
        )
                .chiefComplaint(request.getChiefComplaint())
                .severityScore(
                        request.getSeverityScore() != null
                                ? request.getSeverityScore()
                                : 0
                )
                .requiresAssistance(request.isRequiresAssistance())
                .appointmentScheduledTime(request.getAppointmentScheduledTime())
                .status(TokenStatus.WAITING)
                .officeId(request.getOfficeId() != null
                        ? request.getOfficeId()
                        : (doctor.getOfficeId() != null ? doctor.getOfficeId() : 1))
                .createdAt(LocalDateTime.now())
                .build();

        token.setPatient(persistedPatient);

        long score = priorityEngine.computeScore(token, queueSize, resolvePatientAge(persistedPatient));
        score = applyVisitTypeAdjustments(token, score);

        token.setPriorityScore(score);
        token.setDynamicScore(score);
        token.setLastScoreUpdate(LocalDateTime.now());

        tokenRepository.save(token);

        // Phase 3: Record assignment in history
        continuityOfCareService.recordAssignment(token, doctor, persistedPatient);

        validateDoctorAvailability(doctor);

        if (isRedisAvailable("generateToken", true)) {
            try {
                redisTemplate.get().opsForZSet().add(
                        QUEUE_KEY + doctor.getId(),
                        token.getId().toString(),
                        score
                );
            } catch (Exception e) {
                throw new RuntimeException("Unable to add token to doctor queue");
            }
        }

        int position = getPosition(token.getId(), doctor.getId());
        int avg = (doctor.getAvgConsultMins() != null && doctor.getAvgConsultMins() > 0)
                ? doctor.getAvgConsultMins()
                : 10;
        int waitMins = position * avg;

        broadcastService.broadcastDoctorQueue(
                doctor.getId(),
                doctorQueueService.buildDoctorQueueDTO(doctor.getId())
        );
        int tokenOfficeId = request.getOfficeId() != null ? request.getOfficeId() : 1;
        broadcastService.broadcastReceptionOverview(
                tokenOfficeId,
                doctorQueueService.buildReceptionOverview(tokenOfficeId)
        );

        Counter.builder("smartqueue.token.generated")
                .tag("serviceType", token.getServiceType().name())
                .tag("priorityFlag", request.getPriorityFlag() != null ? request.getPriorityFlag().name() : "NORMAL")
                .register(meterRegistry)
                .increment();

        return TokenResponse.builder()
                .id(token.getId())
                .tokenNumber(token.getTokenNumber())
                .serviceType(token.getServiceType().name())
                .status(token.getStatus().name())
                .doctorName(doctor.getName())
                .roomNumber(doctor.getRoomNumber())
                .positionInQueue(position)
                .estimatedWaitMinutes(waitMins)
                .build();
    }

    private Doctor assignDoctor(TokenRequest request) {

        // ── 1. Explicit doctorId override (highest priority) ──────────────
        if (request.getDoctorId() != null) {
            Doctor requestedDoctor = doctorRepository.findById(request.getDoctorId())
                    .orElseThrow(() -> new RuntimeException("Doctor not found"));
            validateDoctorAvailability(requestedDoctor);
            return requestedDoctor;
        }

        // ── 2. Specialization-based routing (Phase 1 + Phase 3) ────────────
        String spec = request.getSuggestedSpecialization();
        if (spec != null && !spec.isBlank()) {
            List<Doctor> specialists = (request.getOfficeId() != null
                    ? doctorRepository.findBySpecializationAndAvailableTrueAndOfficeId(
                            spec.toUpperCase(), request.getOfficeId())
                    : doctorRepository.findBySpecializationAndAvailableTrue(spec.toUpperCase()))
                    .stream()
                    .filter(d -> d.getMaxQueueSize() == null ||
                            getQueueSize(d.getId()) < d.getMaxQueueSize())
                    .toList();

            if (!specialists.isEmpty()) {
                // Phase 3 sub-priority: prefer the patient's last specialist in this pool
                // (only if we have a persisted patient)
                Doctor preferred = null;
                if (request.getPatientId() != null) {
                    preferred = continuityOfCareService
                            .suggestDoctorForPatient(request.getPatientId())
                            .filter(specialists::contains)
                            .orElse(null);
                }

                if (preferred != null) {
                    log.info("Continuity of care: routing patient={} to preferred doctor={} ({})",
                            request.getPatientId(), preferred.getId(), preferred.getName());
                    return preferred;
                }

                // No continuity preference — pick least-loaded specialist
                return leastLoadedDoctor(specialists);
            }
            // No available specialists in that department → fall through to generic
            log.warn("No available {} specialist — falling back to generic pool", spec);
        }

        // ── 3. Generic least-loaded selection ─────────────────────────────
        List<Doctor> available = (request.getOfficeId() != null
                ? doctorRepository.findByAvailableTrueAndOfficeId(request.getOfficeId())
                : doctorRepository.findByAvailableTrue())
                .stream()
                .filter(d -> d.getMaxQueueSize() == null || getQueueSize(d.getId()) < d.getMaxQueueSize())
                .toList();

        return leastLoadedDoctor(available);
    }

    private long getQueueSize(Long doctorId) {
        return isRedisAvailable("getQueueSize", false)
                ? Optional.ofNullable(redisTemplate.get().opsForZSet().size(QUEUE_KEY + doctorId)).orElse(0L)
                : 0L;
    }

    private Doctor leastLoadedDoctor(List<Doctor> pool) {
        return pool.stream()
                .min(Comparator.comparingLong(d -> {
                    long qs      = getQueueSize(d.getId());
                    int  avgTime = Optional.ofNullable(d.getAvgConsultMins()).orElse(10);
                    return qs + (qs * avgTime / avgTime);
                }))
                .orElseThrow(() -> new RuntimeException("No doctors available"));
    }

    private int getPosition(Long tokenId, Long doctorId) {

        Long rank = isRedisAvailable("getQueuePosition", false) ? redisTemplate.get().opsForZSet()
                .rank(QUEUE_KEY + doctorId, tokenId.toString()) : null;

        return rank != null ? rank.intValue() + 1 : 1;
    }

    private String generateNumber(Long doctorId) {
        long count = tokenRepository.count();
        return "D" + doctorId + "-T" + (count + 1);
    }

    @Scheduled(fixedRateString = "${clinic.priority-recalc-interval-seconds:60}000")
    @Transactional
    public void recalculateAllScores() {
        // Cluster-wide leader lock — only one instance runs the recalc job.
        // leaseTime = 55 s < schedule interval (60 s) to auto-release if the
        // instance crashes mid-run before the next trigger fires.
        lockService.executeWithLockIfAvailable("lock:recalc:priority", 55, () -> {
            log.info("Starting priority recalculation for active doctor queues");
            doRecalculateAllScores();
            log.info("Completed priority recalculation");
        });
    }

    private void doRecalculateAllScores() {

        List<Doctor> activeDoctors = doctorRepository.findByAvailableTrue();

        for (Doctor doctor : activeDoctors) {

            String key = QUEUE_KEY + doctor.getId();

            Set<String> ids = isRedisAvailable("recalculatePriorities", false) ? redisTemplate.get().opsForZSet().range(key, 0, -1) : null;
            if (ids == null) continue;

            int queueSize = ids.size();

            for (String idStr : ids) {
                try {
                    tokenRepository.findByIdWithPatient(Long.parseLong(idStr)).ifPresent(token -> {
                        if (token.getStatus() != TokenStatus.WAITING) {
                            return;
                        }
                        if (token.getLastScoreUpdate() != null &&
                                token.getLastScoreUpdate().isAfter(LocalDateTime.now().minusSeconds(30))) {
                            return;
                        }
                        int patientAge = resolvePatientAge(token.getPatient());
                        long newScore = priorityEngine.computeScore(token, queueSize, patientAge);

                        if (isRedisAvailable("recalculateScores", false)) {
                            redisTemplate.get().opsForZSet().add(key, idStr, newScore);
                        }

                        if (!Objects.equals(token.getDynamicScore(), newScore)) {
                            token.setDynamicScore(newScore);
                            token.setLastScoreUpdate(LocalDateTime.now());
                            tokenRepository.save(token);
                            log.info("Updated token score tokenId={} doctorId={} newScore={}", token.getId(), doctor.getId(), newScore);
                        }
                    });
                } catch (Exception ex) {
                    log.warn("Skipping token {} during recalculation: {}", idStr, ex.getMessage());
                }
            }

            broadcastService.broadcastDoctorQueue(
                    doctor.getId(),
                    doctorQueueService.buildDoctorQueueDTO(doctor.getId())
            );
        }
    }

    public QueueStateDTO getQueueState(Long doctorId) {

        String key = QUEUE_KEY + doctorId;

        Set<String> all = isRedisAvailable("getRedisTokens", false) ? redisTemplate.get().opsForZSet().range(key, 0, -1) : null;

        int waitingCount = all != null ? all.size() : 0;

        return QueueStateDTO.builder()
                .waitingCount(waitingCount)
                .avgWaitMinutes(waitingCount * 10)
                .build();
    }

    private void validateDoctorAvailability(Doctor doctor) {
        if (!doctor.isAvailable()) {
            throw new RuntimeException("Selected doctor is unavailable");
        }

        long queueSize = isRedisAvailable("getQueueSize", false) ? Optional.ofNullable(redisTemplate.get().opsForZSet().size(QUEUE_KEY + doctor.getId()))
                .orElse(0L) : 0L;
        if (doctor.getMaxQueueSize() != null && queueSize >= doctor.getMaxQueueSize()) {
            throw new RuntimeException("Selected doctor queue is full");
        }
    }

    private void validateClinicAccess(TokenRequest request) {
        LocalDateTime now = LocalDateTime.now();
        boolean withinClinicHours = now.getHour() >= clinicOpenHour && now.getHour() < clinicCloseHour;
        boolean withinBreak = clinicBreakStartHour < clinicBreakEndHour
                && now.getHour() >= clinicBreakStartHour
                && now.getHour() < clinicBreakEndHour;

        if (withinBreak) {
            throw new RuntimeException("Clinic is currently on break. Please try again shortly");
        }

        if ((request.getVisitType() == null || request.getVisitType() == VisitType.WALK_IN) && !withinClinicHours) {
            throw new RuntimeException("Clinic is currently closed for walk-in registrations");
        }
    }

    private long applyVisitTypeAdjustments(Token token, long baseScore) {
        if (token.getVisitType() == VisitType.APPOINTMENT && token.getAppointmentScheduledTime() != null) {
            long minutesFromAppointment = java.time.Duration.between(LocalDateTime.now(), token.getAppointmentScheduledTime()).toMinutes();
            if (minutesFromAppointment > 30) {
                return baseScore + 6_000_000L;
            }
            if (minutesFromAppointment > 15) {
                return baseScore + 1_500_000L;
            }
            if (minutesFromAppointment >= -15) {
                return Math.max(1, baseScore - 1_200_000L);
            }
            if (minutesFromAppointment >= -30) {
                return baseScore + 600_000L;
            }
            return baseScore + 4_000_000L;
        }

        return baseScore;
    }

    /**
     * Phase 2 — Family Identification (Phone + Name dual-verification).
     *
     * Lookup hierarchy:
     *  1. patient.id already set  → already persisted, return as-is
     *  2. phone provided          → find all patients with that phone
     *     a. name match (case-insensitive) → return existing record
     *     b. name is new                  → create new profile, same phone
     *  3. no phone                → generate temp phone, save as anonymous
     */
    private Patient persistPatient(Patient patient) {
        if (patient == null) return null;

        // Already a persisted entity
        if (patient.getId() != null) return patient;

        String phone = (patient.getPhone() != null && !patient.getPhone().isBlank())
                ? patient.getPhone().trim()
                : null;

        if (phone != null) {
            // ── Step 1: Exact phone + name match → return existing patient ──
            Optional<Patient> exactMatch = patientRepository
                    .findByPhoneAndNameIgnoreCase(phone, patient.getName().trim());
            if (exactMatch.isPresent()) {
                log.info("Patient identified: id={} name={} (phone match)",
                        exactMatch.get().getId(), exactMatch.get().getName());
                return exactMatch.get();
            }

            // ── Step 2: Phone exists but name is new → new family member ────
            List<Patient> samePhone = patientRepository.findByPhone(phone);
            if (!samePhone.isEmpty()) {
                log.info("New family member on existing phone {}: name={}",
                        phone, patient.getName());
            }

            // Fall through: create new profile (new patient OR new family member)
        } else {
            // No phone provided — generate a temporary identifier
            patient.setPhone("T" + System.currentTimeMillis());
        }

        if (patient.getAge() == null || patient.getAge() <= 0) {
            patient.setAge(30);
        }
        if (patient.getCreatedAt() == null) {
            patient.setCreatedAt(LocalDateTime.now());
        }

        Patient saved = patientRepository.save(patient);
        log.info("New patient created: id={} name={} phone={}", saved.getId(), saved.getName(), saved.getPhone());
        return saved;
    }


    private int resolvePatientAge(Patient patient) {
        if (patient == null || patient.getAge() == null || patient.getAge() <= 0) {
            return 30;
        }
        return patient.getAge();
    }

}
