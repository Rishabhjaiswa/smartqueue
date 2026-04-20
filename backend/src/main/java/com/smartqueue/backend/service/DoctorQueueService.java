package com.smartqueue.backend.service;

import com.smartqueue.backend.dto.ReceptionOverviewDTO;
import com.smartqueue.backend.dto.TokenResponse;
import com.smartqueue.backend.dto.AdminAnalyticsDTO;
import com.smartqueue.backend.dto.HistoryTokenDTO;
import com.smartqueue.backend.entity.Doctor;
import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.TokenStatus;
import com.smartqueue.backend.repository.DoctorRepository;
import com.smartqueue.backend.repository.TokenRepository;
import com.smartqueue.backend.dto.DoctorQueueDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.util.Optional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoctorQueueService {

    private final Optional<RedisTemplate<String, String>> redisTemplate;
    private final TokenRepository tokenRepository;
    private final WebSocketBroadcastService broadcastService;
    private final WaitTimeEstimator waitTimeEstimator;
    private final DoctorRepository doctorRepository;
    private final TelegramService telegramService;
    private final AuditLogService auditLogService;
    private final ScheduledExecutorService consultationExecutor = Executors.newScheduledThreadPool(2);
    private final Map<Long, ScheduledFuture<?>> autoStartTasks = new ConcurrentHashMap<>();
    private final Map<Long, ScheduledFuture<?>> autoCompleteTasks = new ConcurrentHashMap<>();
    private final Map<Long, Long> consultationDeadlineEpoch = new ConcurrentHashMap<>();

    @Value("${smartqueue.enable-auto-rebalance:true}")
    private boolean enableAutoRebalance;

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

    private static final int AUTO_START_DELAY_SECONDS = 10;
    private static final int EXTEND_MINUTES = 5;

    // ✅ CALL NEXT PATIENT
    public TokenResponse callNext(Long doctorId) {

        String key = "queue:doctor:" + doctorId;
        Doctor doctor = doctorRepository.findById(doctorId).orElse(null);

        if (doctor != null && !doctor.isAvailable()) {
            return TokenResponse.builder()
                    .message("Doctor is unavailable and cannot call the next patient")
                    .build();
        }

        Token activeToken = tokenRepository
                .findTopByDoctorIdAndStatusInOrderByCalledAtDesc(
                        doctorId,
                        List.of(TokenStatus.IN_CONSULTATION, TokenStatus.CALLED)
                )
                .orElse(null);

        if (activeToken != null) {
            return TokenResponse.builder()
                    .message("Current token already active")
                    .build();
        }

        // Get first token from Redis (lowest score = highest priority)
        Set<String> top;

        if (!isRedisAvailable("callNext-range", false)) {
            return TokenResponse.builder()
                    .message("Redis not available / Queue empty")
                    .build();
        }

        try {
            top = redisTemplate.get().opsForZSet().range(key, 0, 0);
        } catch (Exception e) {
            return TokenResponse.builder()
                    .message("Redis not available / Queue empty")
                    .build();
        }

        if (top == null || top.isEmpty()) {
            return TokenResponse.builder()
                    .message("Queue empty.")
                    .build();
        }

        String tokenIdStr = top.iterator().next();
        Long tokenId;

        try {
            tokenId = Long.parseLong(tokenIdStr);
        } catch (NumberFormatException e) {
            if (isRedisAvailable("callNext-remove-invalid", true)) {
                redisTemplate.get().opsForZSet().remove(key, tokenIdStr);
            }
            return TokenResponse.builder()
                    .message("Invalid token entry in queue")
                    .build();
        }

        Token token = tokenRepository.findByIdWithPatient(tokenId)
                .orElse(null);

        if (token == null) {
            if (isRedisAvailable("callNext-remove-notfound", true)) {
                redisTemplate.get().opsForZSet().remove(key, tokenIdStr);
            }
            return TokenResponse.builder()
                    .message("Token not found")
                    .build();
        }

        if (token.getStatus() != TokenStatus.WAITING) {
            if (isRedisAvailable("callNext-remove-notwaiting", true)) {
                redisTemplate.get().opsForZSet().remove(key, tokenIdStr);
            }
            return TokenResponse.builder()
                    .message("Only WAITING tokens can be called")
                    .build();
        }

        if (isRedisAvailable("callNext-remove-success", true)) {
            redisTemplate.get().opsForZSet().remove(key, tokenIdStr);
        }

        // Update status and start consultation immediately
        token.setStatus(TokenStatus.IN_CONSULTATION);
        token.setCalledAt(LocalDateTime.now());
        token.setConsultationStart(LocalDateTime.now());

        tokenRepository.save(token);
        broadcastService.broadcastCurrentToken(
                doctorId,
                token.getTokenNumber()
        );
        cancelAutoStart(token.getId());
        scheduleAutoComplete(token);

        if (token.getPatient() != null) {
            broadcastService.notifyPatient(
                    token.getPatient().getId(),
                    "Your token " + token.getTokenNumber() + " is being called. Please proceed."
            );

            if (token.getPatient().getTelegramChatId() != null) {
                telegramService.sendMessage(
                        token.getPatient().getTelegramChatId(),
                        "<b>SmartQueue Update</b>\n"
                                + "Token <b>" + token.getTokenNumber() + "</b> is now being called.\n"
                                + "Please proceed to the consultation room."
                );
            }
        }
        notifyUpcomingPatient(doctorId);

        broadcastService.broadcastDoctorQueue(
                doctorId,
                buildDoctorQueueDTO(doctorId)
        );

        broadcastService.broadcastReceptionOverview(
                buildReceptionOverview()
        );

        return buildResponse(token);
    }

    // ✅ START CONSULTATION
    public void startConsultation(Long tokenId, Long doctorId) {

        Token token = tokenRepository.findByIdWithPatient(tokenId)
                .orElseThrow(() -> new RuntimeException("Token not found"));

        validateOwnership(token, doctorId);

        if (token.getStatus() == TokenStatus.IN_CONSULTATION) {
            return;
        }
        if (token.getStatus() != TokenStatus.CALLED) {
            throw new RuntimeException("Token must be CALLED to start consultation");
        }

        cancelAutoStart(tokenId);
        token.setStatus(TokenStatus.IN_CONSULTATION);
        token.setConsultationStart(LocalDateTime.now());

        tokenRepository.save(token);
        scheduleAutoComplete(token);

        broadcastService.broadcastDoctorQueue(
                doctorId,
                buildDoctorQueueDTO(doctorId)
        );

        broadcastService.broadcastReceptionOverview(
                buildReceptionOverview()
        );

    }


    // ✅ COMPLETE CONSULTATION
    public void completeConsultation(Long tokenId, Long doctorId) {

        Token token = tokenRepository.findByIdWithPatient(tokenId)
                .orElseThrow(() -> new RuntimeException("Token not found"));

        validateOwnership(token, doctorId);

        LocalDateTime end = LocalDateTime.now();
        if (token.getStatus() != TokenStatus.IN_CONSULTATION) {
            throw new RuntimeException("Token must be IN_CONSULTATION to complete");
        }
        cancelAutoStart(tokenId);
        cancelAutoComplete(tokenId);
        int duration = 0;

        if (token.getConsultationStart() != null) {
            duration = (int) Duration.between(
                    token.getConsultationStart(), end
            ).toMinutes();
        }
        token.setStatus(TokenStatus.COMPLETED);
        token.setConsultationEnd(end);
        token.setConsultDurationMins(duration);

        tokenRepository.save(token);

        if (isRedisAvailable("completeConsultation-remove", true)) {
            try {
                redisTemplate.get().opsForZSet().remove(
                        "queue:doctor:" + doctorId,
                        tokenId.toString()
                );
            } catch (Exception ignored) {
            }
        }

        if (token.getPatient() != null && token.getPatient().getTelegramChatId() != null) {
            telegramService.sendMessage(
                    token.getPatient().getTelegramChatId(),
                    "<b>SmartQueue Update</b>\n"
                            + "Consultation for token <b>" + token.getTokenNumber() + "</b> is complete.\n"
                            + "You may leave the consultation room."
            );
        }

        broadcastService.broadcastDoctorQueue(
                doctorId,
                buildDoctorQueueDTO(doctorId)
        );

        broadcastService.broadcastReceptionOverview(
                buildReceptionOverview()
        );
    }

    public DoctorQueueDTO buildDoctorQueueDTO(Long doctorId) {

        String key = "queue:doctor:" + doctorId;

        // ✅ Fetch doctor
        Doctor doctor = doctorRepository.findById(doctorId).orElse(null);

        // ✅ Get current token (CALLED)
        Token currentRef = tokenRepository
                .findTopByDoctorIdAndStatusInOrderByCalledAtDesc(
                        doctorId,
                        List.of(TokenStatus.IN_CONSULTATION, TokenStatus.CALLED)
                )
                .orElse(null);
        Token current = currentRef != null
                ? tokenRepository.findByIdWithPatient(currentRef.getId()).orElse(currentRef)
                : null;

        // ✅ Get all tokens from Redis
        Set<String> tokenIds;
        if (!isRedisAvailable("buildDoctorQueueDTO-range", false)) {
            tokenIds = java.util.Collections.emptySet();
        } else {
            try {
                tokenIds = redisTemplate.get().opsForZSet()
                        .range(key, 0, -1);
            } catch (Exception e) {
                tokenIds = java.util.Collections.emptySet();
            }
        }

        if (tokenIds == null || tokenIds.isEmpty()) {
            return DoctorQueueDTO.builder()
                    .doctorId(doctorId)
                    .doctorName(doctor != null ? doctor.getName() : "Doctor " + doctorId)
                    .roomNumber(doctor != null ? doctor.getRoomNumber() : "Room")
                    .doctorAvailable(doctor != null && doctor.isAvailable())

                    .currentToken(
                            current != null ? current.getTokenNumber() : "N/A"
                    )
                    .currentTokenId(current != null ? current.getId() : null)
                    .currentPatientName(
                            current != null && current.getPatient() != null
                                    ? current.getPatient().getName()
                                    : "N/A"
                    )
                    .currentVisitType(current != null && current.getVisitType() != null ? current.getVisitType().name() : null)
                    .currentSeverityScore(current != null ? current.getSeverityScore() : null)
                    .remainingConsultationSeconds(current != null ? getRemainingConsultationSeconds(current.getId()) : null)

                    .waitingCount(0)
                    .estimatedWaitMinutes(0)
                    .nextTokens(java.util.List.of())
                    .build();
        }

        List<DoctorQueueDTO.QueueEntryDTO> nextTokens = new ArrayList<>();

        int position = 0;

        for (String tokenIdStr : tokenIds) {

            Long tokenId = Long.parseLong(tokenIdStr);

            Token token = tokenRepository.findByIdWithPatient(tokenId)
                    .orElse(null);

            if (token == null) continue;

            // ✅ Safe wait time
            int waitTime = estimateWaitMinutesForPosition(doctorId, position, current);

            nextTokens.add(
                    DoctorQueueDTO.QueueEntryDTO.builder()
                            .tokenNumber(token.getTokenNumber())
                            .patientName(
                                    token.getPatient() != null
                                            ? token.getPatient().getName()
                                            : "Walk-in"
                            )
                            .visitType(token.getVisitType() != null ? token.getVisitType().name() : null)
                            .severityScore(token.getSeverityScore())
                            .estimatedWaitMinutes(waitTime)
                            .build()
            );

            position++;
        }

        int totalWait = nextTokens.isEmpty()
                ? 0
                : nextTokens.get(nextTokens.size() - 1).getEstimatedWaitMinutes();

        return DoctorQueueDTO.builder()
                .doctorId(doctorId)
                .doctorName(doctor != null ? doctor.getName() : "Doctor " + doctorId)
                .roomNumber(doctor != null ? doctor.getRoomNumber() : "Room")
                .doctorAvailable(doctor != null && doctor.isAvailable())

                .currentToken(
                        current != null ? current.getTokenNumber() : "N/A"
                )
                .currentTokenId(current != null ? current.getId() : null)
                .currentPatientName(
                        current != null && current.getPatient() != null
                                ? current.getPatient().getName()
                                : "N/A"
                )
                .currentVisitType(current != null && current.getVisitType() != null ? current.getVisitType().name() : null)
                .currentSeverityScore(current != null ? current.getSeverityScore() : null)
                .remainingConsultationSeconds(current != null ? getRemainingConsultationSeconds(current.getId()) : null)

                .waitingCount(nextTokens.size())
                .estimatedWaitMinutes(totalWait)
                .nextTokens(nextTokens)
                .build();
    }

    public ReceptionOverviewDTO buildReceptionOverview() {

        List<Doctor> doctors = doctorRepository.findByAvailableTrue();

        List<ReceptionOverviewDTO.DoctorSummary> summaries = new ArrayList<>();

        int totalWaiting = 0;

        for (Doctor doctor : doctors) {

            DoctorQueueDTO queue = buildDoctorQueueDTO(doctor.getId());

            summaries.add(
                    ReceptionOverviewDTO.DoctorSummary.builder()
                            .doctorId(doctor.getId())
                            .doctorName(doctor.getName())
                            .currentToken(queue.getCurrentToken())
                            .waitingCount(queue.getWaitingCount())
                            .avgConsultTime(
                                    doctor.getAvgConsultMins() != null
                                            ? doctor.getAvgConsultMins()
                                            : 10
                            )
                            .active(queue.getCurrentTokenId() != null || queue.getWaitingCount() > 0)
                            .nextTokens(queue.getNextTokens().stream()
                                    .limit(3)
                                    .map(DoctorQueueDTO.QueueEntryDTO::getTokenNumber)
                                    .toList())
                            .build()
            );

            totalWaiting += queue.getWaitingCount();
        }

        return ReceptionOverviewDTO.builder()
                .totalDoctorsActive(doctors.size())
                .totalPatientsWaiting(totalWaiting)
                .doctors(summaries)
                .build();
    }

    // 🔐 CRITICAL SECURITY METHOD
    private void validateOwnership(Token token, Long doctorId) {
        if (token.getDoctorId() == null || !token.getDoctorId().equals(doctorId)) {
            throw new AccessDeniedException(
                    "Token does not belong to this doctor");
        }
    }

    private void broadcastAllDoctors() {

        List<Doctor> doctors = doctorRepository.findByAvailableTrue();

        for (Doctor doctor : doctors) {
            broadcastService.broadcastDoctorQueue(
                    doctor.getId(),
                    buildDoctorQueueDTO(doctor.getId())
            );
        }
    }

    private Long findBestDoctorForToken(Token token, Long excludeDoctorId) {

        // 🔥 1. FOLLOW-UP → same doctor
        if (token.getVisitType() != null && "FOLLOW_UP".equalsIgnoreCase(token.getVisitType().name())) {
            if (token.getDoctorId() != null &&
                    !token.getDoctorId().equals(excludeDoctorId)) {
                return token.getDoctorId();
            }
        }

        List<Doctor> doctors = doctorRepository.findByAvailableTrue();

        Long bestDoctorId = null;
        double bestScore = Double.MAX_VALUE;

        for (Doctor doctor : doctors) {

            if (doctor.getId().equals(excludeDoctorId)) continue;
            if (doctor.getMaxQueueSize() != null && getQueueSize(doctor.getId()) >= doctor.getMaxQueueSize()) continue;

            long queueSize = getQueueSize(doctor.getId());
            double avgTime = doctor.getAvgConsultMins() != null ? doctor.getAvgConsultMins() : 10.0;
            double estimatedWait = queueSize * avgTime;
            double score = queueSize + (estimatedWait / avgTime);

            if (score < bestScore) {
                bestScore = score;
                bestDoctorId = doctor.getId();
            }
        }

        return bestDoctorId;
    }

    private void redistributeQueue(Long fromDoctorId) {

        String fromKey = "queue:doctor:" + fromDoctorId;

        if (!isRedisAvailable("redistributeQueue-range", false)) return;

        Set<String> tokens = redisTemplate.get().opsForZSet()
                .range(fromKey, 0, -1);

        if (tokens == null || tokens.isEmpty()) return;

        for (String tokenIdStr : tokens) {

            Long tokenId = Long.parseLong(tokenIdStr);

            Token token = tokenRepository.findById(tokenId)
                    .orElse(null);

            if (token == null) continue;

            // 🔥 STEP 1: Find best doctor dynamically EACH TIME
            Long toDoctorId = findBestDoctorForToken(token,fromDoctorId);

            if (toDoctorId == null) {
                continue;
            }

            String toKey = "queue:doctor:" + toDoctorId;

            // 🔥 STEP 2: Update DB
            token.setDoctorId(toDoctorId);
            tokenRepository.save(token);

            if (isRedisAvailable("redistributeQueue-move", true)) {
                Double score = redisTemplate.get().opsForZSet()
                        .score(fromKey, tokenIdStr);

                if (score != null) {
                    redisTemplate.get().opsForZSet().add(toKey, tokenIdStr, score);
                }

                redisTemplate.get().opsForZSet().remove(fromKey, tokenIdStr);
            }
        }

        // 🔥 STEP 4: Broadcast for ALL doctors
        broadcastAllDoctors();
    }

    public void setAvailability(Long doctorId, boolean available) {

        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        boolean wasAvailable = doctor.isAvailable();
        doctor.setAvailable(available);
        doctorRepository.save(doctor);
        log.info("AUDIT doctor-availability doctorId={} available={}", doctorId, available);
        auditLogService.log(
                "DOCTOR_AVAILABILITY_CHANGED",
                "doctor-" + doctorId,
                "Doctor " + doctorId + " availability changed to " + available
        );

        if (!available) {
            redistributeQueue(doctorId);
        } else if (!wasAvailable && enableAutoRebalance) {
            rebalanceAvailableQueues();
        }

        broadcastAllDoctors();
        broadcastService.broadcastReceptionOverview(
                buildReceptionOverview()
        );
    }

    public void extendConsultation(Long tokenId, Long doctorId) {
        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Token not found"));

        validateOwnership(token, doctorId);

        if (token.getStatus() != TokenStatus.IN_CONSULTATION) {
            throw new RuntimeException("Only IN_CONSULTATION tokens can be extended");
        }

        long remainingSeconds = getRemainingConsultationSeconds(tokenId) != null
                ? getRemainingConsultationSeconds(tokenId)
                : Duration.ofMinutes(EXTEND_MINUTES).getSeconds();
        scheduleAutoComplete(token, remainingSeconds + Duration.ofMinutes(EXTEND_MINUTES).getSeconds());
        notifyDelayToUpcomingPatient(doctorId);

        broadcastService.broadcastDoctorQueue(
                doctorId,
                buildDoctorQueueDTO(doctorId)
        );
        broadcastService.broadcastReceptionOverview(
                buildReceptionOverview()
        );
    }

    private Long findBestDoctor(Long excludeDoctorId) {

        List<Doctor> doctors = doctorRepository.findByAvailableTrue();

        Long bestDoctorId = null;
        double bestScore = Double.MAX_VALUE;

        for (Doctor doctor : doctors) {

            if (doctor.getId().equals(excludeDoctorId)) continue;
            if (doctor.getMaxQueueSize() != null && getQueueSize(doctor.getId()) >= doctor.getMaxQueueSize()) continue;

            long queueSize = getQueueSize(doctor.getId());
            double avgTime = doctor.getAvgConsultMins() != null ? doctor.getAvgConsultMins() : 10.0;
            double estimatedWait = queueSize * avgTime;
            double score = queueSize + (estimatedWait / avgTime);

            if (score < bestScore) {
                bestScore = score;
                bestDoctorId = doctor.getId();
            }
        }

        if (bestDoctorId == null) {
            throw new RuntimeException("No suitable doctor found for redistribution");
        }

        return bestDoctorId;
    }

    private void rebalanceAvailableQueues() {
        List<Doctor> availableDoctors = doctorRepository.findByAvailableTrue();
        if (availableDoctors.size() < 2) {
            return;
        }

        List<Long> doctorIds = availableDoctors.stream()
                .map(Doctor::getId)
                .toList();

        if (!isRedisAvailable("rebalanceAvailableQueues", true)) return;

        List<Token> waitingTokens = new ArrayList<>();
        for (Long doctorId : doctorIds) {
            String key = "queue:doctor:" + doctorId;
            Set<String> tokenIds = redisTemplate.get().opsForZSet().range(key, 0, -1);
            if (tokenIds == null || tokenIds.isEmpty()) {
                continue;
            }

            for (String tokenIdStr : tokenIds) {
                try {
                    Long tokenId = Long.parseLong(tokenIdStr);
                    tokenRepository.findById(tokenId)
                            .filter(token -> token.getStatus() == TokenStatus.WAITING)
                            .ifPresent(waitingTokens::add);
                } catch (NumberFormatException ex) {
                    log.debug("Skipping invalid token id {} during rebalance", tokenIdStr);
                }
            }

            redisTemplate.get().delete(key);
        }

        waitingTokens.stream()
                .sorted((left, right) -> {
                    int scoreCompare = Long.compare(
                            left.getPriorityScore() != null ? left.getPriorityScore() : Long.MAX_VALUE,
                            right.getPriorityScore() != null ? right.getPriorityScore() : Long.MAX_VALUE
                    );
                    if (scoreCompare != 0) {
                        return scoreCompare;
                    }
                    return left.getCreatedAt().compareTo(right.getCreatedAt());
                })
                .forEach(token -> {
                    Long newDoctorId = findBestDoctorForToken(token, null);
                    if (newDoctorId == null) {
                        return;
                    }

                    if (!newDoctorId.equals(token.getDoctorId())) {
                        token.setDoctorId(newDoctorId);
                        tokenRepository.save(token);
                    }

                    try {
                        redisTemplate.get().opsForZSet().add(
                                "queue:doctor:" + newDoctorId,
                                token.getId().toString(),
                                token.getPriorityScore()
                        );
                    } catch (Exception ex) {
                        log.warn("Unable to rebalance token {} to doctor {}: {}", token.getId(), newDoctorId, ex.getMessage());
                    }
                });
    }

    public AdminAnalyticsDTO buildAdminAnalytics() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();

        Double avgConsult = tokenRepository.averageConsultDurationBetween(start, end);
        Double avgWait = tokenRepository.averageWaitMinutesBetween(start, end);
        double fallbackConsultAverage = doctorRepository.findAll().stream()
                .mapToInt(doctor -> doctor.getAvgConsultMins() != null ? doctor.getAvgConsultMins() : 10)
                .average()
                .orElse(10);

        List<AdminAnalyticsDTO.DoctorPerformanceDTO> performance = doctorRepository.findAll()
                .stream()
                .map(doctor -> AdminAnalyticsDTO.DoctorPerformanceDTO.builder()
                        .doctorId(doctor.getId())
                        .doctorName(doctor.getName())
                        .averageConsultMinutes(Math.max(
                                1,
                                (int) Math.round(
                                        tokenRepository.averageConsultDurationForDoctorBetween(doctor.getId(), start, end) != null
                                                ? tokenRepository.averageConsultDurationForDoctorBetween(doctor.getId(), start, end)
                                                : (doctor.getAvgConsultMins() != null ? doctor.getAvgConsultMins() : 10)
                                )
                        ))
                        .waitingCount((int) getQueueSize(doctor.getId()))
                        .available(doctor.isAvailable())
                        .build())
                .toList();

        return AdminAnalyticsDTO.builder()
                .totalPatientsToday(tokenRepository.countCreatedBetween(start, end))
                .averageConsultMinutes(avgConsult != null ? avgConsult : fallbackConsultAverage)
                .averageWaitMinutes(avgWait != null ? avgWait : Math.max(1, Math.round(fallbackConsultAverage / 2.0)))
                .doctorPerformance(performance)
                .build();
    }

    public List<HistoryTokenDTO> getRecentHistory() {
        return tokenRepository.findTop20ByStatusOrderByConsultationEndDescWithPatient(TokenStatus.COMPLETED)
                .stream()
                .map(token -> HistoryTokenDTO.builder()
                        .tokenNumber(token.getTokenNumber())
                        .patientName(token.getPatient() != null ? token.getPatient().getName() : "Patient")
                        .doctorName(doctorRepository.findById(token.getDoctorId()).map(Doctor::getName).orElse("Doctor"))
                        .status(token.getStatus().name())
                        .createdAt(token.getCreatedAt())
                        .consultationEnd(token.getConsultationEnd())
                        .build())
                .toList();
    }

    public Long getRemainingConsultationSeconds(Long tokenId) {
        Long deadline = consultationDeadlineEpoch.get(tokenId);
        if (deadline == null) {
            return null;
        }
        return Math.max(0, deadline - (System.currentTimeMillis() / 1000));
    }

    private long getQueueSize(Long doctorId) {
        if (!isRedisAvailable("getQueueSize", false)) return 0L;
        try {
            Long queueSize = redisTemplate.get().opsForZSet().zCard("queue:doctor:" + doctorId);
            return queueSize != null ? queueSize : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private void scheduleAutoStart(Long tokenId, Long doctorId) {
        cancelAutoStart(tokenId);
        ScheduledFuture<?> future = consultationExecutor.schedule(() -> {
            try {
                Token token = tokenRepository.findById(tokenId).orElse(null);
                if (token != null && token.getStatus() == TokenStatus.CALLED) {
                    startConsultation(tokenId, doctorId);
                }
            } catch (Exception ignored) {
            }
        }, AUTO_START_DELAY_SECONDS, TimeUnit.SECONDS);
        autoStartTasks.put(tokenId, future);
    }

    private void scheduleAutoComplete(Token token) {
        int avgConsult = doctorRepository.findById(token.getDoctorId())
                .map(doctor -> doctor.getAvgConsultMins() != null ? doctor.getAvgConsultMins() : 10)
                .orElse(10);
        scheduleAutoComplete(token, Duration.ofMinutes(avgConsult).getSeconds());
    }

    private void scheduleAutoComplete(Token token, long delaySeconds) {
        cancelAutoComplete(token.getId());
        long deadline = (System.currentTimeMillis() / 1000) + delaySeconds;
        consultationDeadlineEpoch.put(token.getId(), deadline);
        ScheduledFuture<?> future = consultationExecutor.schedule(() -> {
            try {
                Token latest = tokenRepository.findById(token.getId()).orElse(null);
                if (latest != null && latest.getStatus() == TokenStatus.IN_CONSULTATION) {
                    completeConsultation(latest.getId(), latest.getDoctorId());
                }
            } catch (Exception ignored) {
            }
        }, delaySeconds, TimeUnit.SECONDS);
        autoCompleteTasks.put(token.getId(), future);
    }

    private void cancelAutoStart(Long tokenId) {
        ScheduledFuture<?> future = autoStartTasks.remove(tokenId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void cancelAutoComplete(Long tokenId) {
        consultationDeadlineEpoch.remove(tokenId);
        ScheduledFuture<?> future = autoCompleteTasks.remove(tokenId);
        if (future != null) {
            future.cancel(false);
        }
    }

    // 🔧 Helper
    private TokenResponse buildResponse(Token token) {
        return TokenResponse.builder()
                .id(token.getId())
                .tokenNumber(token.getTokenNumber())
                .status(token.getStatus().name())
                .message("Next patient called")
                .build();
    }

    private void notifyUpcomingPatient(Long doctorId) {
        if (!isRedisAvailable("notifyUpcomingPatient", false)) return;
        try {
            Set<String> nextTokens = redisTemplate.get().opsForZSet().range("queue:doctor:" + doctorId, 0, 0);
            if (nextTokens == null || nextTokens.isEmpty()) {
                return;
            }

            Long nextTokenId = Long.parseLong(nextTokens.iterator().next());
            Token nextToken = tokenRepository.findByIdWithPatient(nextTokenId).orElse(null);
            Doctor doctor = doctorRepository.findById(doctorId).orElse(null);

            if (nextToken == null || nextToken.getPatient() == null || nextToken.getPatient().getTelegramChatId() == null) {
                return;
            }

            int avgConsult = doctor != null && doctor.getAvgConsultMins() != null
                    ? doctor.getAvgConsultMins()
                    : 10;

            telegramService.sendMessage(
                    nextToken.getPatient().getTelegramChatId(),
                    "<b>SmartQueue Update</b>\n"
                            + "You are next after the current consultation.\n"
                            + "Token: <b>" + nextToken.getTokenNumber() + "</b>\n"
                            + "Expected turn: <b>" + avgConsult + "-" + (avgConsult + 10) + " min</b>"
            );
        } catch (Exception e) {
            log.debug("Unable to notify upcoming patient for doctor {}: {}", doctorId, e.getMessage());
        }
    }

    private void notifyDelayToUpcomingPatient(Long doctorId) {
        try {
            Set<String> nextTokens = redisTemplate.get().opsForZSet().range("queue:doctor:" + doctorId, 0, 0);
            if (nextTokens == null || nextTokens.isEmpty()) {
                return;
            }

            Long nextTokenId = Long.parseLong(nextTokens.iterator().next());
            Token nextToken = tokenRepository.findByIdWithPatient(nextTokenId).orElse(null);
            if (nextToken == null || nextToken.getPatient() == null || nextToken.getPatient().getTelegramChatId() == null) {
                return;
            }

            telegramService.sendMessage(
                    nextToken.getPatient().getTelegramChatId(),
                    "<b>SmartQueue Update</b>\n"
                            + "Doctor is running behind on the current consultation.\n"
                            + "Your token <b>" + nextToken.getTokenNumber() + "</b> may be delayed slightly."
            );
        } catch (Exception e) {
            log.debug("Unable to notify delay for doctor {}: {}", doctorId, e.getMessage());
        }
    }

    private int estimateWaitMinutesForPosition(Long doctorId, int zeroBasedPosition, Token currentToken) {
        int avgConsultMinutes = waitTimeEstimator.estimateAverageConsultMinutes(doctorId);
        int remainingCurrentConsultation = 0;

        if (currentToken != null && currentToken.getStatus() == TokenStatus.IN_CONSULTATION) {
            Long remainingSeconds = getRemainingConsultationSeconds(currentToken.getId());
            if (remainingSeconds != null && remainingSeconds > 0) {
                remainingCurrentConsultation = (int) Math.ceil(remainingSeconds / 60.0);
            }
        }

        return remainingCurrentConsultation + (zeroBasedPosition * avgConsultMinutes);
    }
}
