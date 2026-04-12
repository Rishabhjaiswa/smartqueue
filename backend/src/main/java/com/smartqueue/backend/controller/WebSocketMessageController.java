package com.smartqueue.backend.controller;

import com.smartqueue.backend.dto.QueueStateDTO;
import com.smartqueue.backend.service.QueueService;
import com.smartqueue.backend.service.WebSocketBroadcastService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class WebSocketMessageController {

    private final QueueService queueService;
    private final WebSocketBroadcastService broadcastService;

    @MessageMapping("/queue.subscribe/{officeId}")
    public void onSubscribe(@DestinationVariable Integer officeId) {
        QueueStateDTO state = queueService.getQueueState((long)officeId);
        broadcastService.broadcastQueueState(officeId, state);
    }
}