package com.smartqueue.backend.controller;

import com.smartqueue.backend.dto.DoctorQueueDTO;
import com.smartqueue.backend.dto.TokenResponse;
import com.smartqueue.backend.service.DoctorQueueService;
import com.smartqueue.backend.service.StaffUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/doctor")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorQueueService doctorQueueService;
    private final StaffUserService staffUserService;

    // ✅ CALL NEXT PATIENT
    @PostMapping("/call-next")
    public ResponseEntity<TokenResponse> callNext(
            @AuthenticationPrincipal UserDetails user) {

        Long doctorId = staffUserService.getDoctorId(user.getUsername());

        return ResponseEntity.ok(
                doctorQueueService.callNext(doctorId));
    }

    // ✅ START CONSULTATION
    @PostMapping("/token/{tokenId}/in-consultation")
    public ResponseEntity<Void> startConsultation(
            @PathVariable Long tokenId,
            @AuthenticationPrincipal UserDetails user) {

        Long doctorId = staffUserService.getDoctorId(user.getUsername());

        doctorQueueService.startConsultation(tokenId, doctorId);

        return ResponseEntity.ok().build();
    }

    // ✅ COMPLETE CONSULTATION
    @PostMapping("/token/{tokenId}/complete")
    public ResponseEntity<Void> complete(
            @PathVariable Long tokenId,
            @AuthenticationPrincipal UserDetails user) {

        Long doctorId = staffUserService.getDoctorId(user.getUsername());

        doctorQueueService.completeConsultation(tokenId, doctorId);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/token/{tokenId}/extend")
    public ResponseEntity<Void> extendConsultation(
            @PathVariable Long tokenId,
            @AuthenticationPrincipal UserDetails user) {

        Long doctorId = staffUserService.getDoctorId(user.getUsername());
        doctorQueueService.extendConsultation(tokenId, doctorId);

        return ResponseEntity.ok().build();
    }

    @PutMapping("/availability")
    public ResponseEntity<Void> toggleAvailability(
            @RequestParam boolean available,
            @AuthenticationPrincipal UserDetails user) {

        Long doctorId = staffUserService.getDoctorId(user.getUsername());
        doctorQueueService.setAvailability(doctorId, available);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/queue")
    public ResponseEntity<DoctorQueueDTO> getQueue(
            @AuthenticationPrincipal UserDetails user) {

        Long doctorId = staffUserService.getDoctorId(user.getUsername());

        return ResponseEntity.ok(
                doctorQueueService.buildDoctorQueueDTO(doctorId)
        );
    }
}
