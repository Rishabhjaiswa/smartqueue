package com.smartqueue.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EligibleTokenDTO {
    private Long id;
    private String tokenNumber;
    private String patientName;
    private String status;
}
