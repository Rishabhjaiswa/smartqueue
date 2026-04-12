package com.smartqueue.backend.service;

import com.smartqueue.backend.dto.*;
import org.springframework.stereotype.Service;

@Service
public class ReceptionService {

    public TokenResponse checkInWalkIn(CheckInRequest req) {
        return TokenResponse.builder()
                .message("Walk-in registered")
                .build();
    }

    public TokenResponse bookAppointment(AppointmentRequest req) {
        return TokenResponse.builder()
                .message("Appointment booked")
                .build();
    }

    public void reassignDoctor(Long tokenId, Long doctorId) {}

    public void markNoShow(Long tokenId) {}

    public TokenResponse reinstateNoShow(Long tokenId, String reason) {
        return TokenResponse.builder()
                .message("Reinstated")
                .build();
    }

    public ReceptionOverviewDTO getOverview() {
        return ReceptionOverviewDTO.builder()
                .totalWaiting(0)
                .totalDoctorsActive(0)
                .build();
    }
}
