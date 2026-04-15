package com.smartqueue.backend.controller;

import com.smartqueue.backend.dto.AppointmentRequest;
import com.smartqueue.backend.dto.CheckInRequest;
import com.smartqueue.backend.dto.ReceptionOverviewDTO;
import com.smartqueue.backend.dto.TokenResponse;
import com.smartqueue.backend.service.ReceptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reception")
@RequiredArgsConstructor
public class ReceptionController {

    private final ReceptionService receptionService;

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
}