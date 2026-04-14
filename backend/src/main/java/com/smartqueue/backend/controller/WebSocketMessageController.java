package com.smartqueue.backend.controller;

import com.smartqueue.backend.dto.DoctorQueueDTO;
import com.smartqueue.backend.dto.QueueStateDTO;
import com.smartqueue.backend.service.QueueService;
import com.smartqueue.backend.service.WebSocketBroadcastService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Controller
@RequiredArgsConstructor
public class WebSocketMessageController {

    private final QueueService queueService;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketBroadcastService broadcastService;

    @MessageMapping("/queue.subscribe/{doctorId}")
    public void onSubscribe(@DestinationVariable Long doctorId) {

        // Trigger immediate push of queue data
        broadcastService.broadcastDoctorQueue(
                doctorId,
                null // temporary (will improve later)
        );
    }

    public void broadcastDoctorQueue(Long doctorId) {

        QueueStateDTO state = queueService.getQueueState(doctorId);

        messagingTemplate.convertAndSend(
                "/topic/doctor/" + doctorId,
                state
        );
    }
}