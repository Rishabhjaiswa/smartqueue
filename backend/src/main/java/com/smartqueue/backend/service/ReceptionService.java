package com.smartqueue.backend.service;

import com.smartqueue.backend.dto.*;
import com.smartqueue.backend.entity.Doctor;
import com.smartqueue.backend.entity.Patient;
import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.idempotency.IdempotencyService;
import com.smartqueue.backend.repository.DoctorRepository;
import com.smartqueue.backend.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.smartqueue.backend.enums.TokenStatus;

import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceptionService {

    private final QueueService queueService;
    private final DoctorQueueService doctorQueueService;
    private final TokenRepository tokenRepository;
    private final DoctorRepository doctorRepository;
    private final Optional<RedisTemplate<String, String>> redisTemplate;
    private final WebSocketBroadcastService broadcastService;
    private final AuditLogService auditLogService;
    private final IdempotencyService idempotencyService;

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

    // ✅ REAL WALK-IN WITH PATIENT
    public TokenResponse checkInWalkIn(CheckInRequest req) {

        // ── Idempotency guard (SETNX / 60s TTL) ──────────────────────────────
        // Key = user-supplied UUID, or deterministic fallback from request fields.
        // Only the first call within the TTL window proceeds; duplicates get 409.
        String idemKey = (req.getIdempotencyKey() != null && !req.getIdempotencyKey().isBlank())
                ? "checkin:" + req.getIdempotencyKey()
                : "checkin:" + req.getOfficeId() + ":" + req.getPatientName() + ":" + req.getAge();

        if (!idempotencyService.tryAcquire(idemKey, IdempotencyService.CHECKIN_TTL)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Duplicate check-in request — please wait 60 seconds before retrying"
            );
        }

        Patient patient = Patient.builder()
                .name(req.getPatientName())
                .age(req.getAge())
                .phone(req.getPhone())
                .build();

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setServiceType(req.getServiceType());
        tokenRequest.setSeverityScore(req.getSeverityScore());
        tokenRequest.setOfficeId(req.getOfficeId());
        tokenRequest.setRequiresAssistance(req.isRequiresAssistance());
        tokenRequest.setVisitType(
                com.smartqueue.backend.enums.VisitType.WALK_IN
        );
        return queueService.generateToken(tokenRequest, patient);
    }
    public TokenResponse bookAppointment(AppointmentRequest req) {

        Patient patient = Patient.builder()
                .name(req.getPatientName())
                .age(req.getAge())
                .build();

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setServiceType(req.getServiceType());
        tokenRequest.setSeverityScore(req.getSeverityScore());
        tokenRequest.setOfficeId(req.getOfficeId());

        // 🔥 IMPORTANT
        tokenRequest.setVisitType(
                com.smartqueue.backend.enums.VisitType.APPOINTMENT
        );

        tokenRequest.setAppointmentScheduledTime(
                req.getAppointmentTime()
        );

        return queueService.generateToken(tokenRequest, patient);
    }

    public ReceptionOverviewDTO getOverview() {
        return doctorQueueService.buildReceptionOverview(1);
    }

    public void markNoShow(Long tokenId) {
        queueService.markNoShow(tokenId, null);
    }

    public void reassignDoctor(Long tokenId, Long newDoctorId) {
        if (newDoctorId == null) {
            throw new IllegalArgumentException("Please select a doctor for reassignment");
        }

        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Token not found"));

        Long oldDoctorId = token.getDoctorId();
        Doctor newDoctor = doctorRepository.findById(newDoctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        if (oldDoctorId == null || newDoctorId == null) {
            throw new RuntimeException("Doctor assignment is required");
        }
        if (!newDoctor.isAvailable()) {
            throw new RuntimeException("Selected doctor is unavailable");
        }
        Long newQueueSize = isRedisAvailable("getQueueSize", false) ? redisTemplate.get().opsForZSet().zCard("queue:doctor:" + newDoctorId) : 0L;
        if (newDoctor.getMaxQueueSize() != null && newQueueSize != null && newQueueSize >= newDoctor.getMaxQueueSize()) {
            throw new RuntimeException("Selected doctor queue is full");
        }

        // ❌ Prevent same doctor reassignment
        if (oldDoctorId.equals(newDoctorId)) {
            throw new RuntimeException("Already assigned to this doctor");
        }

        boolean oldDoctorUnavailable = doctorRepository.findById(oldDoctorId)
                .map(doctor -> !doctor.isAvailable())
                .orElse(false);

        if (token.getStatus() != TokenStatus.WAITING
                && !(token.getStatus() == TokenStatus.CALLED && oldDoctorUnavailable)) {
            throw new RuntimeException("Only WAITING tokens can be reassigned");
        }

        String oldKey = "queue:doctor:" + oldDoctorId;
        String newKey = "queue:doctor:" + newDoctorId;

        if (isRedisAvailable("reassignDoctor-remove", true)) {
            try {
                redisTemplate.get().opsForZSet().remove(oldKey, tokenId.toString());
            } catch (Exception e) {
                throw new RuntimeException("Unable to update old doctor queue");
            }
        }

        // 🔥 Update DB
        token.setDoctorId(newDoctorId);
        tokenRepository.save(token);
        log.info("AUDIT token-reassignment tokenId={} fromDoctorId={} toDoctorId={} status={}",
                tokenId, oldDoctorId, newDoctorId, token.getStatus());
        auditLogService.log(
                "TOKEN_REASSIGNED",
                "reception",
                "Token " + token.getTokenNumber() + " moved from doctor " + oldDoctorId + " to doctor " + newDoctorId
        );

        if (token.getStatus() == TokenStatus.WAITING && isRedisAvailable("reassignDoctor-add", true)) {
            try {
                redisTemplate.get().opsForZSet().add(
                        newKey,
                        tokenId.toString(),
                        token.getPriorityScore()
                );
            } catch (Exception e) {
                throw new RuntimeException("Unable to update new doctor queue");
            }
        }

        // 🔥 Broadcast BOTH doctors
        broadcastService.broadcastDoctorQueue(
                oldDoctorId,
                doctorQueueService.buildDoctorQueueDTO(oldDoctorId)
        );

        broadcastService.broadcastDoctorQueue(
                newDoctorId,
                doctorQueueService.buildDoctorQueueDTO(newDoctorId)
        );

        int reassignOfficeId = doctorRepository.findById(newDoctorId)
                .map(d -> d.getOfficeId() != null ? d.getOfficeId() : 1)
                .orElse(1);
        broadcastService.broadcastReceptionOverview(
                reassignOfficeId,
                doctorQueueService.buildReceptionOverview(reassignOfficeId)
        );
    }
    public TokenResponse reinstateNoShow(Long tokenId, String reason) {

        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Token not found"));

        if (token.getStatus() != TokenStatus.NO_SHOW && token.getStatus() != TokenStatus.EXPIRED) {
            throw new RuntimeException("Only NO_SHOW or EXPIRED tokens can be reinstated");
        }

        token.setStatus(TokenStatus.WAITING);
        tokenRepository.save(token);

        // 🔥 Add back to Redis queue
        if (token.getDoctorId() == null) {
            throw new RuntimeException("Doctor assignment is required");
        }
        Doctor doctor = doctorRepository.findById(token.getDoctorId())
                .orElseThrow(() -> new RuntimeException("Doctor not found"));
        if (!doctor.isAvailable()) {
            throw new RuntimeException("Assigned doctor is unavailable");
        }

        if (isRedisAvailable("reinstateNoShow", true)) {
            try {
                redisTemplate.get().opsForZSet().add(
                        "queue:doctor:" + token.getDoctorId(),
                        tokenId.toString(),
                        token.getPriorityScore()
                );
            } catch (Exception e) {
                throw new RuntimeException("Unable to reinstate token in queue");
            }
        }

        // 🔥 Broadcast update
        broadcastService.broadcastDoctorQueue(
                token.getDoctorId(),
                doctorQueueService.buildDoctorQueueDTO(token.getDoctorId())
        );

        int reinstateOfficeId = token.getOfficeId() != null ? token.getOfficeId() : 1;
        broadcastService.broadcastReceptionOverview(
                reinstateOfficeId,
                doctorQueueService.buildReceptionOverview(reinstateOfficeId)
        );

        return TokenResponse.builder()
                .id(token.getId())
                .tokenNumber(token.getTokenNumber())
                .status(token.getStatus().name())
                .message("Patient reinstated")
                .build();
    }

    public List<EligibleTokenDTO> getEligibleTokens(List<TokenStatus> statuses) {
        return tokenRepository.findByStatusInOrderByCreatedAtDescWithPatient(statuses)
                .stream()
                .map(token -> EligibleTokenDTO.builder()
                        .id(token.getId())
                        .tokenNumber(token.getTokenNumber())
                        .patientName(token.getPatient() != null ? token.getPatient().getName() : "Patient")
                        .status(token.getStatus().name())
                        .build())
                .toList();
    }

    public List<EligibleTokenDTO> getEligibleTokensByOffice(List<TokenStatus> statuses, int officeId) {
        return tokenRepository.findByStatusInOrderByCreatedAtDescWithPatient(statuses)
                .stream()
                .filter(token -> token.getOfficeId() != null && token.getOfficeId() == officeId)
                .map(token -> EligibleTokenDTO.builder()
                        .id(token.getId())
                        .tokenNumber(token.getTokenNumber())
                        .patientName(token.getPatient() != null ? token.getPatient().getName() : "Patient")
                        .status(token.getStatus().name())
                        .build())
                .toList();
    }
}
