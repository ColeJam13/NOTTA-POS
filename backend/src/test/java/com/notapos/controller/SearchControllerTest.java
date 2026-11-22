package com.notapos.controller;

import com.notapos.dto.ClosedCheckView;
import com.notapos.dto.SearchGroupView;
import com.notapos.dto.SearchMenuItemView;
import com.notapos.dto.SearchOpenCheckView;
import com.notapos.dto.SearchResultsView;
import com.notapos.dto.SearchTableView;
import com.notapos.enums.SearchPeriod;
import com.notapos.enums.StaffRole;
import com.notapos.security.StaffSession;
import com.notapos.service.SearchService;
import com.notapos.testsupport.ControllerTest;
import com.notapos.testsupport.SignIn;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for SearchController.
 *
 * Tests the one endpoint behind the search box in the top bar.
 * Uses MockMvc to simulate HTTP requests without starting full server.
 *
 * @author CJ
 */
@ControllerTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignIn signIn;              // Says who a request comes from: .with(signIn.asServer())

    @MockitoBean
    private SearchService searchService;

    @Test
    void testSearch_ShouldReturnTheFourGroups() throws Exception {
        // WHAT: Test GET /api/search?q=w1
        // WHY: The search box draws everything it shows from this one response
        LocalDateTime opened = LocalDateTime.of(2026, 11, 20, 12, 30);
        when(searchService.search(eq("w1"), eq(SearchPeriod.TODAY), any())).thenReturn(new SearchResultsView(
                new SearchGroupView<>(List.of(new SearchTableView(1L, "W1", "Window", 4, "occupied", false)), false),
                new SearchGroupView<>(List.of(new SearchOpenCheckView(31L, 1L, "W1", "occupied", false, "Sarah", 2,
                        new BigDecimal("43.00"), opened)), true),
                new SearchGroupView<>(List.of(new ClosedCheckView(20L, 1L, "W1", false, "Sarah", opened, opened,
                        new BigDecimal("17.00"), new BigDecimal("0.51"), new BigDecimal("17.51"), null, null, List.of())), false),
                new SearchGroupView<>(List.of(new SearchMenuItemView(3L, "W1 Special", "Savory", new BigDecimal("16.00"), true)), false)));

        mockMvc.perform(get("/api/search").param("q", "w1").with(signIn.asServer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables.results[0].tableNumber").value("W1"))
                .andExpect(jsonPath("$.tables.results[0].seatCount").value(4))
                .andExpect(jsonPath("$.tables.results[0].isQuickOrder").value(false))
                .andExpect(jsonPath("$.tables.more").value(false))
                .andExpect(jsonPath("$.openChecks.results[0].orderId").value(31))
                .andExpect(jsonPath("$.openChecks.results[0].subtotal").value(43.00))
                .andExpect(jsonPath("$.openChecks.results[0].itemCount").value(2))
                .andExpect(jsonPath("$.openChecks.more").value(true))
                .andExpect(jsonPath("$.closedChecks.results[0].orderId").value(20))
                .andExpect(jsonPath("$.closedChecks.results[0].total").value(17.51))
                .andExpect(jsonPath("$.menuItems.results[0].name").value("W1 Special"))
                .andExpect(jsonPath("$.menuItems.results[0].is86d").value(true))
                // A closed check whose tip this person may not see does not mention a tip at all
                .andExpect(content().string(not(containsString("tipAmount"))))
                .andExpect(content().string(not(containsString("paymentMethod"))));
    }

    @Test
    void testSearch_ShouldTellTheServiceWhoIsAsking() throws Exception {
        // WHY: Which tips are in the answer depends on the person, so the controller
        //      must pass on the signed-in person and not somebody the request names
        when(searchService.search(any(), any(), any())).thenReturn(SearchResultsView.nothing());

        mockMvc.perform(get("/api/search").param("q", "sarah").with(signIn.as(7L, "Sarah", StaffRole.SERVER)))
                .andExpect(status().isOk());

        ArgumentCaptor<StaffSession> who = ArgumentCaptor.forClass(StaffSession.class);
        verify(searchService).search(eq("sarah"), eq(SearchPeriod.TODAY), who.capture());
        assertEquals(7L, who.getValue().staffId());
        assertEquals(StaffRole.SERVER, who.getValue().role());
    }

    @Test
    void testSearch_ShouldLookAsFarBackAsAsked() throws Exception {
        // WHAT: ?closed=yesterday, 7days, 30days
        when(searchService.search(any(), any(), any())).thenReturn(SearchResultsView.nothing());

        mockMvc.perform(get("/api/search").param("q", "w1").param("closed", "yesterday").with(signIn.asServer())).andExpect(status().isOk());
        mockMvc.perform(get("/api/search").param("q", "w1").param("closed", "7days").with(signIn.asServer())).andExpect(status().isOk());
        mockMvc.perform(get("/api/search").param("q", "w1").param("closed", "30days").with(signIn.asServer())).andExpect(status().isOk());

        verify(searchService).search(eq("w1"), eq(SearchPeriod.YESTERDAY), any());
        verify(searchService).search(eq("w1"), eq(SearchPeriod.LAST_7_DAYS), any());
        verify(searchService).search(eq("w1"), eq(SearchPeriod.LAST_30_DAYS), any());
    }

    @Test
    void testSearch_WithAPeriodThatIsNotOne_ShouldReturn400() throws Exception {
        // WHY: A typo in the request should be told so, not quietly searched as "today"
        mockMvc.perform(get("/api/search").param("q", "w1").param("closed", "forever").with(signIn.asServer()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(searchService);
    }

    @Test
    void testSearch_WithNothingToSearchFor_ShouldReturn400() throws Exception {
        // WHAT: No q at all (not an empty one: that is answered with empty groups by the service)
        mockMvc.perform(get("/api/search").with(signIn.asServer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSearch_ShouldPassOnWhateverWasTypedUntouched() throws Exception {
        // WHAT: Text with a quote, a semicolon, and a percent sign
        // WHY: The controller does not clean or judge it. It is only ever compared with
        //      (SearchService), so there is nothing here to "sanitize" and get wrong.
        when(searchService.search(any(), any(), any())).thenReturn(SearchResultsView.nothing());
        String typed = "'; DROP TABLE orders; -- 100%";

        mockMvc.perform(get("/api/search").param("q", typed).with(signIn.asKitchen()))
                .andExpect(status().isOk());

        verify(searchService).search(eq(typed), eq(SearchPeriod.TODAY), any());
    }

    @Test
    void testSearch_WithNobodySignedIn_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "w1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(searchService);
    }
}
