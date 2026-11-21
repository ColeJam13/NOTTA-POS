package com.notapos.controller;

import com.notapos.dto.FinancialsReportView;
import org.springframework.security.access.prepost.PreAuthorize;
import com.notapos.service.FinancialsReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import com.notapos.security.Permissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * REST API Controller for the Financials screen.
 *
 * One endpoint that answers the screen with every number it shows for a
 * time period.
 *
 * @author CJ
 */

@RestController
@RequestMapping("/api/reports/financials")
public class FinancialsReportController {

    private final FinancialsReportService financialsReportService;

    @Autowired
    public FinancialsReportController(FinancialsReportService financialsReportService) {
        this.financialsReportService = financialsReportService;
    }

    // from and to look like 2026-11-18T00:00:00 - a date and time with no time zone,
    // read as the restaurant's own clock (the same clock orders are stamped with).
    // A value Spring cannot read as a date and time becomes a 400.
    @PreAuthorize(Permissions.MANAGER_ONLY)                                 // Sales figures are for managers only
    @GetMapping                                                         // Get the report for checks closed between two moments
    public ResponseEntity<FinancialsReportView> getReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(financialsReportService.getReport(from, to));
    }
}
