package com.smartqueue.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DoctorQueueDTO {

    private Long doctorId;
    private String currentToken;
    private int waitingCount;

    private List<String> nextTokens; // simple list for now
}
