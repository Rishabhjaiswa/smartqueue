package com.smartqueue.backend.controller;

import com.smartqueue.backend.dto.AppointmentRequest;
import com.smartqueue.backend.dto.CheckInRequest;
import com.smartqueue.backend.dto.EligibleTokenDTO;
import com.smartqueue.backend.dto.ReceptionOverviewDTO;
import com.smartqueue.backend.dto.TokenResponse;
import com.smartqueue.backend.entity.Doctor;
import com.smartqueue.backend.entity.StaffUser;
import com.smartqueue.backend.repository.DoctorRepository;
import com.smartqueue.backend.repository.StaffUserRepository;
import com.smartqueue.backend.service.DoctorQueueService;
import com.smartqueue.backend.service.ReceptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.smartqueue.backend.enums.TokenStatus;

@RestController
@RequestMapping("/api/reception")
@RequiredArgsConstructor
public class ReceptionController {

    private final ReceptionService receptionService;
    private final DoctorRepository doctorRepository;
    private final StaffUserRepository staffUserRepository;
    private final DoctorQueueService doctorQueueService;

    /** Resolve the caller's officeId from their JWT principal. Falls back to 1 if not found. */
    private int callerOfficeId(Authentication auth) {
        if (auth == null) return 1;
        return staffUserRepository.findByUsername(auth.getName())
                .map(u -> u.getOfficeId() != null ? u.getOfficeId() : 1)
                .orElse(1);
    }

    @PostMapping("/checkin")
    public TokenResponse checkIn(@RequestBody CheckInRequest request) {
        return receptionService.checkInWalkIn(request);
    }
    @PostMapping("/appointment")
    public TokenResponse bookAppointment(@RequestBody AppointmentRequest request) {
        return receptionService.bookAppointment(request);
    }
    @GetMapping("/overview")
    public ReceptionOverviewDTO getOverview(Authentication auth) {
        return doctorQueueService.buildReceptionOverview(callerOfficeId(auth));
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
    public List<Doctor> getDoctorsForReception(Authentication auth) {
        int officeId = callerOfficeId(auth);
        List<Doctor> docs = doctorRepository.findByOfficeId(officeId);
        // If nobody is assigned to this office yet, return empty list
        // (do NOT fall back to all doctors — that caused the wrong-office bug)
        return docs;
    }

    @GetMapping("/tokens/waiting")
    public List<EligibleTokenDTO> getWaitingTokens(Authentication auth) {
        return receptionService.getEligibleTokensByOffice(
                List.of(TokenStatus.WAITING), callerOfficeId(auth));
    }

    @GetMapping("/tokens/active")
    public List<EligibleTokenDTO> getActiveTokens(Authentication auth) {
        return receptionService.getEligibleTokensByOffice(
                List.of(TokenStatus.WAITING, TokenStatus.CALLED, TokenStatus.IN_CONSULTATION),
                callerOfficeId(auth));
    }

    @GetMapping("/tokens/reinstatable")
    public List<EligibleTokenDTO> getReinstatableTokens(Authentication auth) {
        return receptionService.getEligibleTokensByOffice(
                List.of(TokenStatus.NO_SHOW, TokenStatus.EXPIRED), callerOfficeId(auth));
    }

    @GetMapping("/display")
    public ReceptionOverviewDTO getDisplayBoard() {
        return doctorQueueService.buildReceptionOverview(1);
    }

    /** Public endpoint — shows all offices for the lobby display board. */
    @GetMapping("/display/all")
    public List<ReceptionOverviewDTO> getAllOfficesDisplay() {
        List<Integer> officeIds = doctorRepository.findDistinctOfficeIds();
        if (officeIds.isEmpty()) {
            officeIds = List.of(1);
        }
        return officeIds.stream()
                .map(doctorQueueService::buildReceptionOverview)
                .toList();
    }
}
