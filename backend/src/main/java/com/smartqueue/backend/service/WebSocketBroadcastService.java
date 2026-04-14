package com.smartqueue.backend.service;

import com.smartqueue.backend.dto.DoctorQueueDTO;
import com.smartqueue.backend.dto.ReceptionOverviewDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WebSocketBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    // ✅ Doctor-specific queue
    public void broadcastDoctorQueue(Long doctorId, DoctorQueueDTO dto) {

        messagingTemplate.convertAndSend(
                "/topic/doctor/" + doctorId,
                dto
        );
    }

    // ✅ Current token display
    public void broadcastCurrentToken(Long doctorId, String tokenNumber) {
        messagingTemplate.convertAndSend(
                "/topic/doctor/" + doctorId + "/current",
                tokenNumber
        );
    }

    // ✅ Reception dashboard
    public void broadcastReceptionOverview(ReceptionOverviewDTO dto) {
        messagingTemplate.convertAndSend(
                "/topic/reception/overview",
                dto
        );
    }

    // ✅ Patient notification
    public void notifyPatient(Long patientId, String message) {
        messagingTemplate.convertAndSendToUser(
                patientId.toString(),
                "/notification",
                Map.of(
                        "message", message,
                        "timestamp", LocalDateTime.now().toString()
                )
        );
    }

    // ✅ Admin alerts
    public void broadcastAdminAlert(String alert) {
        messagingTemplate.convertAndSend(
                "/topic/admin/system",
                Map.of(
                        "alert", alert,
                        "time", LocalDateTime.now().toString()
                )
        );
    }
}