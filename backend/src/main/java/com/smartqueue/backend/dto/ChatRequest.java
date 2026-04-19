package com.smartqueue.backend.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private Integer officeId;
    private String sessionId;
    private Long patientId;
}