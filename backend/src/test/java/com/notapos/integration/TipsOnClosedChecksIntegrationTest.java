package com.notapos.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notapos.entity.MenuItem;
import com.notapos.entity.Order;
import com.notapos.entity.Payment;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for who sees the tip on a closed check (step A6 of the
 * Phase 2 plan, the owner's decision D10).
 *
 * THE RULE: a person sees the tip and the payment method on checks THEY
 * opened. A manager sees them on every check. Everyone still sees that a check
 * was closed, what was on it, and its total.
 *
 * WHAT WAS WRONG BEFORE: GET /api/tables/overview gave every closed check's tip
 * to anyone signed in, so the Tables screen showed a host or a cook what each
 * server was tipped.
 *
 * WHY HERE AND NOT ONLY ON THE SCREEN: hiding the number on the screen would
 * still send it to the device, where anyone can read it in the browser's
 * network tab. These tests read the raw answer, the way that person would, and
 * check the two fields are NOT IN IT at all. (A field that is present with the
 * value null or 0 would still say something: "no tip" is information too.)
 *
 * "Opened" means the order's staff_id, which the backend stamps from the
 * signed-in person when the order is created.
 *
 * The first version of these tests was written before the fix and failed.
 *
 * @author CJ
 */
class TipsOnClosedChecksIntegrationTest extends BaseIntegrationTest {

    @Autowired private TableRepository tableRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderItemModifierRepository orderItemModifierRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private ObjectMapper objectMapper;

    // The people on the floor. Each is one sign-in, used for every request that person makes.
    private RequestPostProcessor sarah;         // A server
    private RequestPostProcessor dan;           // A bartender
    private RequestPostProcessor morgan;        // A manager

    private MenuItem burger;                    // 20.00, so a check for one is 20.60 with 3% tax

    private Long sarahsCheck;                   // Opened by Sarah, paid with a 5.00 tip
    private Long dansCheck;                     // Opened by Dan, paid with a 4.00 tip

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

        burger = new MenuItem();
        burger.setName("Burger");
        burger.setPrice(new BigDecimal("20.00"));
        burger.setCategory("Savory");
        burger.setIsActive(true);
        burger = menuItemRepository.save(burger);

        sarahsCheck = openSendAndPay(table("W1", false), sarah, sarah, "5.00", "credit_card");
        dansCheck = openSendAndPay(table("W2", false), dan, dan, "4.00", "cash");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private RestaurantTable table(String number, boolean quickOrder) {
        RestaurantTable table = new RestaurantTable();
        table.setTableNumber(number);
        table.setSeatCount(2);
        table.setStatus("available");
        table.setIsQuickOrder(quickOrder);
        return tableRepository.save(table);
    }

    // A whole check through the real endpoints: one person opens it and rings in a burger,
    // and someone (the same person or another) takes the payment with a tip.
    private Long openSendAndPay(RestaurantTable table, RequestPostProcessor openedBy, RequestPostProcessor paidBy,
                                String tip, String method) throws Exception {
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

        mockMvc.perform(post("/api/payments").with(paidBy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"amount\":20.60,\"tipAmount\":" + tip
                                + ",\"paymentMethod\":\"" + method + "\"}"))
                .andExpect(status().isCreated());
        return orderId;
    }

    // A check closed before staff profiles existed: nobody is recorded as having opened it
    private Long checkWithNobodyOnRecord(RestaurantTable table) {
        Order order = new Order();
        order.setTableId(table.getTableId());
        order.setStatus("closed");
        order.setServerName("CJ");
        order.setClosedAt(LocalDateTime.now());
        order.setSubtotal(new BigDecimal("20.00"));
        order.setTax(new BigDecimal("0.60"));
        order.setTotal(new BigDecimal("20.60"));
        order = orderRepository.save(order);

        Payment payment = new Payment();
        payment.setOrderId(order.getOrderId());
        payment.setAmount(new BigDecimal("20.60"));
        payment.setTipAmount(new BigDecimal("3.00"));
        payment.setPaymentMethod("cash");
        paymentRepository.save(payment);
        return order.getOrderId();
    }

    // One closed check as a given person is sent it: the raw JSON, so a test can ask
    // whether a field is there at all
    private JsonNode closedCheckAsSeenBy(RequestPostProcessor who, Long orderId, boolean quickOrders) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/tables/overview")
                        .param("quickOrders", String.valueOf(quickOrders)).with(who))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode checks = objectMapper.readTree(result.getResponse().getContentAsString()).get("closedChecks");
        for (JsonNode check : checks) {
            if (check.get("orderId").asLong() == orderId) {
                return check;
            }
        }
        return fail("Closed check " + orderId + " is not in the answer at all");
    }

    private JsonNode closedCheckAsSeenBy(RequestPostProcessor who, Long orderId) throws Exception {
        return closedCheckAsSeenBy(who, orderId, false);
    }

    private static void assertTipShown(JsonNode check, String tip, String method) {
        assertTrue(check.has("tipAmount"), "the tip should be in the answer");
        assertEquals(0, new BigDecimal(tip).compareTo(check.get("tipAmount").decimalValue()));
        assertEquals(method, check.get("paymentMethod").asText());
    }

    private static void assertTipLeftOut(JsonNode check) {
        assertFalse(check.has("tipAmount"), "the tip must not be in the answer, not even as null or 0");
        assertFalse(check.has("paymentMethod"), "the payment method must not be in the answer");
    }

    // ------------------------------------------------------------------
    // The rule
    // ------------------------------------------------------------------

    @Test
    void testAServer_ShouldSeeTheTipOnTheirOwnCheckAndNobodyElses() throws Exception {
        // WHAT: Sarah reads the Tables screen's closed checks
        // WHY: The rule itself. Her own tip is hers to know; Dan's is not.

        assertTipShown(closedCheckAsSeenBy(sarah, sarahsCheck), "5.00", "credit_card");
        assertTipLeftOut(closedCheckAsSeenBy(sarah, dansCheck));

        // And the other way round
        assertTipShown(closedCheckAsSeenBy(dan, dansCheck), "4.00", "cash");
        assertTipLeftOut(closedCheckAsSeenBy(dan, sarahsCheck));
    }

    @Test
    void testEveryone_ShouldStillSeeThatACheckWasClosedAndItsTotal() throws Exception {
        // WHAT: What is left of somebody else's check once the tip is taken out
        // WHY: The Tables screen is how the floor sees that a table has paid. Only the tip
        //      and how it was paid are private; the check itself is not.

        JsonNode check = closedCheckAsSeenBy(sarah, dansCheck);

        assertEquals("W2", check.get("tableNumber").asText());
        assertEquals("Dan", check.get("serverName").asText());
        assertEquals(0, new BigDecimal("20.00").compareTo(check.get("subtotal").decimalValue()));
        assertEquals(0, new BigDecimal("0.60").compareTo(check.get("tax").decimalValue()));
        assertEquals(0, new BigDecimal("20.60").compareTo(check.get("total").decimalValue()));
        assertEquals("Burger", check.get("items").get(0).get("name").asText());
        assertFalse(check.get("closedAt").isNull());
    }

    @Test
    void testAManager_ShouldSeeTheTipOnEveryCheck() throws Exception {
        // WHAT: Morgan, who opened neither check, reads both
        // WHY: A manager settles the night and answers a guest who questions a charge

        assertTipShown(closedCheckAsSeenBy(morgan, sarahsCheck), "5.00", "credit_card");
        assertTipShown(closedCheckAsSeenBy(morgan, dansCheck), "4.00", "cash");
    }

    @Test
    void testSomeoneWhoOpensNoChecks_ShouldSeeNoTips() throws Exception {
        // WHAT: The kitchen and a host read the closed checks
        // WHY: This is who the defect showed tips to. Neither opened either check.

        for (RequestPostProcessor who : new RequestPostProcessor[]{signIn.asKitchen(), signIn.asHost()}) {
            assertTipLeftOut(closedCheckAsSeenBy(who, sarahsCheck));
            assertTipLeftOut(closedCheckAsSeenBy(who, dansCheck));
        }
    }

    @Test
    void testTakingThePaymentOnSomeoneElsesCheck_ShouldNotShowItsTip() throws Exception {
        // WHAT: Sarah opens a check; Dan takes the payment for her while she is busy
        // WHY: "Opened", not "took the payment", decides whose check it is. The tip was
        //      left for the person who looked after the table.

        Long covered = openSendAndPay(table("W3", false), sarah, dan, "6.00", "credit_card");

        assertTipShown(closedCheckAsSeenBy(sarah, covered), "6.00", "credit_card");
        assertTipLeftOut(closedCheckAsSeenBy(dan, covered));
    }

    @Test
    void testACheckWithNobodyOnRecord_ShouldShowItsTipToManagersOnly() throws Exception {
        // WHAT: A check closed before staff profiles existed (its staff_id is empty)
        // WHY: Nobody can be shown to have opened it, so it is nobody's own check. The
        //      safe answer is the manager's eyes only, not everyone's.

        Long old = checkWithNobodyOnRecord(table("W4", false));

        assertTipLeftOut(closedCheckAsSeenBy(sarah, old));
        assertTipLeftOut(closedCheckAsSeenBy(dan, old));
        assertTipShown(closedCheckAsSeenBy(morgan, old), "3.00", "cash");
    }

    @Test
    void testQuickOrders_ShouldFollowTheSameRule() throws Exception {
        // WHAT: The same question asked of the Quick Orders screen
        // WHY: Both screens are answered by this one endpoint; a rule applied to only one
        //      of its two questions would be half a rule

        Long quick = openSendAndPay(table("QO1", true), dan, dan, "2.00", "cash");

        assertTipShown(closedCheckAsSeenBy(dan, quick, true), "2.00", "cash");
        assertTipLeftOut(closedCheckAsSeenBy(sarah, quick, true));
        assertTipShown(closedCheckAsSeenBy(morgan, quick, true), "2.00", "cash");
    }
}
