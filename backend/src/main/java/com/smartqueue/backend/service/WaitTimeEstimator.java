package com.smartqueue.backend.service;

import com.smartqueue.backend.entity.Doctor;
import com.smartqueue.backend.repository.DoctorRepository;
import com.smartqueue.backend.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WaitTimeEstimator {

    private final DoctorRepository doctorRepository;
    private final TokenRepository tokenRepository;

    // ✅ Main method
    public int estimateForPosition(int position, Long doctorId) {

        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        int avgMins = computeRollingAvg(doctorId, doctor.getAvgConsultMins());

        return position * avgMins;
    }

    // ✅ Rolling average of last consultations
    private int computeRollingAvg(Long doctorId, int fallbackMins) {

        List<Integer> recent = tokenRepository
                .findRecentConsultDurations(doctorId, 10);

        if (recent == null || recent.isEmpty()) {
            return fallbackMins;
        }

        return (int) recent.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(fallbackMins);
    }
}