package com.smartqueue.backend.dto;

import com.smartqueue.backend.enums.PriorityFlag;
import com.smartqueue.backend.enums.ServiceType;
import com.smartqueue.backend.enums.VisitType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TokenRequest {

    private ServiceType serviceType;
    private PriorityFlag priorityFlag;
    private Integer officeId;
    private Long doctorId;
    private VisitType visitType;
    private String chiefComplaint;
    private Integer severityScore;
    private LocalDateTime appointmentScheduledTime;
}