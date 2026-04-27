package com.smartqueue.backend.dto;

import lombok.Data;

@Data
public class IntentDTO {
    private String serviceType;
    private String priorityFlag;
    private String suggestedSpecialization;   // e.g. CARDIOLOGY, PEDIATRICS, GENERAL
    private String language;
    private double confidence;
    private boolean clarificationNeeded;
    private String clarificationQuestion;
    private String replyMessage;
}