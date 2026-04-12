package com.smartqueue.backend.controller;

import com.smartqueue.backend.dto.*;
import com.smartqueue.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reception")
@RequiredArgsConstructor
public class ReceptionController {

    private final ReceptionService receptionService;

    @PostMapping("/checkin")
    public ResponseEntity<TokenResponse> checkIn(
            @RequestBody CheckInRequest request) {
        return ResponseEntity.ok(
                receptionService.checkInWalkIn(request));
    }

    @PostMapping("/appointment")
    public ResponseEntity<TokenResponse> bookAppointment(
            @RequestBody AppointmentRequest request) {
        return ResponseEntity.ok(
                receptionService.bookAppointment(request));
    }

    @PutMapping("/token/{tokenId}/doctor/{doctorId}")
    public ResponseEntity<Void> reassign(
            @PathVariable Long tokenId,
            @PathVariable Long doctorId) {
        receptionService.reassignDoctor(tokenId, doctorId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/token/{tokenId}/noshow")
    public ResponseEntity<Void> markNoShow(
            @PathVariable Long tokenId) {
        receptionService.markNoShow(tokenId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/token/{tokenId}/reinstate")
    public ResponseEntity<TokenResponse> reinstate(
            @PathVariable Long tokenId,
            @RequestParam String reason) {
        return ResponseEntity.ok(
                receptionService.reinstateNoShow(tokenId, reason));
    }

    @GetMapping("/overview")
    public ResponseEntity<ReceptionOverviewDTO> overview() {
        return ResponseEntity.ok(receptionService.getOverview());
    }
}