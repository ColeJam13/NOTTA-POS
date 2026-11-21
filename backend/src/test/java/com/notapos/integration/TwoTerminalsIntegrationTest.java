package com.notapos.integration;

import com.notapos.entity.MenuItem;
import com.notapos.entity.Order;
import com.notapos.entity.OrderItem;
import com.notapos.entity.RestaurantTable;
import com.notapos.repository.MenuItemRepository;
import com.notapos.repository.OrderItemModifierRepository;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
import com.notapos.entity.Payment;
import com.notapos.repository.PaymentRepository;
import com.notapos.repository.TableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for two terminals acting on the same check at the same moment.
 *
 * WHAT: Each test fires two real requests at the backend from two threads,
 *       released at the same instant, many times over, and then checks what
 *       ended up in the database.
 * WHY:  Code that reads "is this order open?" and then acts on the answer is
 *       correct for one terminal. With two, both can read "open" before either
 *       writes, and both go ahead: a check paid twice, a table with two open
 *       checks, an item added to a check a moment after it was paid. No amount
 *       of single-request testing finds these, because each request on its
 *       own does the right thing.
 *
 * These races do not happen on every attempt, so each test repeats the race
 * ROUNDS times. Before the fix, at least one round went wrong on nearly every run.
 *
 * @author CJ
 */
class TwoTerminalsIntegrationTest extends BaseIntegrationTest {

    // How many times each race is run
    private static final int ROUNDS = 20;

    @Autowired private TableRepository tableRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderItemModifierRepository orderItemModifierRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private MenuItemRepository menuItemRepository;

    private MenuItem chickenCutty;      // $17.00, no modifiers

    @BeforeEach
    void setUp() {
        // Clean every table these tests touch (nothing rolls back by itself here)
        orderItemModifierRepository.deleteAll();
        orderItemRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        menuItemRepository.deleteAll();
        tableRepository.deleteAll();

        chickenCutty = new MenuItem();
        chickenCutty.setName("Chicken Cutty");
        chickenCutty.setPrice(new BigDecimal("17.00"));
        chickenCutty.setCategory("Savory");
        chickenCutty.setIsActive(true);
        chickenCutty = menuItemRepository.save(chickenCutty);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private RestaurantTable table(String number, String status) {
        RestaurantTable table = new RestaurantTable();
        table.setTableNumber(number);
        table.setSeatCount(2);
        table.setStatus(status);
        return tableRepository.save(table);
    }

    // An open order on its own table, with one $17.00 item the kitchen already has
    private Order openOrderWithOneSentItem(String tableNumber) {
        RestaurantTable table = table(tableNumber, "occupied");

        Order order = new Order();
        order.setTableId(table.getTableId());
        order.setStatus("open");
        order = orderRepository.save(order);

        OrderItem item = new OrderItem();
        item.setOrderId(order.getOrderId());
        item.setMenuItemId(chickenCutty.getMenuItemId());
        item.setQuantity(1);
        item.setPrice(new BigDecimal("17.00"));
        item.setStatus("pending");
        item.setIsLocked(true);
        item.setSentAt(LocalDateTime.now());
        orderItemRepository.save(item);
        return order;
    }

    // The request a terminal sends to pay that order's check: 17.00 + 3% tax = 17.51
    private Callable<Integer> pay(Order order) {
        return () -> mockMvc.perform(post("/api/payments").with(signIn.asServer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + order.getOrderId() + ",\"amount\":17.51,\"tipAmount\":0,\"paymentMethod\":\"cash\"}"))
                .andReturn().getResponse().getStatus();
    }

    // The request a terminal sends to ring one more Chicken Cutty onto that order
    private Callable<Integer> ringIn(Order order) {
        return () -> mockMvc.perform(post("/api/order-items").with(signIn.asServer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + order.getOrderId() + ",\"menuItemId\":" + chickenCutty.getMenuItemId()
                        + ",\"quantity\":1,\"modifierIds\":[]}"))
                .andReturn().getResponse().getStatus();
    }

    // The request a terminal sends to remove an item that is still in its edit window
    private Callable<Integer> remove(OrderItem item) {
        return () -> mockMvc.perform(delete("/api/order-items/" + item.getOrderItemId()).with(signIn.asServer()))
                .andReturn().getResponse().getStatus();
    }

    // The request a terminal sends to open an order on a table
    private Callable<Integer> openOrderOn(RestaurantTable table) {
        return () -> mockMvc.perform(post("/api/orders").with(signIn.asServer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":" + table.getTableId() + ",\"orderType\":\"dine_in\",\"serverName\":\"CJ\"}"))
                .andReturn().getResponse().getStatus();
    }

    /**
     * Run two requests at the same instant, as two terminals would, and return
     * the HTTP status each one got.
     *
     * A CyclicBarrier is a starting gate: each thread waits at it until both
     * have arrived, then both are released together. Without it the first
     * request would usually finish before the second had started, and there
     * would be no race to test.
     */
    private List<Integer> atTheSameMoment(Callable<Integer> first, Callable<Integer> second) throws Exception {
        ExecutorService terminals = Executors.newFixedThreadPool(2);
        CyclicBarrier startingGate = new CyclicBarrier(2);
        try {
            List<Future<Integer>> answers = new ArrayList<>();
            for (Callable<Integer> request : List.of(first, second)) {
                answers.add(terminals.submit(() -> {
                    startingGate.await(5, TimeUnit.SECONDS);
                    return request.call();
                }));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> answer : answers) {
                statuses.add(answer.get(20, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            terminals.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // Two payments for one check
    // ------------------------------------------------------------------

    @Test
    void testTwoPaymentsAtTheSameMoment_ShouldTakeExactlyOne() throws Exception {
        // WHAT: Two terminals press PROCESS PAYMENT for the same check at the same instant
        // WHY: Both used to read the order as open and both saved a payment. The guest was
        //      charged twice and the day's takings were overstated.

        for (int round = 0; round < ROUNDS; round++) {
            // Given
            Order order = openOrderWithOneSentItem("W" + round);

            // When
            List<Integer> statuses = atTheSameMoment(pay(order), pay(order));

            // Then - one payment went through, the other was refused (409), and one payment row exists
            long accepted = statuses.stream().filter(status -> status == 201).count();
            long refused = statuses.stream().filter(status -> status == 409).count();
            assertEquals(1, accepted, "round " + round + ": statuses were " + statuses);
            assertEquals(1, refused, "round " + round + ": statuses were " + statuses);
            assertEquals(1, paymentRepository.findByOrderId(order.getOrderId()).size(), "round " + round);
            assertEquals("closed", orderRepository.findById(order.getOrderId()).orElseThrow().getStatus());
        }
    }

    @Test
    void testPayingACheckThatWasAlreadyPaid_ShouldAnswerWithTheClosedCheckCode() throws Exception {
        // WHAT: Test what the terminal that lost the race is told
        // WHY: "409" alone does not tell a screen what to do next. The code ORDER_NOT_OPEN
        //      does: the payment window closes and the table is reloaded, the same way the
        //      order screen already reacts when it tries to add to a closed check.

        // Given - a check that has just been paid
        Order order = openOrderWithOneSentItem("W1");
        assertEquals(201, pay(order).call());

        // When & Then - the same payment again
        mockMvc.perform(post("/api/payments").with(signIn.asServer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + order.getOrderId() + ",\"amount\":17.51,\"tipAmount\":0,\"paymentMethod\":\"cash\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_OPEN"))
                .andExpect(jsonPath("$.message").value("This check was already closed, most likely on another terminal. Nothing was charged."));
        assertEquals(1, paymentRepository.findByOrderId(order.getOrderId()).size());
    }

    // ------------------------------------------------------------------
    // Two terminals open the same table
    // ------------------------------------------------------------------

    @Test
    void testTwoOpensOnOneTableAtTheSameMoment_ShouldMakeOneOrder() throws Exception {
        // WHAT: Two servers tap the same free table and press Send at the same instant
        // WHY: The rule "a table has one open check" lived only in service code that read
        //      first and wrote second, so both could open one. The table then had two
        //      checks, and paying one freed the table while the other was still open.

        for (int round = 0; round < ROUNDS; round++) {
            // Given
            RestaurantTable table = table("W" + round, "available");

            // When
            List<Integer> statuses = atTheSameMoment(openOrderOn(table), openOrderOn(table));

            // Then - both terminals got an order back, and it is the same single order
            assertEquals(List.of(201, 201), statuses, "round " + round);
            assertEquals(1, orderRepository.findByTableIdAndStatus(table.getTableId(), "open").size(), "round " + round);
            assertEquals("occupied", tableRepository.findById(table.getTableId()).orElseThrow().getStatus());
        }
    }

    // ------------------------------------------------------------------
    // A ring-in racing a payment
    // ------------------------------------------------------------------

    @Test
    void testRingInRacingAPayment_ShouldNeverLeaveAnItemOnAPaidCheck() throws Exception {
        // WHAT: One terminal pays a check at the same instant another rings an item onto it
        // WHY: A1 made ring-in check "is the order open?" first. But checking and then
        //      saving are two steps: the payment could land between them, and the item
        //      ended up on a check that had just been paid - never billed, never cooked.
        //
        // Either order of events is fine. What must never happen is both succeeding.

        for (int round = 0; round < ROUNDS; round++) {
            // Given
            Order order = openOrderWithOneSentItem("W" + round);

            // When
            List<Integer> statuses = atTheSameMoment(pay(order), ringIn(order));
            int payStatus = statuses.get(0);
            int ringInStatus = statuses.get(1);

            // Then
            Order after = orderRepository.findById(order.getOrderId()).orElseThrow();
            int itemsOnOrder = orderItemRepository.findByOrderId(order.getOrderId()).size();

            assertFalse(payStatus == 201 && ringInStatus == 201,
                    "round " + round + ": the payment AND the ring-in both went through");

            if (payStatus == 201) {
                // The payment won: the check is closed with exactly what was paid for
                assertEquals("closed", after.getStatus(), "round " + round);
                assertEquals(1, itemsOnOrder, "round " + round + ": an item was added to a paid check");
                assertEquals(409, ringInStatus, "round " + round);
            } else {
                // The ring-in won: the check is still open with both items, and nothing was charged
                assertEquals(201, ringInStatus, "round " + round + ": statuses were " + statuses);
                assertEquals("open", after.getStatus(), "round " + round);
                assertEquals(2, itemsOnOrder, "round " + round);
                assertEquals(0, paymentRepository.findByOrderId(order.getOrderId()).size(), "round " + round);
            }
        }
    }

    // ------------------------------------------------------------------
    // Removing an item racing a payment
    // ------------------------------------------------------------------

    @Test
    void testRemovingAnItemRacingAPayment_ShouldNeverChargeForAnItemThatWasRemoved() throws Exception {
        // WHAT: One terminal pays a check at the same instant another removes an item that
        //       is still in its edit window
        // WHY: The payment adds up the check and the removal deletes a line from it. If both
        //      go through, the guest has paid for a dish that is no longer on the check and
        //      will never be made.
        //
        // Either order of events is fine. What must never happen is both succeeding.

        for (int round = 0; round < ROUNDS; round++) {
            // Given - one item the kitchen has, and a second one still counting down
            Order order = openOrderWithOneSentItem("W" + round);
            OrderItem inEditWindow = new OrderItem();
            inEditWindow.setOrderId(order.getOrderId());
            inEditWindow.setMenuItemId(chickenCutty.getMenuItemId());
            inEditWindow.setQuantity(1);
            inEditWindow.setPrice(new BigDecimal("17.00"));
            inEditWindow.setStatus("limbo");
            inEditWindow.setIsLocked(false);
            inEditWindow.setSentAt(LocalDateTime.now());
            inEditWindow.setDelayExpiresAt(LocalDateTime.now().plusSeconds(16));
            OrderItem saved = orderItemRepository.save(inEditWindow);

            // The check for both items: 34.00 + 3% tax = 35.02
            Callable<Integer> payForBoth = () -> mockMvc.perform(post("/api/payments").with(signIn.asServer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"orderId\":" + order.getOrderId() + ",\"amount\":35.02,\"tipAmount\":0,\"paymentMethod\":\"cash\"}"))
                    .andReturn().getResponse().getStatus();

            // When
            List<Integer> statuses = atTheSameMoment(payForBoth, remove(saved));
            int payStatus = statuses.get(0);
            int removeStatus = statuses.get(1);

            // Then
            int itemsOnOrder = orderItemRepository.findByOrderId(order.getOrderId()).size();
            assertFalse(payStatus == 201 && removeStatus == 204,
                    "round " + round + ": the payment AND the removal both went through");

            if (payStatus == 201) {
                // The payment won: both items are on the paid check, and the removal was refused
                assertEquals(409, removeStatus, "round " + round);
                assertEquals(2, itemsOnOrder, "round " + round + ": an item was removed from a paid check");
            } else {
                // The removal won: the payment was for an old total, so it was refused
                assertEquals(204, removeStatus, "round " + round + ": statuses were " + statuses);
                assertEquals(409, payStatus, "round " + round);
                assertEquals(1, itemsOnOrder, "round " + round);
                assertEquals(0, paymentRepository.findByOrderId(order.getOrderId()).size(), "round " + round);
            }
        }
    }

    // ------------------------------------------------------------------
    // Opening an order must never touch an existing one
    // ------------------------------------------------------------------

    @Test
    void testOpenOrder_WithTheIdOfAClosedCheck_ShouldNotReopenIt() throws Exception {
        // WHAT: POST /api/orders with an "orderId" in the body that belongs to a paid check
        // WHY: "Save" on an entity that carries an ID means "update that row". A request
        //      that named an existing order was overwriting it, status and all: a paid
        //      check could be turned back into an open one.

        // Given - a check that was paid
        Order paid = openOrderWithOneSentItem("W1");
        mockMvc.perform(post("/api/payments").with(signIn.asServer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + paid.getOrderId() + ",\"amount\":17.51,\"tipAmount\":0,\"paymentMethod\":\"cash\"}"));
        assertEquals("closed", orderRepository.findById(paid.getOrderId()).orElseThrow().getStatus());

        // When - a new order is "opened" on the same table, naming the paid check's ID
        mockMvc.perform(post("/api/orders").with(signIn.asServer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + paid.getOrderId() + ",\"tableId\":" + paid.getTableId()
                        + ",\"orderType\":\"dine_in\",\"serverName\":\"CJ\"}"));

        // Then - the paid check is still closed with its totals, and a separate new order was opened
        Order stillPaid = orderRepository.findById(paid.getOrderId()).orElseThrow();
        assertEquals("closed", stillPaid.getStatus());
        assertEquals(0, new BigDecimal("17.51").compareTo(stillPaid.getTotal()));
        assertEquals(2, orderRepository.findByTableId(paid.getTableId()).size());
    }

    @Test
    void testPayment_WithTheIdOfAnEarlierPayment_ShouldNotOverwriteIt() throws Exception {
        // WHAT: POST /api/payments with a "paymentId" in the body that belongs to another check's payment
        // WHY: The same defect as the test above, in the payment endpoint. A request that
        //      named an existing payment replaced it: that payment's row now pointed at the
        //      new check, and the first check was left closed with no payment on record.
        //      Taking a payment always adds one.

        // Given - one check paid by card with a tip, and a second check still open
        Order first = openOrderWithOneSentItem("W1");
        mockMvc.perform(post("/api/payments").with(signIn.asServer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + first.getOrderId() + ",\"amount\":17.51,\"tipAmount\":5.00,\"paymentMethod\":\"credit_card\"}"))
                .andExpect(status().isCreated());
        Payment firstPayment = paymentRepository.findByOrderId(first.getOrderId()).get(0);

        Order second = openOrderWithOneSentItem("W2");

        // When - the second check is paid in cash, naming the first payment's ID
        mockMvc.perform(post("/api/payments").with(signIn.asServer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentId\":" + firstPayment.getPaymentId() + ",\"orderId\":" + second.getOrderId()
                        + ",\"amount\":17.51,\"tipAmount\":0,\"paymentMethod\":\"cash\"}"))
                .andExpect(status().isCreated());

        // Then - both payments exist, and the first is exactly as it was
        assertEquals(2, paymentRepository.count());
        Payment stillThere = paymentRepository.findById(firstPayment.getPaymentId()).orElseThrow();
        assertEquals(first.getOrderId(), stillThere.getOrderId());
        assertEquals("credit_card", stillThere.getPaymentMethod());
        assertEquals(0, new BigDecimal("5.00").compareTo(stillThere.getTipAmount()));
        assertEquals(1, paymentRepository.findByOrderId(second.getOrderId()).size());
    }

    // ------------------------------------------------------------------
    // The version number: a save from an out-of-date copy is refused
    // ------------------------------------------------------------------

    @Test
    void testSavingAnOrderFromAnOutOfDateCopy_ShouldBeRefused() {
        // WHAT: Two copies of the same order are read, then both are saved
        // WHY: This is the general safety net under the locks above. Any code, now or
        //      later, that reads an order, changes it, and saves it, cannot silently
        //      overwrite a change made by another terminal in between: the second save
        //      started from version 0, the row is at version 1, and the database layer
        //      refuses it. (GlobalExceptionHandler turns that into a 409.)

        // Given - the same order read twice, as two terminals would have it
        Order order = openOrderWithOneSentItem("W1");
        Order copyOnTerminalA = orderRepository.findById(order.getOrderId()).orElseThrow();
        Order copyOnTerminalB = orderRepository.findById(order.getOrderId()).orElseThrow();

        // When - A saves first
        copyOnTerminalA.setServerName("Sarah");
        orderRepository.save(copyOnTerminalA);

        // Then - B's save, from the copy it read before A's change, is refused...
        copyOnTerminalB.setServerName("Morgan");
        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class,
                () -> orderRepository.save(copyOnTerminalB));

        // ...and A's change is what the database holds
        Order after = orderRepository.findById(order.getOrderId()).orElseThrow();
        assertEquals("Sarah", after.getServerName());
        assertEquals(1L, after.getVersion());
    }
}
