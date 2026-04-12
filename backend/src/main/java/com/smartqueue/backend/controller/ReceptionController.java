package com.smartqueue.backend.controller;

import com.smartqueue.backend.dto.TokenRequest;
import com.smartqueue.backend.dto.TokenResponse;
import com.smartqueue.backend.dto.WalkInRequest;
import com.smartqueue.backend.entity.Patient;
import com.smartqueue.backend.entity.Doctor;
import com.smartqueue.backend.enums.PriorityFlag;
import com.smartqueue.backend.enums.ServiceType;
import com.smartqueue.backend.enums.VisitType;
import com.smartqueue.backend.repository.PatientRepository;
import com.smartqueue.backend.repository.DoctorRepository;
import com.smartqueue.backend.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;

@RestController
@RequestMapping("/api/reception")
@RequiredArgsConstructor
public class ReceptionController {

    private final QueueService queueService;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @PostMapping("/check-in")
    public ResponseEntity<TokenResponse> checkIn(
            @RequestBody WalkInRequest request,
            Authentication auth) {

        // Find or create patient
        Patient patient = patientRepository.findByPhone(request.getPhone())
                .orElseGet(() -> patientRepository.save(
                        Patient.builder()
                                .name(request.getName())
                                .phone(request.getPhone())
                                .age(request.getAge())
                                .registeredAt(LocalDateTime.now())
                                .build()
                ));

        // Assign doctor
        Long doctorId = request.getDoctorId() != null
                ? request.getDoctorId()
                : assignBestDoctor(request.getDepartment());

        // Build token request
        TokenRequest tokenReq = new TokenRequest();
        tokenReq.setServiceType(ServiceType.valueOf(request.getDepartment()));
        tokenReq.setPriorityFlag(determinePriority(
                patient.getAge(),
                request.getSeverityScore()
        ));
        tokenReq.setDoctorId(doctorId);
        tokenReq.setPatientId(patient.getId());
        tokenReq.setVisitType(VisitType.WALK_IN);
        tokenReq.setSeverityScore(request.getSeverityScore());

        TokenResponse response = queueService.generateToken(tokenReq);

        return ResponseEntity.ok(response);
    }

    private Long assignBestDoctor(String department) {
        return doctorRepository
                .findBySpecializationAndIsAvailableTrue(department)
                .stream()
                .min(Comparator.comparingLong(d ->
                        Optional.ofNullable(
                                redisTemplate.opsForZSet()
                                        .size("queue:doctor:" + d.getId())
                        ).orElse(0L)))
                .map(Doctor::getId)
                .orElseThrow(() -> new RuntimeException(
                        "No available doctor for: " + department));
    }

    private PriorityFlag determinePriority(int age, Integer severity) {
        if (severity != null && severity >= 80) return PriorityFlag.EMERGENCY;
        if (age >= 60) return PriorityFlag.SENIOR;
        return PriorityFlag.NORMAL;
    }
}