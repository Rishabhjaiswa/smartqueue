package com.smartqueue.backend.dto;

import lombok.Data;

@Data
public class WalkInRequest {
    private String name;
    private String phone;
    private int age;
    private String department;
    private Integer severityScore;
    private Long doctorId; // optional
}