package com.smartqueue.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data @Builder
public class DoctorQueueDTO {
    private Long doctorId;
    private String doctorName;
    private String roomNumber;
    private String currentToken;
    private String currentPatientName;
    private int waitingCount;
    private int estimatedWaitMinutes;
    private List<QueueEntryDTO> nextTokens;

    @Data @Builder
    public static class QueueEntryDTO {
        private String tokenNumber;
        private String patientName;
        private String visitType;
        private int estimatedWaitMinutes;
    }
}