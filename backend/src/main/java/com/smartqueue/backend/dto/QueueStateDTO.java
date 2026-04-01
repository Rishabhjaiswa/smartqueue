package com.smartqueue.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class QueueStateDTO {
    private Integer officeId;
    private String currentToken;
    private int waitingCount;
    private int avgWaitMinutes;
    private List<String> nextTokens;
}
