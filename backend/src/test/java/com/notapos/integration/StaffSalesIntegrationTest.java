package com.notapos.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.notapos.entity.MenuItem;
import com.notapos.entity.Order;
import com.notapos.entity.OrderItem;
import com.notapos.entity.RestaurantTable;
import com.notapos.entity.Staff;
import com.notapos.enums.StaffRole;
import com.notapos.repository.MenuItemRepository;
import com.notapos.repository.OrderItemModifierRepository;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.PaymentRepository;
import com.notapos.repository.StaffRepository;
import com.notapos.repository.TableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for per-person sales, the numbers behind the leaderboard
 * (step N4 of the nothing-fake brief, the owner's decision D6).
 *
 * THE RULE THESE HOLD THE BACKEND TO: the leaderboard and the Financials screen
 * are two views of the same sales. Add up what every person sold, add the checks
 * that have nobody on record, and the answer is Financials' revenue for the same
 * period, to the cent. If the two reports ever count a check differently (which
 * period it belongs to, what an old check is worth, whether tax is in the figure)
 * the first test here fails.
 *
 * WHY HERE AND NOT IN A UNIT TEST: each report on its own does the right thing
 * with faked data. Only running both against the same real rows shows that they
 * agree. Checks are opened, rung in, sent, and paid through the real endpoints,
 * so the amounts are the ones the backend itself recorded.
 *
 * Also here: a sale belongs to whoever OPENED the check, and no tip is in the
 * answer for anybody, read as raw JSON the way a person at a browser could.
 *
 * @author CJ
 */
class StaffSalesIntegrationTest extends BaseIntegrationTest {

    @Autowired private TableRepository tableRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderItemModifierRepository orderItemModifierRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    // The way the screens write a moment for these reports: 2026-11-18T00:00:00, no time zone
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // The people, as rows in the staff table, and each one's sign-in
    private Staff sarahRow, danRow, kitRow, morganRow;
    private RequestPostProcessor sarah;         // A server
    private RequestPostProcessor dan;           // A bartender
    private RequestPostProcessor kit;           // The kitchen
    private RequestPostProcessor morgan;        // A manager

    private MenuItem burger;                    // 20.00
    private MenuItem soda;                      // 3.50

    private int tablesMade = 0;

    @BeforeEach
    void setUp() {
        // Clean every table these tests touch (nothing rolls back by itself here)
        orderItemModifierRepository.deleteAll();
        orderItemRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        menuItemRepository.deleteAll();
        tableRepository.deleteAll();
        jdbc.update("DELETE FROM staff_sessions");
        staffRepository.deleteAll();

        sarahRow = person("Sarah", StaffRole.SERVER);
        danRow = person("Dan", StaffRole.BARTENDER);
        kitRow = person("Kit", StaffRole.KITCHEN);
        morganRow = person("Morgan", StaffRole.MANAGER);
        sarah = signIn.as(sarahRow.getStaffId(), "Sarah", StaffRole.SERVER);
        dan = signIn.as(danRow.getStaffId(), "Dan", StaffRole.BARTENDER);
        kit = signIn.as(kitRow.getStaffId(), "Kit", StaffRole.KITCHEN);
        morgan = signIn.as(morganRow.getStaffId(), "Morgan", StaffRole.MANAGER);

        burger = menuItem("Burger", "20.00");
        soda = menuItem("Soda", "3.50");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Staff person(String name, StaffRole role) {
        Staff staff = new Staff();
        staff.setName(name);
        staff.setRole(role.getValue());
        staff.setPinHash("not-a-real-hash-" + name);        // Nobody signs in with a PIN in these tests
        staff.setIsActive(true);
        return staffRepository.save(staff);
    }

    private MenuItem menuItem(String name, String price) {
        MenuItem item = new MenuItem();
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        item.setCategory("Savory");
        item.setIsActive(true);
        return menuItemRepository.save(item);
    }

    private RestaurantTable newTable() {
        RestaurantTable table = new RestaurantTable();
        table.setTableNumber("T" + (++tablesMade));
        table.setSeatCount(2);
        table.setStatus("available");
        table.setIsQuickOrder(false);
        return tableRepository.save(table);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // One line of a check: a menu item and how many
    private record Line(MenuItem item, int quantity) {
    }

    private Line of(MenuItem item, int quantity) {
        return new Line(item, quantity);
    }

    // One person opens a check on a new table, rings the lines in, and sends them. Open, not yet paid.
    private long openAndSend(RequestPostProcessor openedBy, Line... lines) throws Exception {
        MvcResult opened = mockMvc.perform(post("/api/orders").with(openedBy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":" + newTable().getTableId() + ",\"orderType\":\"dine_in\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long orderId = read(opened).get("orderId").asLong();

        for (int position = 0; position < lines.length; position++) {
            mockMvc.perform(post("/api/order-items").with(openedBy)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":" + orderId + ",\"menuItemId\":" + lines[position].item().getMenuItemId()
                                    + ",\"quantity\":" + lines[position].quantity() + ",\"modifierIds\":[],\"ringInPosition\":" + position + "}"))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/order-items/order/" + orderId + "/send-now").with(openedBy))
                .andExpect(status().isOk());
        return orderId;
    }

    // Someone takes the payment for a check: exactly what the backend says is due, plus a tip
    private void pay(long orderId, RequestPostProcessor paidBy, String tip) throws Exception {
        MvcResult check = mockMvc.perform(get("/api/orders/" + orderId + "/check").with(paidBy))
                .andExpect(status().isOk())
                .andReturn();
        String due = read(check).get("total").decimalValue().toPlainString();

        mockMvc.perform(post("/api/payments").with(paidBy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"amount\":" + due + ",\"tipAmount\":" + tip
                                + ",\"paymentMethod\":\"credit_card\"}"))
                .andExpect(status().isCreated());
    }

    // A whole check, through the real endpoints, opened and paid by one person
    private long sell(RequestPostProcessor who, Line... lines) throws Exception {
        long orderId = openAndSend(who, lines);
        pay(orderId, who, "5.00");
        return orderId;
    }

    // A closed check written straight into the database: for the cases no request can make today
    private Order closedCheck(Long staffId, String nameOnIt, String subtotal, String tax, String total) {
        Order order = new Order();
        order.setTableId(newTable().getTableId());
        order.setOrderType("dine_in");
        order.setStatus("closed");
        order.setStaffId(staffId);
        order.setServerName(nameOnIt);
        order.setClosedAt(LocalDateTime.now());
        order.setSubtotal(new BigDecimal(subtotal));
        order.setTax(new BigDecimal(tax));
        order.setTotal(new BigDecimal(total));
        return orderRepository.save(order);
    }

    private void completedItemOn(Order order, MenuItem item, int quantity) {
        OrderItem line = new OrderItem();
        line.setOrderId(order.getOrderId());
        line.setMenuItemId(item.getMenuItemId());
        line.setQuantity(quantity);
        line.setPrice(item.getPrice());
        line.setStatus("completed");
        line.setIsLocked(true);
        orderItemRepository.save(line);
    }

    // A period that holds "now" with room to spare on both sides
    private String yesterday() {
        return LocalDateTime.now().minusDays(1).format(LOCAL);
    }

    private String tomorrow() {
        return LocalDateTime.now().plusDays(1).format(LOCAL);
    }

    // The leaderboard's rows as a given person is sent them, for a period
    private JsonNode staffSales(RequestPostProcessor asWhom, String from, String to) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/reports/staff-sales").with(asWhom)
                        .param("from", from).param("to", to))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).get("staff");
    }

    private JsonNode staffSales() throws Exception {
        return staffSales(sarah, yesterday(), tomorrow());
    }

    private JsonNode rowOf(JsonNode rows, Staff who) {
        for (JsonNode row : rows) {
            if (row.get("staffId").asLong() == who.getStaffId()) {
                return row;
            }
        }
        return fail(who.getName() + " has no row in the answer");
    }

    private List<String> namesIn(JsonNode rows) {
        List<String> names = new ArrayList<>();
        for (JsonNode row : rows) {
            names.add(row.get("name").asString());
        }
        return names;
    }

    // Money is compared as an amount: 40.0 and 40.00 are the same money
    private void assertMoney(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()),
                "expected " + expected + " but the answer holds " + actual);
    }

    // ------------------------------------------------------------------
    // The two reports agree
    // ------------------------------------------------------------------

    @Test
    void testEveryonesSales_PlusTheChecksWithNobodyOnRecord_EqualFinancialsRevenue() throws Exception {
        // WHAT: Add up the leaderboard and compare it with the Financials report
        // WHY: They are the same sales seen two ways. A manager who adds up the
        //      leaderboard on a calculator must get the number on the Financials screen.

        // Given - a night with every kind of check in it:
        sell(sarah, of(burger, 2), of(soda, 1));                    // 43.50, recorded when paid
        sell(sarah, of(soda, 3));                                   // 10.50
        sell(dan, of(burger, 1), of(soda, 2));                      // 27.00
        long takenOver = openAndSend(dan, of(burger, 1));           // 20.00: Dan opens it, Sarah takes the payment
        pay(takenOver, sarah, "2.00");
        // ...one paid before the backend recorded amounts: it holds zeros, and is worth its items (2 x 20.00)
        completedItemOn(closedCheck(sarahRow.getStaffId(), "Sarah", "0.00", "0.00", "0.00"), burger, 2);
        // ...and one closed before staff profiles existed: nobody is on record for its 33.33
        closedCheck(null, "CJ", "33.33", "1.00", "34.33");
        openAndSend(sarah, of(burger, 5));                          // Still open: not a sale yet, for either report

        // When - both reports, same period
        JsonNode rows = staffSales(morgan, yesterday(), tomorrow());
        JsonNode financials = read(mockMvc.perform(get("/api/reports/financials").with(morgan)
                        .param("from", yesterday()).param("to", tomorrow()))
                .andExpect(status().isOk())
                .andReturn());

        // Then - everyone's sales...
        BigDecimal everyonesSales = BigDecimal.ZERO;
        int everyonesChecks = 0;
        for (JsonNode row : rows) {
            everyonesSales = everyonesSales.add(row.get("sales").decimalValue());
            everyonesChecks += row.get("checksClosed").asInt();
        }
        assertEquals(0, new BigDecimal("141.00").compareTo(everyonesSales), "everyone's sales came to " + everyonesSales);

        // ...plus the check with nobody on record, is Financials' revenue, to the cent
        BigDecimal nobodys = new BigDecimal("33.33");
        assertEquals(0, financials.get("revenue").decimalValue().compareTo(everyonesSales.add(nobodys)),
                "Financials says " + financials.get("revenue") + ", the leaderboard plus nobody's checks says " + everyonesSales.add(nobodys));
        assertEquals(financials.get("orderCount").asInt(), everyonesChecks + 1);
    }

    // ------------------------------------------------------------------
    // One person's row
    // ------------------------------------------------------------------

    @Test
    void testARow_HoldsThatPersonsChecksSalesAverageBiggestCheckAndItems() throws Exception {
        // WHAT: Test the five numbers on a row, from checks sold through the real endpoints
        // WHY: These are what the screen prints, and what challenges will be measured by

        // Given - Sarah sells three checks: 43.50, 10.50, and 20.00
        sell(sarah, of(burger, 2), of(soda, 1));
        sell(sarah, of(soda, 3));
        sell(sarah, of(burger, 1));

        // When
        JsonNode row = rowOf(staffSales(), sarahRow);

        // Then
        assertEquals("Sarah", row.get("name").asString());
        assertEquals(1, row.get("rank").asInt());
        assertEquals(3, row.get("checksClosed").asInt());
        assertMoney("74.00", row.get("sales"));                     // Before tax: what Financials calls revenue
        assertMoney("24.67", row.get("checkAverage"));              // 74.00 / 3 = 24.666..., to the cent
        assertMoney("43.50", row.get("biggestCheck"));
        assertEquals(7, row.get("itemsSold").asInt());              // 2 + 1, 3, 1: counted by quantity, not by line
    }

    @Test
    void testASale_BelongsToWhoeverOpenedTheCheck_NotWhoeverTookThePayment() throws Exception {
        // WHAT: Test a check Dan opened and Sarah closed
        // WHY: One rule for whose a check is, the same one tips follow (D10): the opener

        // Given
        long orderId = openAndSend(dan, of(burger, 1));
        pay(orderId, sarah, "4.00");

        // When
        JsonNode rows = staffSales();

        // Then - it is Dan's sale, and Sarah, who sold nothing of her own, has no row
        assertEquals(List.of("Dan"), namesIn(rows));
        assertMoney("20.00", rowOf(rows, danRow).get("sales"));
    }

    @Test
    void testRanks_BestSalesFirst_AndEqualSalesShareARank() throws Exception {
        // WHAT: Test the order and the ranks, with two people on exactly the same sales
        // WHY: Two people tied for first are both first, and the next is third

        // Given - Dan and Sarah both sell 40.00 (as different checks), Morgan 3.50
        sell(dan, of(burger, 2));
        sell(sarah, of(burger, 1));
        sell(sarah, of(burger, 1));
        sell(morgan, of(soda, 1));

        // When
        JsonNode rows = staffSales();

        // Then - the tied two are listed by name
        assertEquals(List.of("Dan", "Sarah", "Morgan"), namesIn(rows));
        assertEquals(1, rows.get(0).get("rank").asInt());
        assertEquals(1, rows.get(1).get("rank").asInt());
        assertEquals(3, rows.get(2).get("rank").asInt());
    }

    @Test
    void testSomeoneDeactivatedSince_StillAppearsForAPeriodTheySoldIn() throws Exception {
        // WHAT: Test a person a manager deactivated after they closed a check
        // WHY: Their sales happened, and Financials still counts them

        // Given
        sell(dan, of(burger, 1));
        danRow.setIsActive(false);
        staffRepository.save(danRow);

        // When & Then
        assertMoney("20.00", rowOf(staffSales(), danRow).get("sales"));
    }

    // ------------------------------------------------------------------
    // Which checks count
    // ------------------------------------------------------------------

    @Test
    void testASale_BelongsToThePeriodItsCheckWasClosedIn() throws Exception {
        // WHAT: Test a check opened three days ago and closed two days ago
        // WHY: "Today" must not include it, and a period that holds its closing must

        // Given - one check closed just now, and one moved two days back
        sell(sarah, of(soda, 1));
        long earlier = sell(sarah, of(burger, 1));
        jdbc.update("UPDATE orders SET created_at = created_at - INTERVAL '3 days', closed_at = closed_at - INTERVAL '2 days' WHERE order_id = ?", earlier);

        // When & Then - the last day holds only the soda
        JsonNode recent = rowOf(staffSales(), sarahRow);
        assertEquals(1, recent.get("checksClosed").asInt());
        assertMoney("3.50", recent.get("sales"));

        // ...the day the burger was closed in holds only the burger
        JsonNode thatDay = rowOf(staffSales(sarah, LocalDateTime.now().minusDays(3).format(LOCAL), LocalDateTime.now().minusDays(1).format(LOCAL)), sarahRow);
        assertEquals(1, thatDay.get("checksClosed").asInt());
        assertMoney("20.00", thatDay.get("sales"));

        // ...and a week holds both
        assertMoney("23.50", rowOf(staffSales(sarah, LocalDateTime.now().minusDays(7).format(LOCAL), tomorrow()), sarahRow).get("sales"));
    }

    @Test
    void testAnOpenCheck_IsNotASaleYet() throws Exception {
        // WHAT: Test a check that has been sent to the kitchen and not paid
        // WHY: A sale is a closed check. Until it is paid it can still be changed or cleared.

        // Given
        openAndSend(sarah, of(burger, 3));

        // When & Then
        assertEquals(0, staffSales().size());
    }

    @Test
    void testWhenNobodySoldAnything_TheListIsEmpty() throws Exception {
        // WHAT: Test a period with no closed checks
        // WHY: The screen says "No sales yet for this period." from an empty list

        MvcResult result = mockMvc.perform(get("/api/reports/staff-sales").with(sarah)
                        .param("from", yesterday()).param("to", tomorrow()))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals("{\"staff\":[]}", result.getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------
    // Who may read it, and what is not in it
    // ------------------------------------------------------------------

    @Test
    void testEveryRole_ReadsTheSameRows() throws Exception {
        // WHAT: Test the answer as a server, the kitchen, and a manager
        // WHY: The owner's decision D6: everyone sees everyone's name, rank, and sales.
        //      Nothing in it depends on who is asking.

        // Given
        sell(sarah, of(burger, 1));
        sell(dan, of(soda, 2));

        // When
        JsonNode asServer = staffSales(sarah, yesterday(), tomorrow());
        JsonNode asKitchen = staffSales(kit, yesterday(), tomorrow());
        JsonNode asManager = staffSales(morgan, yesterday(), tomorrow());

        // Then
        assertEquals(2, asServer.size());
        assertEquals(asServer, asKitchen);
        assertEquals(asServer, asManager);
    }

    @Test
    void testNoTipIsInTheAnswer_ForAnybody() throws Exception {
        // WHAT: Read the raw answer and look for a tip, as a server and as a manager
        // WHY: A tip is shown only to the person who earned it and to managers (D10), and
        //      this answer goes to everyone. Not even a manager is sent one here: there is
        //      no field for it. The 77.77 tip below is an amount no sale in this test has.

        // Given
        long orderId = openAndSend(dan, of(burger, 1));
        pay(orderId, dan, "77.77");

        for (RequestPostProcessor reader : List.of(sarah, dan, morgan)) {
            // When
            String raw = mockMvc.perform(get("/api/reports/staff-sales").with(reader)
                            .param("from", yesterday()).param("to", tomorrow()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // Then
            assertFalse(raw.toLowerCase().contains("tip"), "the answer mentions a tip: " + raw);
            assertFalse(raw.contains("77.77"), "the answer holds the tip's amount: " + raw);
            assertTrue(raw.contains("\"sales\":20.00"), "the sale itself should be there: " + raw);
        }
    }

    @Test
    void testWithNobodySignedIn_TheReportIsRefused() throws Exception {
        // WHAT: Test the endpoint with no sign-in
        // WHY: Everyone on the staff, not everyone on the network

        mockMvc.perform(get("/api/reports/staff-sales")
                        .param("from", yesterday()).param("to", tomorrow()))
                .andExpect(status().isUnauthorized());
    }
}
