package com.notapos.controller;

import com.notapos.config.ServerTimeFilter;
import com.notapos.service.OrderCheckService;
import com.notapos.testsupport.ControllerTest;
import com.notapos.testsupport.SignIn;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for SettingsController: the settings a screen needs from the backend.
 *
 * Uses @ControllerTest (see testsupport) - only the web layer is started and
 * the service is faked with @MockitoBean.
 *
 * @author CJ
 */
@ControllerTest(SettingsController.class)
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignIn signIn;              // Says who a request comes from: .with(signIn.asServer())

    @MockitoBean
    private OrderCheckService orderCheckService;

    @Test
    void testGetSettings_ShouldReturnTheTaxRateChecksAreChargedAt() throws Exception {
        // WHAT: Test GET /api/settings
        // WHY: The order screen works out its running total with this number. It must be
        //      the one OrderCheckService charges with, whatever that is set to.

        // Given - the restaurant's rate is 8.25%
        when(orderCheckService.getTaxRate()).thenReturn(new BigDecimal("0.0825"));

        // When & Then
        mockMvc.perform(get("/api/settings").with(signIn.asServer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxRate").value(0.0825));
    }

    @Test
    void testGetSettings_ShouldBeOpenToEveryRole() throws Exception {
        // WHAT: Test the endpoint as the roles that never open Financials or Staff
        // WHY: The tax rate is printed on every check; it is not a manager's secret. Anyone
        //      who can ring an item in sees the running total.

        when(orderCheckService.getTaxRate()).thenReturn(new BigDecimal("0.03"));

        mockMvc.perform(get("/api/settings").with(signIn.asHost())).andExpect(status().isOk());
        mockMvc.perform(get("/api/settings").with(signIn.asKitchen())).andExpect(status().isOk());
    }

    @Test
    void testGetSettings_WithNoSignIn_ShouldBeRefused() throws Exception {
        // WHAT: Test the endpoint with no token
        // WHY: Default deny: nothing but signing in is open to a request from nobody

        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAnyAnswer_ShouldCarryTheBackendsTime() throws Exception {
        // WHAT: Test that the X-Server-Time header is on an answer in a controller test too
        // WHY: ServerTimeFilter is a filter, and @ControllerTest must load it with its clock.
        //      If it stopped doing so, controller tests would no longer match the real app.

        when(orderCheckService.getTaxRate()).thenReturn(new BigDecimal("0.03"));

        mockMvc.perform(get("/api/settings").with(signIn.asServer()))
                .andExpect(header().exists(ServerTimeFilter.SERVER_TIME_HEADER));
    }
}
