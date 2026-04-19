package com.smartqueue.backend.service;

import com.smartqueue.backend.dto.AuditLogDTO;
import com.smartqueue.backend.entity.AuditLog;
import com.smartqueue.backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public void log(String action, String actorUsername, String details) {
        auditLogRepository.save(
                AuditLog.builder()
                        .action(action)
                        .actorUsername(actorUsername == null || actorUsername.isBlank() ? "system" : actorUsername)
                        .details(details)
                        .createdAt(LocalDateTime.now())
                        .build()
        );
    }

    public List<AuditLogDTO> getRecentLogs() {
        return auditLogRepository.findTop30ByOrderByCreatedAtDesc()
                .stream()
                .map(item -> AuditLogDTO.builder()
                        .action(item.getAction())
                        .actorUsername(item.getActorUsername())
                        .details(item.getDetails())
                        .createdAt(item.getCreatedAt())
                        .build())
                .toList();
    }
}
