package com.smartqueue.backend.dto;

import com.smartqueue.backend.enums.ServiceType;
import lombok.Data;

@Data
public class CheckInRequest {

    private String patientName;
    private Integer age;

    /** Optional: mobile number for family identification (Phase 2). */
    private String phone;

    private ServiceType serviceType;
    private Integer severityScore;
    private Integer officeId;

    /** Client-generated key (UUID) to prevent duplicate check-ins on double-click. Optional. */
    private String idempotencyKey;

    /** Flag: patient needs mobility/language/cognitive assistance — boosts priority to near-front. */
    private boolean requiresAssistance;
}