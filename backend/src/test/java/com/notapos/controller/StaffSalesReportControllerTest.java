package com.notapos.controller;

import com.notapos.dto.StaffSalesView;
import com.notapos.service.StaffSalesReportService;
import com.notapos.testsupport.ControllerTest;
import com.notapos.testsupport.SignIn;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for StaffSalesReportController.
 *
 * Tests the REST endpoint behind the leaderboard.
 * Uses MockMvc to simulate HTTP requests without starting full server.
 *
 * @author CJ
 */

@ControllerTest(StaffSalesReportController.class)
class StaffSalesReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // The real sign-in check runs in controller tests. signIn makes a request come from a
    // signed-in person: .with(signIn.asManager()), .with(signIn.asServer()), ... (see SignIn)
    @Autowired
    private SignIn signIn;

    @MockitoBean
    private StaffSalesReportService staffSalesReportService;

    private final LocalDateTime from = LocalDateTime.of(2026, 11, 18, 0, 0, 0);
    private final LocalDateTime to = LocalDateTime.of(2026, 11, 18, 23, 59, 59);

    private StaffSalesView twoPeople() {
        return new StaffSalesView(List.of(
                new StaffSalesView.Row(7L, "Sarah", 1, 3, new BigDecimal("90.50"), new BigDecimal("30.17"), new BigDecimal("50.00"), 7),
                new StaffSalesView.Row(8L, "Dan", 2, 1, new BigDecimal("45.25"), new BigDecimal("45.25"), new BigDecimal("45.25"), 3)));
    }

    @Test
    void testGetReport_ShouldReturnEachPersonsSalesForThePeriod() throws Exception {
        // WHAT: Test GET /api/reports/staff-sales?from=...&to=...
        // WHY: The leaderboard reads every number it shows from this one response

        when(staffSalesReportService.getReport(from, to)).thenReturn(twoPeople());

        mockMvc.perform(get("/api/reports/staff-sales").with(signIn.asServer())
                .param("from", "2026-11-18T00:00:00")
                .param("to", "2026-11-18T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff.length()").value(2))
                .andExpect(jsonPath("$.staff[0].staffId").value(7))
                .andExpect(jsonPath("$.staff[0].name").value("Sarah"))
                .andExpect(jsonPath("$.staff[0].rank").value(1))
                .andExpect(jsonPath("$.staff[0].checksClosed").value(3))
                .andExpect(jsonPath("$.staff[0].sales").value(90.50))
                .andExpect(jsonPath("$.staff[0].checkAverage").value(30.17))
                .andExpect(jsonPath("$.staff[0].biggestCheck").value(50.00))
                .andExpect(jsonPath("$.staff[0].itemsSold").value(7))
                .andExpect(jsonPath("$.staff[1].name").value("Dan"))
                .andExpect(jsonPath("$.staff[1].rank").value(2));

        verify(staffSalesReportService).getReport(from, to);
    }

    @Test
    void testGetReport_ShouldCarryNoTip() throws Exception {
        // WHAT: Test that the word "tip" is nowhere in the answer
        // WHY: Everyone signed in reads this (D6); a tip is private to the person who
        //      earned it and to managers (D10). A field named avgTip, sent and then hidden
        //      on the screen, could be read in the browser's network tab.

        when(staffSalesReportService.getReport(from, to)).thenReturn(twoPeople());

        mockMvc.perform(get("/api/reports/staff-sales").with(signIn.asManager())
                .param("from", "2026-11-18T00:00:00")
                .param("to", "2026-11-18T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsStringIgnoringCase("tip"))));
    }

    @Test
    void testGetReport_AsTheKitchenOrAHost_ShouldBeAllowed() throws Exception {
        // WHAT: Test the report as the two roles that are allowed the least
        // WHY: The owner's decision D6: everyone signed in sees everyone's sales and rank

        when(staffSalesReportService.getReport(from, to)).thenReturn(twoPeople());

        mockMvc.perform(get("/api/reports/staff-sales").with(signIn.asKitchen())
                .param("from", "2026-11-18T00:00:00").param("to", "2026-11-18T23:59:59"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/staff-sales").with(signIn.asHost())
                .param("from", "2026-11-18T00:00:00").param("to", "2026-11-18T23:59:59"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetReport_WithNobodySignedIn_ShouldReturn401() throws Exception {
        // WHAT: Test the report with no sign-in
        // WHY: "Everyone" means everyone on the staff, not everyone on the network

        mockMvc.perform(get("/api/reports/staff-sales")
                .param("from", "2026-11-18T00:00:00")
                .param("to", "2026-11-18T23:59:59"))
                .andExpect(status().isUnauthorized());

        verify(staffSalesReportService, never()).getReport(any(), any());
    }

    @Test
    void testGetReport_WithADateItCannotRead_ShouldReturn400() throws Exception {
        // WHAT: Test the endpoint with a "from" that is not a date and time
        // WHY: A bad request should say so, not return a leaderboard for the wrong period

        mockMvc.perform(get("/api/reports/staff-sales").with(signIn.asServer())
                .param("from", "yesterday")
                .param("to", "2026-11-18T23:59:59"))
                .andExpect(status().isBadRequest());

        verify(staffSalesReportService, never()).getReport(any(), any());
    }

    @Test
    void testGetReport_WithoutAPeriod_ShouldReturn400() throws Exception {
        // WHAT: Test the endpoint with no from/to
        // WHY: "Everything, ever" must be asked for on purpose, not by leaving the dates off

        mockMvc.perform(get("/api/reports/staff-sales").with(signIn.asServer()))
                .andExpect(status().isBadRequest());

        verify(staffSalesReportService, never()).getReport(any(), any());
    }

    @Test
    void testGetReport_WhenNobodySoldAnything_ShouldReturnAnEmptyList() throws Exception {
        // WHAT: Test a period with no sales
        // WHY: The screen says "No sales yet for this period." from an empty list, so it
        //      must be a list and not null or a 404

        when(staffSalesReportService.getReport(from, to)).thenReturn(new StaffSalesView(List.of()));

        mockMvc.perform(get("/api/reports/staff-sales").with(signIn.asServer())
                .param("from", "2026-11-18T00:00:00")
                .param("to", "2026-11-18T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff").isArray())
                .andExpect(jsonPath("$.staff.length()").value(0));
    }
}
