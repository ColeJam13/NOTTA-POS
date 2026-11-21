package com.notapos.controller;

import com.notapos.dto.FinancialsReportView;
import com.notapos.service.FinancialsReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.notapos.testsupport.ControllerTest;
import com.notapos.testsupport.SignIn;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for FinancialsReportController.
 *
 * Tests the one endpoint behind the Financials screen.
 * Uses MockMvc to simulate HTTP requests without starting full server.
 *
 * @author CJ
 */

@ControllerTest(FinancialsReportController.class)
class FinancialsReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // The real sign-in check runs in controller tests. signIn makes a request come from a
    // signed-in person: .with(signIn.asManager()), .with(signIn.asServer()), ... (see SignIn)
    @Autowired
    private SignIn signIn;

    @MockitoBean
    private FinancialsReportService financialsReportService;

    @Test
    void testGetReport_ShouldReturnTheReportForThePeriod() throws Exception {
        // WHAT: Test GET /api/reports/financials?from=...&to=...
        // WHY: The Financials screen reads every number it shows from this one response

        LocalDateTime from = LocalDateTime.of(2026, 11, 18, 0, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 11, 18, 23, 59, 59);
        when(financialsReportService.getReport(from, to)).thenReturn(new FinancialsReportView(
                1, new BigDecimal("33.00"), new BigDecimal("0.99"), new BigDecimal("6.80"), new BigDecimal("40.79"),
                new BigDecimal("33.00"), new BigDecimal("20.6"),
                List.of(new FinancialsReportView.PaymentMethodTotal("credit_card", new BigDecimal("33.99"), new BigDecimal("100.0"))),
                List.of(new FinancialsReportView.Transaction(10L, "W1", false, "CJ", to, new BigDecimal("33.99"), new BigDecimal("6.80"), "credit_card"))));

        mockMvc.perform(get("/api/reports/financials").with(signIn.asManager())
                .param("from", "2026-11-18T00:00:00")
                .param("to", "2026-11-18T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderCount").value(1))
                .andExpect(jsonPath("$.revenue").value(33.00))
                .andExpect(jsonPath("$.tax").value(0.99))
                .andExpect(jsonPath("$.totalCollected").value(40.79))
                .andExpect(jsonPath("$.avgTipPercent").value(20.6))
                .andExpect(jsonPath("$.paymentBreakdown[0].paymentMethod").value("credit_card"))
                .andExpect(jsonPath("$.paymentBreakdown[0].percent").value(100.0))
                .andExpect(jsonPath("$.recentTransactions[0].tableNumber").value("W1"))
                .andExpect(jsonPath("$.recentTransactions[0].total").value(33.99));

        verify(financialsReportService).getReport(from, to);
    }

    @Test
    void testGetReport_WithADateItCannotRead_ShouldReturn400() throws Exception {
        // WHAT: Test the endpoint with a "from" that is not a date and time
        // WHY: A bad request should say so, not return a report for the wrong period

        mockMvc.perform(get("/api/reports/financials").with(signIn.asManager())
                .param("from", "yesterday")
                .param("to", "2026-11-18T23:59:59"))
                .andExpect(status().isBadRequest());

        verify(financialsReportService, never()).getReport(any(), any());
    }

    @Test
    void testGetReport_WithoutAPeriod_ShouldReturn400() throws Exception {
        // WHAT: Test the endpoint with no from/to
        // WHY: "Everything, ever" must be asked for on purpose, not by leaving the dates off

        mockMvc.perform(get("/api/reports/financials").with(signIn.asManager()))
                .andExpect(status().isBadRequest());

        verify(financialsReportService, never()).getReport(any(), any());
    }

    @Test
    void testGetReport_WhenSignedInAsAServer_ShouldReturn403() throws Exception {
        // WHAT: Test the report signed in as a server
        // WHY: Sales figures are for managers only

        mockMvc.perform(get("/api/reports/financials").with(signIn.asServer())
                .param("from", "2026-11-18T00:00:00")
                .param("to", "2026-11-18T23:59:59"))
                .andExpect(status().isForbidden());

        verify(financialsReportService, never()).getReport(any(), any());
    }

    @Test
    void testGetReport_WhenNobodyIsSignedIn_ShouldReturn401() throws Exception {
        // WHAT: Test the report with no sign-in
        // WHY: The screen answers a 401 by showing the PIN pad

        mockMvc.perform(get("/api/reports/financials")
                .param("from", "2026-11-18T00:00:00")
                .param("to", "2026-11-18T23:59:59"))
                .andExpect(status().isUnauthorized());

        verify(financialsReportService, never()).getReport(any(), any());
    }
}
