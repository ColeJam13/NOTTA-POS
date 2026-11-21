package com.notapos.controller;

import com.notapos.dto.ActiveTableView;
import com.notapos.dto.ClosedCheckItemView;
import com.notapos.dto.ClosedCheckView;
import com.notapos.dto.OrderItemModifierView;
import com.notapos.dto.TablesOverviewView;
import com.notapos.enums.StaffRole;
import com.notapos.security.StaffSession;
import com.notapos.service.TableOverviewService;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.notapos.testsupport.ControllerTest;
import com.notapos.testsupport.SignIn;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for TableOverviewController.
 *
 * Tests the one endpoint behind the Tables and Quick Orders screens.
 * Uses MockMvc to simulate HTTP requests without starting full server.
 *
 * @author CJ
 */

@ControllerTest(TableOverviewController.class)
class TableOverviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignIn signIn;              // Says who a request comes from: .with(signIn.asServer())

    @MockBean
    private TableOverviewService tableOverviewService;

    @Test
    void testGetOverview_ShouldReturnActiveTablesAndClosedChecks() throws Exception {
        // WHAT: Test GET /api/tables/overview
        // WHY: The Tables screen reads everything it shows from this one response

        LocalDateTime now = LocalDateTime.now();
        when(tableOverviewService.getOverview(eq(false), any())).thenReturn(new TablesOverviewView(
                List.of(new ActiveTableView(1L, "W1", "occupied", "CJ", false, 3, 1, 1, 0, 1, new BigDecimal("50.00"), now)),
                List.of(new ClosedCheckView(20L, 2L, "W2", false, "CJ", now, now,
                        new BigDecimal("17.00"), new BigDecimal("0.51"), new BigDecimal("17.51"),
                        new BigDecimal("3.50"), "credit_card",
                        List.of(new ClosedCheckItemView(200L, "Combo Plate", 1, new BigDecimal("17.00"), "nut allergy",
                                List.of(new OrderItemModifierView(200L, 14L, "French Toast", new BigDecimal("2.00")))))))));

        mockMvc.perform(get("/api/tables/overview").with(signIn.asServer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeTables[0].tableNumber").value("W1"))
                .andExpect(jsonPath("$.activeTables[0].itemCount").value(3))
                .andExpect(jsonPath("$.activeTables[0].subtotal").value(50.00))
                .andExpect(jsonPath("$.closedChecks[0].orderId").value(20))
                .andExpect(jsonPath("$.closedChecks[0].total").value(17.51))
                .andExpect(jsonPath("$.closedChecks[0].tipAmount").value(3.50))
                .andExpect(jsonPath("$.closedChecks[0].paymentMethod").value("credit_card"))
                .andExpect(jsonPath("$.closedChecks[0].items[0].name").value("Combo Plate"))
                .andExpect(jsonPath("$.closedChecks[0].items[0].specialInstructions").value("nut allergy"))
                .andExpect(jsonPath("$.closedChecks[0].items[0].modifiers[0].name").value("French Toast"));

        // With no parameter, regular tables are returned (not Quick Orders)
        verify(tableOverviewService).getOverview(eq(false), any());
    }

    @Test
    void testGetOverview_WithQuickOrdersTrue_ShouldAskForQuickOrders() throws Exception {
        // WHAT: Test GET /api/tables/overview?quickOrders=true
        // WHY: The Quick Orders screen uses the same endpoint

        when(tableOverviewService.getOverview(eq(true), any())).thenReturn(new TablesOverviewView(List.of(), List.of()));

        mockMvc.perform(get("/api/tables/overview").with(signIn.asServer()).param("quickOrders", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeTables").isEmpty())
                .andExpect(jsonPath("$.closedChecks").isEmpty());

        verify(tableOverviewService).getOverview(eq(true), any());
        verify(tableOverviewService, never()).getOverview(eq(false), any());
    }

    @Test
    void testGetOverview_ShouldTellTheServiceWhoIsAsking() throws Exception {
        // WHAT: Test that the signed-in person is passed on to the service
        // WHY: Which tips are in the answer depends on who asks (a person sees the tip on
        //      checks they opened; a manager sees all). The service can only decide that
        //      if the controller hands it the right person.

        when(tableOverviewService.getOverview(eq(false), any())).thenReturn(new TablesOverviewView(List.of(), List.of()));

        mockMvc.perform(get("/api/tables/overview").with(signIn.as(7L, "Sarah", StaffRole.SERVER)))
                .andExpect(status().isOk());

        ArgumentCaptor<StaffSession> whoAsked = ArgumentCaptor.forClass(StaffSession.class);
        verify(tableOverviewService).getOverview(eq(false), whoAsked.capture());
        assertEquals(7L, whoAsked.getValue().staffId());
        assertEquals(StaffRole.SERVER, whoAsked.getValue().role());
    }

    @Test
    void testGetOverview_ShouldLeaveAHiddenTipOutOfTheJsonAltogether() throws Exception {
        // WHAT: Test the JSON of a closed check whose tip and payment method are null
        // WHY: Null is how the service says "this person may not see it". The answer must
        //      then not mention a tip at all: "tipAmount": null would still be drawn by a
        //      careless screen, and tells the reader there is something being withheld
        //      in a field they can name. The other fields are untouched.

        LocalDateTime now = LocalDateTime.now();
        when(tableOverviewService.getOverview(eq(false), any())).thenReturn(new TablesOverviewView(
                List.of(),
                List.of(new ClosedCheckView(20L, 2L, "W2", false, "Dan", now, now,
                        new BigDecimal("17.00"), new BigDecimal("0.51"), new BigDecimal("17.51"),
                        null, null,
                        List.of()))));

        mockMvc.perform(get("/api/tables/overview").with(signIn.asServer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closedChecks[0].tipAmount").doesNotExist())
                .andExpect(jsonPath("$.closedChecks[0].paymentMethod").doesNotExist())
                .andExpect(content().string(not(containsString("tipAmount"))))
                .andExpect(content().string(not(containsString("paymentMethod"))))
                .andExpect(jsonPath("$.closedChecks[0].serverName").value("Dan"))
                .andExpect(jsonPath("$.closedChecks[0].total").value(17.51));
    }
}
