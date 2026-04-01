package com.smartqueue.backend.controller;

import com.smartqueue.backend.dto.QueueStateDTO;
import com.smartqueue.backend.dto.TokenRequest;
import com.smartqueue.backend.dto.TokenResponse;
import com.smartqueue.backend.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> generateToken(
            @RequestBody TokenRequest request) {
        return ResponseEntity.ok(queueService.generateToken(request));
    }

    @GetMapping("/queue/{officeId}")
    public ResponseEntity<QueueStateDTO> getQueueState(
            @PathVariable Integer officeId) {
        return ResponseEntity.ok(queueService.getQueueState(officeId));
    }

    @PostMapping("/staff/next")
    public ResponseEntity<TokenResponse> callNext(
            @RequestParam Integer officeId) {
        return ResponseEntity.ok(queueService.callNextToken(officeId));
    }

    @PostMapping("/staff/complete/{tokenId}")
    public ResponseEntity<Void> complete(@PathVariable Long tokenId) {
        queueService.completeToken(tokenId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/staff/noshow/{tokenId}")
    public ResponseEntity<Void> noShow(
            @PathVariable Long tokenId,
            @RequestParam Integer officeId) {
        queueService.markNoShow(tokenId, officeId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/staff/override")
    public ResponseEntity<Void> override(
            @RequestParam String tokenNumber,
            @RequestParam Integer officeId) {
        queueService.staffOverride(tokenNumber, officeId);
        return ResponseEntity.ok().build();
    }
}