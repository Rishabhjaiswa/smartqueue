package com.smartqueue.backend.service;

import com.smartqueue.backend.dto.QueueStateDTO;
import com.smartqueue.backend.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WebSocketBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastQueueState(Integer officeId, QueueStateDTO state) {
        messagingTemplate.convertAndSend(
                "/topic/queue/" + officeId, state
        );
        messagingTemplate.convertAndSend(
                "/topic/staff/" + officeId, state
        );
    }

    public void sendPrivateTokenConfirmation(
            String sessionId, TokenResponse response) {
        messagingTemplate.convertAndSendToUser(
                sessionId,
                "/queue/token-confirmation",
                response
        );
    }

    public void broadcastCurrentToken(Integer officeId, String tokenNumber) {
        messagingTemplate.convertAndSend(
                "/topic/queue/" + officeId + "/current",
                tokenNumber
        );
    }
}