package com.smartqueue.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class QueueStateDTO {

    // 🔹 Existing fields (keep them)
    private Integer officeId;
    private String currentToken;
    private int waitingCount;
    private int avgWaitMinutes;
    private List<String> nextTokens;

    // 🔥 NEW — Phase 4 additions

    // Overall doctor-level wait info
    private int estimatedWaitMinutes;
    private int doctorDelayMinutes;

    // Doctor status → UI + logic
    // Possible values: ON_TIME, RUNNING_LATE, ON_BREAK
    private String doctorStatus;

    // Message like:
    // "Doctor running 10 min late"
    private String waitMessage;

    // 🔥 MOST IMPORTANT — enriched tokens
    private List<TokenWithWaitDTO> tokensWithWait;
}