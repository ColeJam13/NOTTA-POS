package com.notapos.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.notapos.entity.Order;
import com.notapos.entity.RestaurantTable;
import com.notapos.repository.OrderItemModifierRepository;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.PaymentRepository;
import com.notapos.repository.TableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for two things every screen must take from the backend and
 * not work out for itself: the tax rate, and what time it is.
 *
 * THE TAX RATE. The order screen showed a running total using its own copy of
 * the rate, and the backend charged using notta.tax-rate. Change one and not
 * the other, and the guest is shown one total and charged another. Now the
 * screen asks: GET /api/settings.
 *
 * THE CLOCK. The backend stamps "this edit window closes at 7:15:31 pm" and
 * "sent at 7:02 pm" with its own clock. The screens compared those stamps
 * with the tablet's clock, so a tablet running two minutes slow showed an edit
 * window with two minutes left on it, and one in another time zone showed
 * tickets as hours late. Now every answer carries the backend's own time in
 * the X-Server-Time header, in the same form as those stamps, and the screens
 * measure against that.
 *
 * @author CJ
 */
class OneRateOneClockIntegrationTest extends BaseIntegrationTest {

    private static final String SERVER_TIME_HEADER = "X-Server-Time";
    private static final String FRONTEND_ADDRESS = "http://localhost:3000";     // notta.cors.allowed-origins in application.properties

    @Autowired private TableRepository tableRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderItemModifierRepository orderItemModifierRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ObjectMapper objectMapper;

    private RestaurantTable table;

    @BeforeEach
    void setUp() {
        // Clean every table these tests touch (nothing rolls back by itself here)
        orderItemModifierRepository.deleteAll();
        orderItemRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        tableRepository.deleteAll();

        table = new RestaurantTable();
        table.setTableNumber("W1");
        table.setSeatCount(2);
        table.setStatus("available");
        table = tableRepository.save(table);
    }

    private JsonNode bodyOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // How far apart two backend times are, whichever is later
    private static Duration gapBetween(LocalDateTime a, LocalDateTime b) {
        return Duration.between(a, b).abs();
    }

    // ------------------------------------------------------------------
    // One tax rate
    // ------------------------------------------------------------------

    @Test
    void testSettings_ShouldGiveTheSameTaxRateTheCheckIsWorkedOutWith() throws Exception {
        // WHAT: The rate GET /api/settings hands the order screen, next to the rate on a real check
        // WHY: They must be one number, not two that happen to match. If the screen's rate
        //      and the charged rate can differ, the guest sees one total and pays another.

        Order order = new Order();
        order.setTableId(table.getTableId());
        order.setStatus("open");
        order = orderRepository.save(order);

        BigDecimal rateOnTheCheck = bodyOf(mockMvc.perform(
                        get("/api/orders/" + order.getOrderId() + "/check").with(signIn.asServer()))
                .andExpect(status().isOk())
                .andReturn()).get("taxRate").decimalValue();

        BigDecimal rateTheScreenIsGiven = bodyOf(mockMvc.perform(get("/api/settings").with(signIn.asServer()))
                .andExpect(status().isOk())
                .andReturn()).get("taxRate").decimalValue();

        assertEquals(0, rateOnTheCheck.compareTo(rateTheScreenIsGiven),
                "The order screen is given " + rateTheScreenIsGiven + " but checks are charged at " + rateOnTheCheck);
    }

    @Test
    void testSettings_ShouldBeReadableByEveryRoleThatOpensTheOrderScreen() throws Exception {
        // WHAT: Hosts and bartenders ask for the settings too
        // WHY: Anyone who can ring an item in sees the running total

        mockMvc.perform(get("/api/settings").with(signIn.asHost())).andExpect(status().isOk());
        mockMvc.perform(get("/api/settings").with(signIn.asBartender())).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // One clock
    // ------------------------------------------------------------------

    @Test
    void testEveryAnswer_ShouldCarryTheBackendsTime() throws Exception {
        // WHAT: The X-Server-Time header on an ordinary answer
        // WHY: It is how a screen learns what time the backend thinks it is. It must be
        //      there, readable as a date and time with no zone (the form the backend's
        //      timestamps have), and it must be now.

        LocalDateTime before = LocalDateTime.now();

        String headerValue = mockMvc.perform(get("/api/tables").with(signIn.asServer()))
                .andExpect(status().isOk())
                .andExpect(header().exists(SERVER_TIME_HEADER))
                .andReturn().getResponse().getHeader(SERVER_TIME_HEADER);

        LocalDateTime serverTime = LocalDateTime.parse(headerValue);    // Throws if it carries a zone or is not a time at all
        assertTrue(gapBetween(before, serverTime).compareTo(Duration.ofSeconds(5)) < 0,
                "X-Server-Time was " + headerValue + ", which is not within 5 seconds of " + before);
    }

    @Test
    void testTheBackendsTime_ShouldBeOnTheSameClockAsItsTimestamps() throws Exception {
        // WHAT: Open an order, and compare the time in the header with the order's createdAt
        // WHY: The header is only useful if it can be compared with the stamps in the answer
        //      beside it. Had the header been in UTC while the stamps are in the backend's
        //      local time, the two would be hours apart and every countdown would be wrong
        //      by exactly that much.

        MvcResult opened = mockMvc.perform(post("/api/orders").with(signIn.asServer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":" + table.getTableId() + ",\"orderType\":\"dine_in\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        LocalDateTime serverTime = LocalDateTime.parse(opened.getResponse().getHeader(SERVER_TIME_HEADER));
        LocalDateTime createdAt = LocalDateTime.parse(bodyOf(opened).get("createdAt").asString());

        assertTrue(gapBetween(serverTime, createdAt).compareTo(Duration.ofSeconds(5)) < 0,
                "The header said " + serverTime + " and the order was stamped " + createdAt);
    }

    @Test
    void testARefusal_ShouldCarryTheBackendsTimeToo() throws Exception {
        // WHAT: A request with no sign-in (401)
        // WHY: "Every answer" means every answer. Nothing a screen receives from the backend
        //      should leave it guessing the time, and a rule with no exceptions is one nobody
        //      has to remember.

        mockMvc.perform(get("/api/tables"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(SERVER_TIME_HEADER));
    }

    @Test
    void testTheBrowser_ShouldBeAllowedToReadTheBackendsTime() throws Exception {
        // WHAT: The same request, sent the way the frontend sends it: from another address
        // WHY: The frontend (port 3000) and the backend (port 8080) are different "origins".
        //      A browser hides every response header from the page except a short standard
        //      list, unless the backend names the extra ones in Access-Control-Expose-Headers.
        //      Without that the header would be sent, and the screen could never see it.

        mockMvc.perform(get("/api/tables").with(signIn.asServer()).header("Origin", FRONTEND_ADDRESS))
                .andExpect(status().isOk())
                .andExpect(header().exists(SERVER_TIME_HEADER))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString(SERVER_TIME_HEADER)));
    }
}
