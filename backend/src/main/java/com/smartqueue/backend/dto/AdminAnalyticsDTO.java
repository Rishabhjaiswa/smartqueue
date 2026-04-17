package com.smartqueue.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminAnalyticsDTO {
    private long totalPatientsToday;
    private double averageConsultMinutes;
    private double averageWaitMinutes;
    private List<DoctorPerformanceDTO> doctorPerformance;

    @Data
    @Builder
    public static class DoctorPerformanceDTO {
        private Long doctorId;
        private String doctorName;
        private int averageConsultMinutes;
        private int waitingCount;
        private boolean available;
    }
}
