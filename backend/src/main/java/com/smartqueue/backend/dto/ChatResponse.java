package com.smartqueue.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatResponse {
    private String botMessage;
    private boolean tokenGenerated;
    private TokenResponse tokenData;
    private boolean needsClarification;
    private String clarificationQuestion;
}