package com.smartqueue.backend.service;

import com.smartqueue.backend.dto.QueueStateDTO;
import com.smartqueue.backend.dto.TokenResponse;
import com.smartqueue.backend.dto.TokenWithWaitDTO;
import com.smartqueue.backend.dto.WaitTimeDTO;
import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.TokenStatus;
import com.smartqueue.backend.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WebSocketBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    // 🔥 NEW DEPENDENCIES
    private final QueueService queueService;
    private final WaitTimeService waitTimeService;
    private final TokenRepository tokenRepository;

    public void broadcastDoctorQueue(Long doctorId) {

        // 1️⃣ Get base queue state
        QueueStateDTO state = queueService.getQueueState(doctorId);

        // 2️⃣ Get waiting tokens
        List<Token> waiting = tokenRepository
                .findByDoctorIdAndStatusOrderByDynamicScoreAsc(
                        doctorId, TokenStatus.WAITING
                );

        // 3️⃣ Enrich each token with wait time
        List<TokenWithWaitDTO> enriched = waiting.stream()
                .map(token -> {
                    WaitTimeDTO wait =
                            waitTimeService.calculateWaitTime(token.getId());

                    return TokenWithWaitDTO.from(token, wait);
                })
                .collect(Collectors.toList());

        // 4️⃣ Inject into state
        state.setTokensWithWait(enriched);

        // 5️⃣ Set doctor status
        if (state.getDoctorDelayMinutes() > 0) {
            state.setDoctorStatus("RUNNING_LATE");
            state.setWaitMessage(
                    "Doctor running " + state.getDoctorDelayMinutes() + " min late"
            );
        } else {
            state.setDoctorStatus("ON_TIME");
            state.setWaitMessage("On schedule");
        }

        // 6️⃣ Broadcast
        messagingTemplate.convertAndSend(
                "/topic/doctor/" + doctorId + "/queue",
                state
        );

        messagingTemplate.convertAndSend(
                "/topic/reception/overview",
                state
        );
    }

    // 🔹 KEEP — legacy support (optional)
    public void broadcastQueueState(Integer officeId, QueueStateDTO state) {
        messagingTemplate.convertAndSend(
                "/topic/queue/" + officeId, state
        );
        messagingTemplate.convertAndSend(
                "/topic/staff/" + officeId, state
        );
    }

    // 🔹 KEEP — token confirmation
    public void sendPrivateTokenConfirmation(
            String sessionId, TokenResponse response) {

        messagingTemplate.convertAndSendToUser(
                sessionId,
                "/queue/token-confirmation",
                response
        );
    }

    // 🔹 KEEP — current token update
    public void broadcastCurrentToken(Integer officeId, String tokenNumber) {

        messagingTemplate.convertAndSend(
                "/topic/queue/" + officeId + "/current",
                tokenNumber
        );
    }
}