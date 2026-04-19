package com.smartqueue.backend.controller;

import com.smartqueue.backend.dto.CreateStaffRequest;
import com.smartqueue.backend.dto.StaffUserDTO;
import com.smartqueue.backend.dto.AdminAnalyticsDTO;
import com.smartqueue.backend.dto.AuditLogDTO;
import com.smartqueue.backend.dto.HistoryTokenDTO;
import com.smartqueue.backend.dto.ResetPasswordRequest;
import com.smartqueue.backend.service.AuditLogService;
import com.smartqueue.backend.service.DoctorQueueService;
import com.smartqueue.backend.service.StaffUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final StaffUserService staffUserService;
    private final DoctorQueueService doctorQueueService;
    private final AuditLogService auditLogService;

    @PostMapping("/create-staff")
    public ResponseEntity<StaffUserDTO> createStaff(@Valid @RequestBody CreateStaffRequest request) {
        return ResponseEntity.ok(staffUserService.createStaff(request));
    }

    @GetMapping("/staff")
    public ResponseEntity<List<StaffUserDTO>> getStaff() {
        return ResponseEntity.ok(staffUserService.listStaff());
    }

    @GetMapping("/analytics")
    public ResponseEntity<AdminAnalyticsDTO> getAnalytics() {
        return ResponseEntity.ok(doctorQueueService.buildAdminAnalytics());
    }

    @GetMapping("/history")
    public ResponseEntity<List<HistoryTokenDTO>> getHistory() {
        return ResponseEntity.ok(doctorQueueService.getRecentHistory());
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLogDTO>> getAuditLogs() {
        return ResponseEntity.ok(auditLogService.getRecentLogs());
    }

    @PostMapping("/staff/{staffUserId}/reset-password")
    public ResponseEntity<Void> resetStaffPassword(
            @PathVariable Long staffUserId,
            @Valid @RequestBody ResetPasswordRequest request) {
        staffUserService.resetStaffPassword(staffUserId, request.getPassword());
        return ResponseEntity.ok().build();
    }
}
