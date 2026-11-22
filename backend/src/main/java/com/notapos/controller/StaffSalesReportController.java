package com.notapos.controller;

import com.notapos.dto.StaffSalesView;
import com.notapos.security.Permissions;
import com.notapos.service.StaffSalesReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * REST API Controller for per-person sales: the leaderboard on the Challenges screen.
 *
 * One endpoint that answers what each person sold in a time period.
 *
 * @author CJ
 */

@RestController
@RequestMapping("/api/reports/staff-sales")
public class StaffSalesReportController {

    private final StaffSalesReportService staffSalesReportService;

    @Autowired
    public StaffSalesReportController(StaffSalesReportService staffSalesReportService) {
        this.staffSalesReportService = staffSalesReportService;
    }

    // from and to are read exactly as the Financials report reads them: 2026-11-18T00:00:00,
    // a date and time with no time zone, on the restaurant's own clock (the clock orders are
    // stamped with). A value Spring cannot read as a date and time becomes a 400.
    @PreAuthorize(Permissions.SEE_STAFF_SALES)                          // Every role: everyone sees everyone's sales and rank (D6), and no tips
    @GetMapping                                                         // Get what each person sold on checks closed between two moments
    public ResponseEntity<StaffSalesView> getReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(staffSalesReportService.getReport(from, to));
    }
}
