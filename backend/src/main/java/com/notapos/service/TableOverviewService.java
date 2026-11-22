package com.notapos.service;

import com.notapos.dto.ActiveTableView;
import com.notapos.dto.ClosedCheckItemView;
import com.notapos.dto.ClosedCheckView;
import com.notapos.dto.OrderCheckView;
import com.notapos.dto.OrderItemModifierView;
import com.notapos.dto.TablesOverviewView;
import com.notapos.entity.MenuItem;
import com.notapos.entity.Order;
import com.notapos.entity.OrderItem;
import com.notapos.entity.Payment;
import com.notapos.entity.RestaurantTable;
import com.notapos.enums.OrderItemStatus;
import com.notapos.enums.OrderStatus;
import com.notapos.enums.StaffRole;
import com.notapos.repository.MenuItemRepository;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.PaymentRepository;
import com.notapos.security.StaffSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds what the Tables screen and the Quick Orders screen show: the tables
 * with an open check, and the most recent paid checks.
 *
 * WHAT: Answers either screen with ONE response.
 * HOW:  At most eight database queries no matter how busy the restaurant is:
 *         1. the tables (regular tables, or Quick Orders)
 *         2. the open orders on those tables        (WHERE table_id IN (...))
 *         3. the 50 most recently closed orders     (WHERE table_id IN (...))
 *         4. every item on those orders             (WHERE order_id IN (...))
 *         5. the payments for the closed orders the person asking may see the tip on
 *                                                   (WHERE order_id IN (...))
 *         6. the menu items those items use         (WHERE menu_item_id IN (...))
 *         7. the modifiers chosen on closed checks  (WHERE order_item_id IN (...))
 *         8. the names of those modifiers           (WHERE modifier_id IN (...))
 *       The results are then matched up in memory using maps.
 *       (Queries 7 and 8 live in OrderItemModifierService.)
 * WHY:  Each screen used to download EVERY order, order item, payment, and menu
 *       item every 3 seconds and match them up in the browser. That gets slower
 *       every day the restaurant is open. Same idea as ActiveOrderService.
 *
 * Only the 50 most recently closed checks are returned. Older ones are in the
 * Financials screen's numbers.
 *
 * WHO SEES A TIP (the owner's decision D10, step A6 of the Phase 2 plan): a
 * person sees the tip and the payment method on checks they opened; a manager
 * sees them on every check. Everyone sees that a check was closed, what was on
 * it, and its total. The rule is maySeeTip() below, and it is applied HERE, on
 * the backend: for anyone else the two fields are not in the answer at all.
 * Sending them and hiding them on the screen would still hand them to anyone
 * who opens the browser's network tab.
 *
 * @author CJ
 */
@Service
@Transactional(readOnly = true)      // All the queries see the same consistent snapshot of the data
public class TableOverviewService {

    private final TableService tableService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final MenuItemRepository menuItemRepository;
    private final OrderItemModifierService orderItemModifierService;
    private final OrderCheckService orderCheckService;

    @Autowired
    public TableOverviewService(TableService tableService,
                                OrderRepository orderRepository,
                                OrderItemRepository orderItemRepository,
                                PaymentRepository paymentRepository,
                                MenuItemRepository menuItemRepository,
                                OrderItemModifierService orderItemModifierService,
                                OrderCheckService orderCheckService) {
        this.tableService = tableService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.menuItemRepository = menuItemRepository;
        this.orderItemModifierService = orderItemModifierService;
        this.orderCheckService = orderCheckService;
    }

    /**
     * Get the active tables and recent closed checks (Quick Orders, or regular tables).
     *
     * @param quickOrders True for the Quick Orders screen, false for Tables
     * @param viewer      The signed-in person asking. Decides which tips are in the answer.
     */
    public TablesOverviewView getOverview(boolean quickOrders, StaffSession viewer) {
        // Query 1: the tables this screen is about
        List<RestaurantTable> tables = tableService.getTablesByQuickOrder(quickOrders);
        if (tables.isEmpty()) {
            return new TablesOverviewView(List.of(), List.of());        // No tables - skip the other queries
        }
        List<Long> tableIds = tables.stream().map(RestaurantTable::getTableId).toList();
        Map<Long, RestaurantTable> tablesById = tables.stream()
                .collect(Collectors.toMap(RestaurantTable::getTableId, Function.identity()));

        // Queries 2 and 3: open orders, and the most recent closed ones
        List<Order> openOrders = orderRepository.findByTableIdInAndStatus(tableIds, OrderStatus.OPEN.getValue());
        List<Order> closedOrders = orderRepository.findTop50ByTableIdInAndStatusOrderByClosedAtDesc(
                tableIds, OrderStatus.CLOSED.getValue());

        // Query 4: every item on all of those orders, grouped by the order it belongs to
        List<Long> orderIds = new ArrayList<>();
        openOrders.forEach(order -> orderIds.add(order.getOrderId()));
        closedOrders.forEach(order -> orderIds.add(order.getOrderId()));
        List<OrderItem> allItems = orderIds.isEmpty() ? List.of() : orderItemRepository.findByOrderIdIn(orderIds);
        Map<Long, List<OrderItem>> itemsByOrderId = allItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        List<ActiveTableView> activeTables = buildActiveTables(openOrders, tablesById, itemsByOrderId);
        List<ClosedCheckView> closedChecks = buildClosedChecks(closedOrders, tablesById, itemsByOrderId, viewer);

        return new TablesOverviewView(activeTables, closedChecks);
    }

    // One card per table that has an open order. (A table normally has one open order; if an
    // older database left it with more, their items are added together.)
    private List<ActiveTableView> buildActiveTables(List<Order> openOrders,
                                                    Map<Long, RestaurantTable> tablesById,
                                                    Map<Long, List<OrderItem>> itemsByOrderId) {
        Map<Long, List<Order>> openOrdersByTableId = openOrders.stream()
                .collect(Collectors.groupingBy(Order::getTableId));

        return openOrdersByTableId.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())                     // By table ID, so cards do not jump around on screen
                .map(entry -> {
                    RestaurantTable table = tablesById.get(entry.getKey());
                    List<Order> tableOrders = entry.getValue();
                    List<OrderItem> items = tableOrders.stream()
                            .flatMap(order -> itemsByOrderId.getOrDefault(order.getOrderId(), List.of()).stream())
                            .toList();

                    BigDecimal subtotal = items.stream()
                            .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return new ActiveTableView(
                            table.getTableId(),
                            table.getTableNumber(),
                            table.getStatus(),
                            table.getServerName(),
                            table.getIsQuickOrder(),
                            items.size(),
                            countWithStatus(items, OrderItemStatus.LIMBO),
                            countWithStatus(items, OrderItemStatus.PENDING),
                            countWithStatus(items, OrderItemStatus.FIRED),
                            countWithStatus(items, OrderItemStatus.COMPLETED),
                            subtotal,
                            tableOrders.stream().map(Order::getCreatedAt).filter(Objects::nonNull)
                                    .min(Comparator.naturalOrder()).orElse(null));     // The oldest open order's time
                })
                .toList();
    }

    private int countWithStatus(List<OrderItem> items, OrderItemStatus status) {
        return (int) items.stream().filter(item -> status.matches(item.getStatus())).count();
    }

    /**
     * May this person see the tip and payment method on this check?
     *
     * A manager: always. Anyone else: only on a check they opened, which means
     * the order's staff ID is theirs (OrderController stamps it from the
     * signed-in person when the order is created; taking the payment on
     * someone else's check does not change it).
     *
     * A check with no staff ID was closed before staff profiles existed.
     * Nobody can be shown to have opened it, so only a manager sees its tip.
     */
    static boolean maySeeTip(Order order, StaffSession viewer) {
        if (viewer.role() == StaffRole.MANAGER) {
            return true;
        }
        return order.getStaffId() != null && order.getStaffId().equals(viewer.staffId());
    }

    // One card per closed order, in the order the query returned them (newest first).
    // Not private: the search box (SearchService, in this package) shows closed checks too,
    // and must show them under exactly this rule about tips. It hands in the checks it
    // found and gets back the same views this screen gets; the rule is written once.
    List<ClosedCheckView> buildClosedChecks(List<Order> closedOrders,
                                                    Map<Long, RestaurantTable> tablesById,
                                                    Map<Long, List<OrderItem>> itemsByOrderId,
                                                    StaffSession viewer) {
        if (closedOrders.isEmpty()) {
            return List.of();                                           // Nothing closed - skip queries 5 to 8
        }

        List<Long> closedOrderIds = closedOrders.stream().map(Order::getOrderId).toList();
        List<OrderItem> closedItems = closedOrderIds.stream()
                .flatMap(orderId -> itemsByOrderId.getOrDefault(orderId, List.of()).stream())
                .toList();

        // Query 5: payments, keyed by order. An order has one payment; if it somehow has more, the first is shown.
        // Only for the checks this person may see the tip on: the others' payments are never
        // read from the database, so there is nothing to leak by mistake further down.
        List<Long> tipVisibleOrderIds = closedOrders.stream()
                .filter(order -> maySeeTip(order, viewer))
                .map(Order::getOrderId)
                .toList();
        Map<Long, Payment> paymentsByOrderId = tipVisibleOrderIds.isEmpty()
                ? Map.of()                                              // Nothing to look up - skip the query
                : paymentRepository.findByOrderIdIn(tipVisibleOrderIds).stream()
                        .collect(Collectors.toMap(Payment::getOrderId, Function.identity(), (first, second) -> first));

        // Query 6: menu items, keyed by ID so each name lookup is instant
        Set<Long> menuItemIds = closedItems.stream().map(OrderItem::getMenuItemId).collect(Collectors.toSet());
        Map<Long, MenuItem> menuItemsById = menuItemRepository.findAllById(menuItemIds).stream()
                .collect(Collectors.toMap(MenuItem::getMenuItemId, Function.identity()));

        // Queries 7 and 8: the modifiers chosen for every item, with names, keyed by order item ID
        Map<Long, List<OrderItemModifierView>> modifiersByOrderItemId = orderItemModifierService
                .getModifierViewsForOrderItems(closedItems.stream().map(OrderItem::getOrderItemId).toList());

        return closedOrders.stream()
                .map(order -> {
                    RestaurantTable table = tablesById.get(order.getTableId());
                    List<OrderItem> items = itemsByOrderId.getOrDefault(order.getOrderId(), List.of());
                    Payment payment = paymentsByOrderId.get(order.getOrderId());

                    // Items in the order the server rang them in, so the check always lists them
                    // in the same order and reads the way the order was taken
                    List<ClosedCheckItemView> itemViews = items.stream()
                            .sorted(OrderItem.IN_RING_IN_ORDER)
                            .map(item -> {
                                MenuItem menuItem = menuItemsById.get(item.getMenuItemId());
                                return new ClosedCheckItemView(
                                        item.getOrderItemId(),
                                        menuItem != null ? menuItem.getName() : "Item " + item.getMenuItemId(),
                                        item.getQuantity(),
                                        item.getPrice(),
                                        item.getSpecialInstructions(),
                                        modifiersByOrderItemId.getOrDefault(item.getOrderItemId(), List.of()));
                            })
                            .toList();

                    // The amounts recorded when the check was paid. Checks closed before those were
                    // recorded hold zeros, so for them the amounts are worked out from the items.
                    BigDecimal subtotal = order.getSubtotal();
                    BigDecimal tax = order.getTax();
                    BigDecimal total = order.getTotal();
                    if (total == null || total.signum() == 0) {
                        OrderCheckView check = orderCheckService.buildCheck(order, items);
                        subtotal = check.subtotal();
                        tax = check.tax();
                        total = check.total();
                    }

                    // The tip and how it was paid: left out (null) unless this person may see them.
                    // For someone who may: zero when there was no tip, and no method when no
                    // payment is on record at all (a check from before payments were kept).
                    boolean tipVisible = tipVisibleOrderIds.contains(order.getOrderId());
                    BigDecimal tipAmount = null;
                    String paymentMethod = null;
                    if (tipVisible) {
                        tipAmount = payment != null && payment.getTipAmount() != null ? payment.getTipAmount() : BigDecimal.ZERO;
                        paymentMethod = payment != null ? payment.getPaymentMethod() : null;
                    }

                    return new ClosedCheckView(
                            order.getOrderId(),
                            order.getTableId(),
                            table.getTableNumber(),
                            table.getIsQuickOrder(),
                            order.getServerName(),
                            order.getCreatedAt(),
                            order.getClosedAt(),
                            subtotal,
                            tax,
                            total,
                            tipAmount,
                            paymentMethod,
                            itemViews);
                })
                .toList();
    }
}
