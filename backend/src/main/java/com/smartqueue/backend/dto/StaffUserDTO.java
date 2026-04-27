package com.smartqueue.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StaffUserDTO {
    private Long id;
    private String username;
    private String role;
    private Integer officeId;
    private Long doctorId;
    private String doctorName;
}
