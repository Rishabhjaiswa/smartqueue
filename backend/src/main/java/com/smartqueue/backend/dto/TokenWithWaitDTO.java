package com.smartqueue.backend.dto;

import com.smartqueue.backend.entity.Token;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenWithWaitDTO {

    private Long tokenId;
    private String patientName;
    private int position;
    private String status;

    private int estimatedWaitMinutes;
    private int doctorDelayMinutes;
    private String waitMessage;

    public static TokenWithWaitDTO from(Token token, WaitTimeDTO wait) {
        return TokenWithWaitDTO.builder()
                .tokenId(token.getId())
                .patientName(token.getPatientName())
                .position(wait.getPositionInQueue())
                .status(token.getStatus().name())
                .estimatedWaitMinutes(wait.getEstimatedWaitMinutes())
                .doctorDelayMinutes(wait.getDoctorDelayMinutes())
                .waitMessage(wait.getDisplayMessage())
                .build();
    }
}