package com.smartqueue.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HistoryTokenDTO {
    private String tokenNumber;
    private String patientName;
    private String doctorName;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime consultationEnd;
}
