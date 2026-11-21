package com.notapos.service;

import com.notapos.dto.ActiveTableView;
import com.notapos.dto.ClosedCheckView;
import com.notapos.dto.OrderCheckView;
import com.notapos.dto.OrderItemModifierView;
import com.notapos.dto.TablesOverviewView;
import com.notapos.entity.MenuItem;
import com.notapos.entity.Order;
import com.notapos.entity.OrderItem;
import com.notapos.entity.Payment;
import com.notapos.entity.RestaurantTable;
import com.notapos.repository.MenuItemRepository;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.PaymentRepository;
import com.notapos.security.StaffSession;
import com.notapos.enums.StaffRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TableOverviewService.
 *
 * Tests the single response behind the Tables and Quick Orders screens.
 *
 * @author CJ
 */

@ExtendWith(MockitoExtension.class)
class TableOverviewServiceTest {

    @Mock
    private TableService tableService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private OrderItemModifierService orderItemModifierService;

    @Mock
    private OrderCheckService orderCheckService;

    @InjectMocks
    private TableOverviewService tableOverviewService;

    // Who is asking. A manager sees every tip, so the tests about how a check is put
    // together ask as one; the tests about who sees a tip are at the end of the file.
    private static final StaffSession MANAGER = new StaffSession(2L, "Morgan", StaffRole.MANAGER, Instant.now(), false);
    private static final StaffSession SARAH = new StaffSession(7L, "Sarah", StaffRole.SERVER, Instant.now(), false);

    private RestaurantTable table(Long id, String number) {
        RestaurantTable table = new RestaurantTable();
        table.setTableId(id);
        table.setTableNumber(number);
        table.setStatus("occupied");
        table.setServerName("CJ");
        table.setIsQuickOrder(false);
        return table;
    }

    private Order order(Long id, Long tableId, String status) {
        Order order = new Order();
        order.setOrderId(id);
        order.setTableId(tableId);
        order.setStatus(status);
        order.setServerName("CJ");
        return order;
    }

    private OrderItem item(Long id, Long orderId, Long menuItemId, String status, String price) {
        OrderItem item = new OrderItem();
        item.setOrderItemId(id);
        item.setOrderId(orderId);
        item.setMenuItemId(menuItemId);
        item.setStatus(status);
        item.setQuantity(1);
        item.setPrice(new BigDecimal(price));
        return item;
    }

    private MenuItem menuItem(Long id, String name) {
        MenuItem menuItem = new MenuItem();
        menuItem.setMenuItemId(id);
        menuItem.setName(name);
        return menuItem;
    }

    @Test
    void testGetOverview_ShouldSummarizeEachTableWithAnOpenOrder() {
        // WHAT: Test the ACTIVE half of the overview
        // WHY: Each card shows the table, how many items it has in each state, and what it comes to

        // Given - W1 has an open order with three items; W2 has no orders
        Order open = order(10L, 1L, "open");
        when(tableService.getTablesByQuickOrder(false)).thenReturn(List.of(table(1L, "W1"), table(2L, "W2")));
        when(orderRepository.findByTableIdInAndStatus(List.of(1L, 2L), "open")).thenReturn(List.of(open));
        when(orderRepository.findTop50ByTableIdInAndStatusOrderByClosedAtDesc(List.of(1L, 2L), "closed")).thenReturn(List.of());
        when(orderItemRepository.findByOrderIdIn(List.of(10L))).thenReturn(List.of(
                item(100L, 10L, 5L, "pending", "16.00"),
                item(101L, 10L, 6L, "completed", "17.00"),
                item(102L, 10L, 6L, "limbo", "17.00")));

        // When
        TablesOverviewView overview = tableOverviewService.getOverview(false, MANAGER);

        // Then - One card, for W1 only
        assertEquals(1, overview.activeTables().size());
        ActiveTableView card = overview.activeTables().get(0);
        assertEquals("W1", card.tableNumber());
        assertEquals("CJ", card.serverName());
        assertEquals(3, card.itemCount());
        assertEquals(1, card.limboCount());
        assertEquals(1, card.pendingCount());
        assertEquals(0, card.firedCount());
        assertEquals(1, card.completedCount());
        assertEquals(new BigDecimal("50.00"), card.subtotal());
        assertTrue(overview.closedChecks().isEmpty());

        // Nothing is closed, so the payment, menu, and modifier queries are skipped
        verify(paymentRepository, never()).findByOrderIdIn(anyCollection());
        verify(menuItemRepository, never()).findAllById(any());
    }

    @Test
    void testGetOverview_ShouldBuildClosedChecksWithItemsModifiersAndPayment() {
        // WHAT: Test the CLOSED half of the overview
        // WHY: A closed check shows what was ordered (with modifiers and notes), what it
        //      came to, and how it was paid

        // Given - A closed order on W1 whose totals were recorded when it was paid
        Order closed = order(20L, 1L, "closed");
        closed.setSubtotal(new BigDecimal("17.00"));
        closed.setTax(new BigDecimal("0.51"));
        closed.setTotal(new BigDecimal("17.51"));
        closed.setClosedAt(LocalDateTime.now());

        OrderItem comboPlate = item(200L, 20L, 4L, "completed", "17.00");
        comboPlate.setSpecialInstructions("nut allergy");

        Payment payment = new Payment();
        payment.setOrderId(20L);
        payment.setAmount(new BigDecimal("17.51"));
        payment.setTipAmount(new BigDecimal("3.50"));
        payment.setPaymentMethod("credit_card");

        when(tableService.getTablesByQuickOrder(false)).thenReturn(List.of(table(1L, "W1")));
        when(orderRepository.findByTableIdInAndStatus(List.of(1L), "open")).thenReturn(List.of());
        when(orderRepository.findTop50ByTableIdInAndStatusOrderByClosedAtDesc(List.of(1L), "closed")).thenReturn(List.of(closed));
        when(orderItemRepository.findByOrderIdIn(List.of(20L))).thenReturn(List.of(comboPlate));
        when(paymentRepository.findByOrderIdIn(List.of(20L))).thenReturn(List.of(payment));
        when(menuItemRepository.findAllById(any())).thenReturn(List.of(menuItem(4L, "Combo Plate")));
        when(orderItemModifierService.getModifierViewsForOrderItems(List.of(200L))).thenReturn(Map.of(
                200L, List.of(new OrderItemModifierView(200L, 14L, "French Toast", new BigDecimal("2.00")))));

        // When
        TablesOverviewView overview = tableOverviewService.getOverview(false, MANAGER);

        // Then
        assertTrue(overview.activeTables().isEmpty());
        assertEquals(1, overview.closedChecks().size());
        ClosedCheckView check = overview.closedChecks().get(0);
        assertEquals(20L, check.orderId());
        assertEquals("W1", check.tableNumber());
        assertEquals(new BigDecimal("17.00"), check.subtotal());
        assertEquals(new BigDecimal("0.51"), check.tax());
        assertEquals(new BigDecimal("17.51"), check.total());
        assertEquals(new BigDecimal("3.50"), check.tipAmount());
        assertEquals("credit_card", check.paymentMethod());

        assertEquals(1, check.items().size());
        assertEquals("Combo Plate", check.items().get(0).name());
        assertEquals("nut allergy", check.items().get(0).specialInstructions());
        assertEquals("French Toast", check.items().get(0).modifiers().get(0).name());

        // The recorded totals were used - nothing was recalculated
        verify(orderCheckService, never()).buildCheck(any(), any());
    }

    @Test
    void testGetOverview_ShouldWorkOutTotalsForChecksClosedBeforeTheyWereRecorded() {
        // WHAT: Test a closed order whose subtotal, tax, and total are still zero
        // WHY: Orders paid before the backend recorded the check hold zeros. Showing $0.00
        //      for a check the guest paid would be wrong.

        // Given - An older closed order with zeros, and no payment on record
        Order older = order(20L, 1L, "closed");
        OrderItem omelette = item(200L, 20L, 5L, "completed", "16.00");

        when(tableService.getTablesByQuickOrder(false)).thenReturn(List.of(table(1L, "W1")));
        when(orderRepository.findByTableIdInAndStatus(List.of(1L), "open")).thenReturn(List.of());
        when(orderRepository.findTop50ByTableIdInAndStatusOrderByClosedAtDesc(List.of(1L), "closed")).thenReturn(List.of(older));
        when(orderItemRepository.findByOrderIdIn(List.of(20L))).thenReturn(List.of(omelette));
        when(paymentRepository.findByOrderIdIn(List.of(20L))).thenReturn(List.of());
        when(menuItemRepository.findAllById(any())).thenReturn(List.of());
        when(orderItemModifierService.getModifierViewsForOrderItems(List.of(200L))).thenReturn(Map.of());
        when(orderCheckService.buildCheck(older, List.of(omelette))).thenReturn(new OrderCheckView(
                20L, "closed", 1, 0, new BigDecimal("16.00"), new BigDecimal("0.48"), new BigDecimal("16.48"), new BigDecimal("0.03")));

        // When
        ClosedCheckView check = tableOverviewService.getOverview(false, MANAGER).closedChecks().get(0);

        // Then - Worked out from the items; no payment means no tip and no method
        assertEquals(new BigDecimal("16.48"), check.total());
        assertEquals(new BigDecimal("0.48"), check.tax());
        assertEquals(BigDecimal.ZERO, check.tipAmount());
        assertNull(check.paymentMethod());
        // A menu item that no longer exists gets a placeholder name instead of breaking the check
        assertEquals("Item 5", check.items().get(0).name());
    }

    @Test
    void testGetOverview_WhenThereAreNoTables_ShouldSkipEveryOtherQuery() {
        // WHAT: Test the overview with no tables (for example, no Quick Orders yet)
        // WHY: Nothing to show should cost one query, not eight

        // Given
        when(tableService.getTablesByQuickOrder(true)).thenReturn(List.of());

        // When
        TablesOverviewView overview = tableOverviewService.getOverview(true, MANAGER);

        // Then
        assertTrue(overview.activeTables().isEmpty());
        assertTrue(overview.closedChecks().isEmpty());
        verifyNoInteractions(orderRepository, orderItemRepository, paymentRepository, menuItemRepository);
    }

    // ------------------------------------------------------------------
    // Who sees the tip on a closed check (the owner's decision D10)
    // ------------------------------------------------------------------

    // A closed order on W1, opened by the given staff member (null = nobody on record)
    private Order closedOrderOpenedBy(Long orderId, Long staffId) {
        Order closed = order(orderId, 1L, "closed");
        closed.setStaffId(staffId);
        closed.setSubtotal(new BigDecimal("20.00"));
        closed.setTax(new BigDecimal("0.60"));
        closed.setTotal(new BigDecimal("20.60"));
        closed.setClosedAt(LocalDateTime.now());
        return closed;
    }

    private Payment paymentWithTip(Long orderId, String tip) {
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(new BigDecimal("20.60"));
        payment.setTipAmount(new BigDecimal(tip));
        payment.setPaymentMethod("credit_card");
        return payment;
    }

    @Test
    void testGetOverview_ShouldGiveAServerTheTipOnTheirOwnCheckOnly() {
        // WHAT: Test two closed checks read by Sarah: one she opened, one Dan (staff 8) opened
        // WHY: A person sees the tip on checks they opened and nobody else's

        // Given
        Order hers = closedOrderOpenedBy(20L, 7L);
        Order dans = closedOrderOpenedBy(21L, 8L);

        when(tableService.getTablesByQuickOrder(false)).thenReturn(List.of(table(1L, "W1")));
        when(orderRepository.findByTableIdInAndStatus(List.of(1L), "open")).thenReturn(List.of());
        when(orderRepository.findTop50ByTableIdInAndStatusOrderByClosedAtDesc(List.of(1L), "closed")).thenReturn(List.of(hers, dans));
        when(orderItemRepository.findByOrderIdIn(List.of(20L, 21L))).thenReturn(List.of());
        when(paymentRepository.findByOrderIdIn(List.of(20L))).thenReturn(List.of(paymentWithTip(20L, "5.00")));
        when(menuItemRepository.findAllById(any())).thenReturn(List.of());
        when(orderItemModifierService.getModifierViewsForOrderItems(List.of())).thenReturn(Map.of());

        // When
        List<ClosedCheckView> checks = tableOverviewService.getOverview(false, SARAH).closedChecks();

        // Then - her own check carries the tip and how it was paid
        assertEquals(new BigDecimal("5.00"), checks.get(0).tipAmount());
        assertEquals("credit_card", checks.get(0).paymentMethod());

        // And - Dan's check is there with its total, and neither private field
        assertEquals(21L, checks.get(1).orderId());
        assertEquals(new BigDecimal("20.60"), checks.get(1).total());
        assertNull(checks.get(1).tipAmount());
        assertNull(checks.get(1).paymentMethod());

        // And - Dan's payment was never read from the database at all
        verify(paymentRepository).findByOrderIdIn(List.of(20L));
        verifyNoMoreInteractions(paymentRepository);
    }

    @Test
    void testGetOverview_ShouldNotReadAnyPaymentForSomeoneWhoOpenedNoneOfTheChecks() {
        // WHAT: Test closed checks read by someone who opened none of them
        // WHY: With no tip to show, the payments query is skipped: what is never read
        //      cannot be sent by mistake

        // Given
        when(tableService.getTablesByQuickOrder(false)).thenReturn(List.of(table(1L, "W1")));
        when(orderRepository.findByTableIdInAndStatus(List.of(1L), "open")).thenReturn(List.of());
        when(orderRepository.findTop50ByTableIdInAndStatusOrderByClosedAtDesc(List.of(1L), "closed"))
                .thenReturn(List.of(closedOrderOpenedBy(21L, 8L)));
        when(orderItemRepository.findByOrderIdIn(List.of(21L))).thenReturn(List.of());
        when(menuItemRepository.findAllById(any())).thenReturn(List.of());
        when(orderItemModifierService.getModifierViewsForOrderItems(List.of())).thenReturn(Map.of());

        // When
        ClosedCheckView check = tableOverviewService.getOverview(false, SARAH).closedChecks().get(0);

        // Then
        assertNull(check.tipAmount());
        assertNull(check.paymentMethod());
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void testMaySeeTip_ShouldFollowTheOwnersRule() {
        // WHAT: Test the rule on its own, for each kind of person and check
        // WHY: One small method decides what is private. Every case is written out.

        StaffSession kitchen = new StaffSession(9L, "Kit", StaffRole.KITCHEN, Instant.now(), false);
        Order sarahs = closedOrderOpenedBy(20L, 7L);
        Order dans = closedOrderOpenedBy(21L, 8L);
        Order nobodyOnRecord = closedOrderOpenedBy(22L, null);       // Closed before staff profiles existed

        // A manager: every check, including one with nobody on record
        assertTrue(TableOverviewService.maySeeTip(sarahs, MANAGER));
        assertTrue(TableOverviewService.maySeeTip(dans, MANAGER));
        assertTrue(TableOverviewService.maySeeTip(nobodyOnRecord, MANAGER));

        // Anyone else: the checks they opened, and no others
        assertTrue(TableOverviewService.maySeeTip(sarahs, SARAH));
        assertFalse(TableOverviewService.maySeeTip(dans, SARAH));
        assertFalse(TableOverviewService.maySeeTip(nobodyOnRecord, SARAH));
        assertFalse(TableOverviewService.maySeeTip(sarahs, kitchen));
    }
}
