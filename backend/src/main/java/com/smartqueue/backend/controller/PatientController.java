package com.smartqueue.backend.controller;

import com.smartqueue.backend.dto.TokenResponse;
import com.smartqueue.backend.entity.Doctor;
import com.smartqueue.backend.entity.DoctorAssignmentHistory;
import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.repository.DoctorRepository;
import com.smartqueue.backend.repository.TokenRepository;
import com.smartqueue.backend.service.ContinuityOfCareService;
import com.smartqueue.backend.service.DoctorQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * Public (no-auth) endpoints for patient-facing Magic Link status tracking.
 * Whitelisted in SecurityConfig under /api/patient/status/**.
 */
@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class PatientController {

    private final TokenRepository tokenRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorQueueService doctorQueueService;
    private final ContinuityOfCareService continuityOfCareService;
    private final Optional<RedisTemplate<String, String>> redisTemplate;

    /**
     * GET /api/patient/status/{tokenId}
     * Returns live queue status for a given token — no authentication required.
     */
    @GetMapping("/status/{tokenId}")
    public TokenResponse getTokenStatus(@PathVariable Long tokenId) {
        Token token = tokenRepository.findByIdWithPatient(tokenId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Token not found: " + tokenId));

        Doctor doctor = token.getDoctorId() != null
                ? doctorRepository.findById(token.getDoctorId()).orElse(null)
                : null;

        int position  = 0;
        int waitMins  = 0;

        if (token.getDoctorId() != null && redisTemplate.isPresent()) {
            try {
                Long rank = redisTemplate.get().opsForZSet()
                        .rank("queue:doctor:" + token.getDoctorId(), tokenId.toString());
                position = rank != null ? rank.intValue() + 1 : 0;
                int avg  = (doctor != null && doctor.getAvgConsultMins() != null)
                        ? doctor.getAvgConsultMins() : 10;
                waitMins = position * avg;
            } catch (Exception ignored) {
            }
        }

        return TokenResponse.builder()
                .id(token.getId())
                .tokenNumber(token.getTokenNumber())
                .serviceType(token.getServiceType() != null ? token.getServiceType().name() : null)
                .status(token.getStatus().name())
                .positionInQueue(position)
                .estimatedWaitMinutes(waitMins)
                .doctorName(doctor != null ? doctor.getName() : "TBD")
                .roomNumber(doctor != null ? doctor.getRoomNumber() : null)
                .build();
    }

    /**
     * GET /api/patient/{patientId}/history
     * Returns complete doctor assignment history for a patient.
     * Used by reception front-desk card and Telegram continuity context.
     */
    @GetMapping("/{patientId}/history")
    public List<DoctorAssignmentHistory> getPatientHistory(@PathVariable Long patientId) {
        return continuityOfCareService.getPatientHistory(patientId);
    }
}

