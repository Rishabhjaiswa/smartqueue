package com.smartqueue.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WaitTimeDTO {

    private Long tokenId;
    private int positionInQueue;
    private int estimatedWaitMinutes;
    private int doctorDelayMinutes;
    private String displayMessage;
    private LocalDateTime lastUpdated;
}