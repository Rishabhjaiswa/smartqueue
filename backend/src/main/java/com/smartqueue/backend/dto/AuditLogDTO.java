package com.smartqueue.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogDTO {
    private String action;
    private String actorUsername;
    private String details;
    private LocalDateTime createdAt;
}
