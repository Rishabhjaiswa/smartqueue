package com.smartqueue.backend.dto;

import com.smartqueue.backend.enums.PriorityFlag;
import com.smartqueue.backend.enums.ServiceType;
import com.smartqueue.backend.enums.VisitType;
import lombok.Data;

@Data
public class TokenRequest {

    private Long doctorId;
    private Long patientId;

    private ServiceType serviceType;
    private VisitType visitType;

    private PriorityFlag priorityFlag;

    private Integer severityScore;
}