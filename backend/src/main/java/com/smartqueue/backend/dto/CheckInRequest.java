package com.smartqueue.backend.dto;

import com.smartqueue.backend.enums.ServiceType;
import lombok.Data;

@Data
public class CheckInRequest {

    private String patientName;
    private Integer age;

    private ServiceType serviceType;
    private Integer severityScore;
    private Integer officeId;
}