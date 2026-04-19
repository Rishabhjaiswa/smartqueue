package com.smartqueue.backend.dto;

import com.smartqueue.backend.enums.ServiceType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppointmentRequest {

    private String patientName;
    private Integer age;

    private ServiceType serviceType;
    private Integer severityScore;
    private Integer officeId;

    private LocalDateTime appointmentTime;
}