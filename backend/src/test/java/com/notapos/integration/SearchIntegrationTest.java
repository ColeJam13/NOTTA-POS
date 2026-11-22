package com.notapos.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.notapos.entity.MenuItem;
import com.notapos.entity.RestaurantTable;
import com.notapos.enums.StaffRole;
import com.notapos.repository.MenuItemRepository;
import com.notapos.repository.OrderItemModifierRepository;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.PaymentRepository;
import com.notapos.repository.TableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the search box (step N2 of
 * Documentation/nothing-fake-brief.md): GET /api/search, through real requests
 * against a real database.
 *
 * WHAT THESE PROVE THAT THE UNIT TESTS CANNOT:
 *   - The whole path finds real checks: opened, rung in, sent, and paid through
 *     the app's own endpoints, then found by table, by number, and by opener.
 *   - A closed check found by searching follows the rule about tips exactly as
 *     the Tables screen does. A person is sent the tip on checks they opened; a
 *     manager on every check; for anyone else the two fields are NOT IN the
 *     answer. (Search is a second door onto the same checks. It must not be a
 *     way round the rule.)
 *   - Text typed into the box cannot change what the database is asked. The
 *     classic attempts find nothing and leave every row where it was.
 *
 * @author CJ
 */
class SearchIntegrationTest extends BaseIntegrationTest {

    @Autowired private TableRepository tableRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderItemModifierRepository orderItemModifierRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    // The people. Each is one sign-in, used for every request that person makes.
    private RequestPostProcessor sarah;         // A server
    private RequestPostProcessor dan;           // A bartender
    private RequestPostProcessor morgan;        // A manager
    private RequestPostProcessor kit;           // The kitchen

    private MenuItem burger;                    // 20.00, so a check for one is 20.60 with 3% tax

    private RestaurantTable w12;
    private Long sarahsPaidCheck;               // On W1: opened by Sarah, paid with a 5.00 tip
    private Long dansPaidCheck;                 // On W2: opened by Dan, paid with a 4.00 tip
    private Long sarahsOpenCheck;               // On W12: one burger sent, not paid
    private Long quickOrderCheck;               // On QO1: a Quick Order, open

    @BeforeEach
    void setUp() throws Exception {
        // Clean every table these tests touch (nothing rolls back by itself here)
        orderItemModifierRepository.deleteAll();
        orderItemRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        menuItemRepository.deleteAll();
        tableRepository.deleteAll();

        sarah = signIn.as(7L, "Sarah", StaffRole.SERVER);
        dan = signIn.as(8L, "Dan", StaffRole.BARTENDER);
        morgan = signIn.as(2L, "Morgan", StaffRole.MANAGER);
        kit = signIn.as(9L, "Kit", StaffRole.KITCHEN);

        burger = menuItem("Burger", "20.00", true, false);
        menuItem("Garden Toast", "12.50", true, true);            // On the menu, 86'd
        menuItem("Old Toast Special", "9.00", false, false);     // Taken off the menu

        sarahsPaidCheck = openAndSend(table("W1", false), sarah);
        pay(sarahsPaidCheck, sarah, "5.00", "credit_card");
        dansPaidCheck = openAndSend(table("W2", false), dan);
        pay(dansPaidCheck, dan, "4.00", "cash");
        w12 = table("W12", false);
        sarahsOpenCheck = openAndSend(w12, sarah);
        quickOrderCheck = openAndSend(table("QO1", true), sarah);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private RestaurantTable table(String number, boolean quickOrder) {
        RestaurantTable table = new RestaurantTable();
        table.setTableNumber(number);
        table.setSection("Window");
        table.setSeatCount(4);
        table.setStatus("available");
        table.setIsQuickOrder(quickOrder);
        return tableRepository.save(table);
    }

    private MenuItem menuItem(String name, String price, boolean active, boolean is86d) {
        MenuItem item = new MenuItem();
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        item.setCategory("Savory");
        item.setIsActive(active);
        item.setIs86d(is86d);
        return menuItemRepository.save(item);
    }

    // A check through the real endpoints: one person opens it, rings in a burger, and sends it
    private Long openAndSend(RestaurantTable table, RequestPostProcessor openedBy) throws Exception {
        MvcResult opened = mockMvc.perform(post("/api/orders").with(openedBy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":" + table.getTableId() + ",\"orderType\":\"dine_in\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long orderId = objectMapper.readTree(opened.getResponse().getContentAsString()).get("orderId").asLong();

        mockMvc.perform(post("/api/order-items").with(openedBy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"menuItemId\":" + burger.getMenuItemId()
                                + ",\"quantity\":1,\"modifierIds\":[],\"ringInPosition\":0}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/order-items/order/" + orderId + "/send-now").with(openedBy))
                .andExpect(status().isOk());
        return orderId;
    }

    private void pay(Long orderId, RequestPostProcessor paidBy, String tip, String method) throws Exception {
        mockMvc.perform(post("/api/payments").with(paidBy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"amount\":20.60,\"tipAmount\":" + tip
                                + ",\"paymentMethod\":\"" + method + "\"}"))
                .andExpect(status().isCreated());
    }

    // The whole answer for what was typed, as a given person is sent it
    private JsonNode search(RequestPostProcessor who, String typed, String... closed) throws Exception {
        var request = get("/api/search").param("q", typed).with(who);
        if (closed.length > 0) {
            request = request.param("closed", closed[0]);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static List<String> values(JsonNode answer, String group, String field) {
        List<String> found = new ArrayList<>();
        for (JsonNode result : answer.get(group).get("results")) {
            found.add(result.get(field).asString());
        }
        return found;
    }

    private static List<Long> checkNumbers(JsonNode answer, String group) {
        List<Long> found = new ArrayList<>();
        for (JsonNode result : answer.get(group).get("results")) {
            found.add(result.get("orderId").asLong());
        }
        return found;
    }

    // One closed check out of an answer, as raw JSON, so a test can ask whether a field is there at all
    private static JsonNode closedCheck(JsonNode answer, Long orderId) {
        for (JsonNode check : answer.get("closedChecks").get("results")) {
            if (check.get("orderId").asLong() == orderId) {
                return check;
            }
        }
        return fail("Closed check " + orderId + " is not in the answer at all");
    }

    private static void assertNothingFound(JsonNode answer) {
        for (String group : List.of("tables", "openChecks", "closedChecks", "menuItems")) {
            assertEquals(0, answer.get(group).get("results").size(), group + " should be empty");
            assertFalse(answer.get(group).get("more").asBoolean());
        }
    }

    // ------------------------------------------------------------------
    // Finding things
    // ------------------------------------------------------------------

    @Test
    void testSearch_ByTableNumber_ShouldFindTheTableItsOpenCheckAndTodaysPaidOne() throws Exception {
        // WHAT: Sarah types "w1"
        // WHY: The point of the box: a table, what is open on it, and what was paid on
        //      it today, without walking through three screens.
        JsonNode answer = search(sarah, "w1");

        assertEquals(List.of("W1", "W12"), values(answer, "tables", "tableNumber"));     // The shorter number first
        assertEquals(List.of(sarahsOpenCheck), checkNumbers(answer, "openChecks"));     // The open check on W12
        assertEquals(List.of(sarahsPaidCheck), checkNumbers(answer, "closedChecks"));   // The paid one on W1

        JsonNode open = answer.get("openChecks").get("results").get(0);
        assertEquals(w12.getTableId(), open.get("tableId").asLong());                   // What the order screen needs to open it
        assertEquals("W12", open.get("tableNumber").asString());
        assertEquals("occupied", open.get("status").asString());
        assertEquals("Sarah", open.get("serverName").asString());
        assertEquals(1, open.get("itemCount").asInt());
        assertEquals(0, new BigDecimal("20.00").compareTo(open.get("subtotal").decimalValue()));

        JsonNode table = answer.get("tables").get("results").get(1);
        assertEquals("occupied", table.get("status").asString());
        assertEquals(4, table.get("seatCount").asInt());
    }

    @Test
    void testSearch_ByCheckNumber_ShouldFindThatCheckOpenOrClosed() throws Exception {
        assertEquals(List.of(dansPaidCheck), checkNumbers(search(sarah, String.valueOf(dansPaidCheck)), "closedChecks"));
        assertEquals(List.of(sarahsOpenCheck), checkNumbers(search(sarah, String.valueOf(sarahsOpenCheck)), "openChecks"));
    }

    @Test
    void testSearch_ByWhoOpenedIt_ShouldFindTheirChecks_InAnyCase() throws Exception {
        JsonNode answer = search(morgan, "DAN");

        assertEquals(List.of(dansPaidCheck), checkNumbers(answer, "closedChecks"));
        assertEquals(List.of(), checkNumbers(answer, "openChecks"));
    }

    @Test
    void testSearch_ShouldFindAQuickOrderAsACheckAndNotAsATable() throws Exception {
        // WHY: Every Quick Order is a row in "tables", but nobody is seated at QO1
        JsonNode answer = search(sarah, "qo1");

        assertEquals(List.of(), values(answer, "tables", "tableNumber"));
        assertEquals(List.of(quickOrderCheck), checkNumbers(answer, "openChecks"));
        assertTrue(answer.get("openChecks").get("results").get(0).get("isQuickOrder").asBoolean());
    }

    @Test
    void testSearch_ByMenuItemName_ShouldSayWhatItCostsAndWhetherItIsOut() throws Exception {
        // WHAT: "toast" matches one item on the menu (86'd) and one taken off it
        JsonNode answer = search(kit, "toast");

        assertEquals(List.of("Garden Toast"), values(answer, "menuItems", "name"));
        JsonNode item = answer.get("menuItems").get("results").get(0);
        assertTrue(item.get("is86d").asBoolean());
        assertEquals(0, new BigDecimal("12.50").compareTo(item.get("price").decimalValue()));
        assertEquals("Savory", item.get("category").asString());
    }

    @Test
    void testSearch_WithOneLetter_ShouldFindNothing_AndWithOneDigit_ShouldSearch() throws Exception {
        // WHY: Words search from the second character; digits from the first ("1" is a table)
        assertNothingFound(search(sarah, "w"));

        assertEquals(List.of("W1", "W12"), values(search(sarah, "1"), "tables", "tableNumber"));     // Not W2, and not the Quick Order QO1
    }

    // ------------------------------------------------------------------
    // How far back
    // ------------------------------------------------------------------

    @Test
    void testSearch_ShouldFindOnlyTodaysPaidChecksUnlessAskedToLookBack() throws Exception {
        // WHAT: Dan's check is moved to yesterday, Sarah's to ten days ago
        jdbc.update("UPDATE orders SET closed_at = closed_at - INTERVAL '1 day' WHERE order_id = ?", dansPaidCheck);
        jdbc.update("UPDATE orders SET closed_at = closed_at - INTERVAL '10 days' WHERE order_id = ?", sarahsPaidCheck);

        assertEquals(List.of(), checkNumbers(search(morgan, "w2"), "closedChecks"));                  // Today: neither
        assertEquals(List.of(dansPaidCheck), checkNumbers(search(morgan, "w2", "yesterday"), "closedChecks"));
        assertEquals(List.of(dansPaidCheck), checkNumbers(search(morgan, "w2", "7days"), "closedChecks"));
        assertEquals(List.of(), checkNumbers(search(morgan, "w1", "7days"), "closedChecks"));              // Ten days is further than seven
        assertEquals(List.of(sarahsPaidCheck), checkNumbers(search(morgan, "w1", "30days"), "closedChecks"));
    }

    // ------------------------------------------------------------------
    // Tips: the same rule as the Tables screen
    // ------------------------------------------------------------------

    private static void assertTipShown(JsonNode check, String tip, String method) {
        assertTrue(check.has("tipAmount"), "the tip should be in the answer");
        assertEquals(0, new BigDecimal(tip).compareTo(check.get("tipAmount").decimalValue()));
        assertEquals(method, check.get("paymentMethod").asString());
    }

    private static void assertTipLeftOut(JsonNode check) {
        assertFalse(check.has("tipAmount"), "the tip must not be in the answer, not even as null or 0");
        assertFalse(check.has("paymentMethod"), "the payment method must not be in the answer");
    }

    @Test
    void testAServer_ShouldBeSentTheTipOnACheckTheyOpened_AndNotOnSomeoneElses() throws Exception {
        // WHY: Search is a second way to reach a closed check. It must not be a way
        //      round the rule that a tip is for the person who opened the check.
        assertTipShown(closedCheck(search(sarah, "sarah"), sarahsPaidCheck), "5.00", "credit_card");
        assertTipLeftOut(closedCheck(search(sarah, "dan"), dansPaidCheck));
        assertTipLeftOut(closedCheck(search(sarah, String.valueOf(dansPaidCheck)), dansPaidCheck));     // Not by its number either
    }

    @Test
    void testAManager_ShouldBeSentEveryTip() throws Exception {
        assertTipShown(closedCheck(search(morgan, "sarah"), sarahsPaidCheck), "5.00", "credit_card");
        assertTipShown(closedCheck(search(morgan, "dan"), dansPaidCheck), "4.00", "cash");
    }

    @Test
    void testTheKitchen_ShouldFindTheChecksAndBeSentNoTips() throws Exception {
        // WHAT: Someone who opens no checks
        // WHY: They still see that the check was closed and its total; never a tip
        JsonNode check = closedCheck(search(kit, "dan"), dansPaidCheck);

        assertTipLeftOut(check);
        assertEquals(0, new BigDecimal("20.60").compareTo(check.get("total").decimalValue()));
        assertEquals(1, check.get("items").size());
    }

    // ------------------------------------------------------------------
    // What is typed cannot change the query
    // ------------------------------------------------------------------

    @Test
    void testSearch_WithSqlTypedIn_ShouldFindNothingAndLeaveEveryRowWhereItWas() throws Exception {
        // WHAT: The classic attempts, typed into the box
        // WHY: What is typed is handed to the database as a value to look for, never as
        //      part of the question. So these are just text that matches nothing.
        long orders = orderRepository.count();
        long tables = tableRepository.count();
        long payments = paymentRepository.count();

        assertNothingFound(search(kit, "'; DROP TABLE orders; --"));
        assertNothingFound(search(kit, "' OR '1'='1"));
        assertNothingFound(search(kit, "w1' UNION SELECT pin_hash FROM staff --"));
        assertNothingFound(search(kit, "%%"));                    // Two percent signs: looked for as percent signs
        assertNothingFound(search(kit, "__"));                    // The same for LIKE's other special character

        assertEquals(orders, orderRepository.count());
        assertEquals(tables, tableRepository.count());
        assertEquals(payments, paymentRepository.count());
        assertEquals(List.of("W1", "W12"), values(search(kit, "w1"), "tables", "tableNumber"));     // And the search still works
    }

    @Test
    void testSearch_WithNobodySignedIn_ShouldBeRefused() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "w1"))
                .andExpect(status().isUnauthorized());
    }
}
