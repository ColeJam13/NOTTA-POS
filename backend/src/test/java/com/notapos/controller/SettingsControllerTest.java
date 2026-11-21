package com.notapos.controller;

import com.notapos.config.ServerTimeFilter;
import com.notapos.service.OrderCheckService;
import com.notapos.service.SettingsService;
import com.notapos.enums.StaffRole;
import com.notapos.security.StaffSession;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import com.notapos.testsupport.ControllerTest;
import com.notapos.testsupport.SignIn;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @MockitoBean
    private SettingsService settingsService;

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

    // ------------------------------------------------------------------
    // The restaurant's template
    // ------------------------------------------------------------------

    @Test
    void testGetSettings_ShouldIncludeTheTemplate() throws Exception {
        // WHAT: Test that the template's name is in the same answer as the tax rate
        // WHY: Every terminal asks this endpoint which look to draw itself in

        when(orderCheckService.getTaxRate()).thenReturn(new BigDecimal("0.03"));
        when(settingsService.getTemplate()).thenReturn("plain");

        mockMvc.perform(get("/api/settings").with(signIn.asKitchen()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.template").value("plain"))
                .andExpect(jsonPath("$.taxRate").value(0.03));
    }

    @Test
    void testChooseTemplate_AsAManager_ShouldPassTheNameAndWhoIsChoosing() throws Exception {
        // WHAT: Test PUT /api/settings/template signed in as a manager
        // WHY: The service needs the name, and the person, to record with the change.
        //      The answer is the settings as they now stand.

        when(orderCheckService.getTaxRate()).thenReturn(new BigDecimal("0.03"));
        when(settingsService.getTemplate()).thenReturn("plain");

        mockMvc.perform(put("/api/settings/template").with(signIn.as(2L, "Morgan", StaffRole.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"template\":\"plain\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.template").value("plain"))
                .andExpect(jsonPath("$.taxRate").value(0.03));

        ArgumentCaptor<StaffSession> chosenBy = ArgumentCaptor.forClass(StaffSession.class);
        verify(settingsService).setTemplate(eq("plain"), chosenBy.capture());
        assertEquals(2L, chosenBy.getValue().staffId());
        assertEquals("Morgan", chosenBy.getValue().name());
    }

    @Test
    void testChooseTemplate_WithSomethingThatIsNotAName_ShouldReturn400() throws Exception {
        // WHAT: Test bodies that do not hold a template's name
        // WHY: The name is written into the page by the frontend, so only a plain short
        //      word is accepted. The message tells the screen what was wrong.

        for (String body : new String[]{"{}", "{\"template\":\"\"}", "{\"template\":\"Plain\"}", "{\"template\":\"coffee shop\"}",
                "{\"template\":\"<script>\"}", "{\"template\":\"a-name-that-is-longer-than-thirty-characters\"}"}) {
            mockMvc.perform(put("/api/settings/template").with(signIn.asManager())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.template").exists());
        }

        verify(settingsService, never()).setTemplate(any(), any());
    }

    @Test
    void testChooseTemplate_WhenNotAManager_ShouldBeRefused() throws Exception {
        // WHAT: Test PUT /api/settings/template with no sign-in, and signed in as a server
        // WHY: 401 = "we do not know who you are"; 403 = "we know, and no". The look of
        //      every terminal is the manager's to choose.

        mockMvc.perform(put("/api/settings/template")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"template\":\"plain\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/settings/template").with(signIn.asServer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"template\":\"plain\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your role (server) is not allowed to do that."));

        verify(settingsService, never()).setTemplate(any(), any());
    }
}
