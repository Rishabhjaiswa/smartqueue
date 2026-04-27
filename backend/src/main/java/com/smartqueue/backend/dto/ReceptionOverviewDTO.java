package com.smartqueue.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReceptionOverviewDTO {

    private int officeId;
    private int totalDoctorsActive;
    private int totalPatientsWaiting;
    private List<DoctorSummary> doctors;

    @Data
    @Builder
    public static class DoctorSummary {
        private Long doctorId;
        private String doctorName;
        private String currentToken;
        private int waitingCount;
        private int avgConsultTime;
        private boolean active;
        private List<String> nextTokens;
    }
}
