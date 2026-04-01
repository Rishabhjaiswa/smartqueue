package com.smartqueue.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenResponse {
    private Long id;
    private String tokenNumber;
    private String serviceType;
    private String status;
    private int positionInQueue;
    private int estimatedWaitMinutes;
    private String message;
}