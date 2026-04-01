package com.smartqueue.backend.dto;

import com.smartqueue.backend.enums.PriorityFlag;
import com.smartqueue.backend.enums.ServiceType;
import lombok.Data;

@Data
public class TokenRequest {
    private ServiceType serviceType;
    private PriorityFlag priorityFlag;
    private Integer officeId;
}