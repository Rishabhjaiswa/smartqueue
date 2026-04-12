package com.smartqueue.backend.dto;

import lombok.Data;

@Data
public class CheckInRequest {
    private Long patientId;
    private Long doctorId;
    private String visitType; // WALK_IN / EMERGENCY etc.
}
