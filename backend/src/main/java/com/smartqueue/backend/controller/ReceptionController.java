package com.smartqueue.backend.controller;

import com.smartqueue.backend.dto.AppointmentRequest;
import com.smartqueue.backend.dto.CheckInRequest;
import com.smartqueue.backend.dto.EligibleTokenDTO;
import com.smartqueue.backend.dto.ReceptionOverviewDTO;
import com.smartqueue.backend.dto.TokenResponse;
import com.smartqueue.backend.entity.Doctor;
import com.smartqueue.backend.repository.DoctorRepository;
import com.smartqueue.backend.service.DoctorQueueService;
import com.smartqueue.backend.service.ReceptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.smartqueue.backend.enums.TokenStatus;

@RestController
@RequestMapping("/api/reception")
@RequiredArgsConstructor
public class ReceptionController {

    private final ReceptionService receptionService;
    private final DoctorRepository doctorRepository;
    private final DoctorQueueService doctorQueueService;

    @PostMapping("/checkin")
    public TokenResponse checkIn(@RequestBody CheckInRequest request) {
        return receptionService.checkInWalkIn(request);
    }
    @PostMapping("/appointment")
    public TokenResponse bookAppointment(@RequestBody AppointmentRequest request) {
        return receptionService.bookAppointment(request);
    }
    @GetMapping("/overview")
    public ReceptionOverviewDTO getOverview() {
        return receptionService.getOverview();
    }
    @PostMapping("/token/{tokenId}/noshow")
    public void markNoShow(@PathVariable Long tokenId) {
        receptionService.markNoShow(tokenId);
    }
    @PutMapping("/token/{tokenId}/doctor/{doctorId}")
    public void reassignDoctor(
            @PathVariable Long tokenId,
            @PathVariable Long doctorId) {

        receptionService.reassignDoctor(tokenId, doctorId);
    }
    @PostMapping("/token/{tokenId}/reinstate")
    public TokenResponse reinstate(
            @PathVariable Long tokenId,
            @RequestParam String reason) {

        return receptionService.reinstateNoShow(tokenId, reason);
    }
    @GetMapping("/doctors")
    public List<Doctor> getDoctorsForReception() {
        return doctorRepository.findAll();
    }

    @GetMapping("/tokens/waiting")
    public List<EligibleTokenDTO> getWaitingTokens() {
        return receptionService.getEligibleTokens(List.of(TokenStatus.WAITING));
    }

    @GetMapping("/tokens/active")
    public List<EligibleTokenDTO> getActiveTokens() {
        return receptionService.getEligibleTokens(List.of(TokenStatus.WAITING, TokenStatus.CALLED, TokenStatus.IN_CONSULTATION));
    }

    @GetMapping("/tokens/reinstatable")
    public List<EligibleTokenDTO> getReinstatableTokens() {
        return receptionService.getEligibleTokens(List.of(TokenStatus.NO_SHOW, TokenStatus.EXPIRED));
    }

    @GetMapping("/display")
    public ReceptionOverviewDTO getDisplayBoard() {
        return doctorQueueService.buildReceptionOverview();
    }
}
