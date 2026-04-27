package com.smartqueue.backend.controller;

import com.smartqueue.backend.service.MedicalReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Phase 4 — Medical Report REST endpoints.
 *
 * Secured: only STAFF / ADMIN roles may download reports.
 * GET /api/reports/visit/{tokenId}  → streams PDF bytes inline/attachment.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Slf4j
public class MedicalReportController {

    private final MedicalReportService reportService;

    /**
     * Download a PDF visit summary for the given token.
     *
     * Example: GET /api/reports/visit/42?download=true
     *   download=false (default) → inline preview in browser
     *   download=true            → force file download
     */
    @GetMapping("/visit/{tokenId}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN', 'DOCTOR')")
    public ResponseEntity<byte[]> downloadVisitReport(
            @PathVariable Long tokenId,
            @RequestParam(defaultValue = "false") boolean download) {

        log.info("PDF report requested: tokenId={}", tokenId);
        byte[] pdf = reportService.generateVisitReport(tokenId);

        String filename = "SmartQueue_Visit_" + tokenId + ".pdf";

        ContentDisposition cd = download
                ? ContentDisposition.attachment().filename(filename).build()
                : ContentDisposition.inline().filename(filename).build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(cd);
        headers.setContentLength(pdf.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdf);
    }
}
