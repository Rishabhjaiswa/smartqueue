package com.smartqueue.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateStaffRequest {
    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String role;

    private Long doctorId;
    private String doctorName;
    private String specialization;
    private String roomNumber;
    private Integer avgConsultMins;
    private Integer officeId;
}
