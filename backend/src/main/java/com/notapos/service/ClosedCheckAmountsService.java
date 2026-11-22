package com.notapos.service;

import com.notapos.dto.OrderCheckView;
import com.notapos.entity.Order;
import com.notapos.entity.OrderItem;
import com.notapos.repository.OrderItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Says what each closed check was worth: its subtotal, tax, and total.
 *
 * WHAT: The one place a REPORT asks "how much was this closed check?".
 * WHY:  The answer is not always written on the order. A check paid since the
 *       backend started recording amounts carries its subtotal, tax, and total.
 *       One paid before that holds zeros, and its amounts have to be worked out
 *       from its items. The Financials report and the per-person sales report
 *       must treat such a check the same way, or the two screens would disagree
 *       by exactly those checks. So both ask here, and neither holds the rule.
 * HOW:  At most one database query, however many checks it is given: the items
 *       of the checks with no recorded total (WHERE order_id IN (...)).
 *
 * (The Tables screen applies the same rule to the checks it lists, in
 * TableOverviewService, where it already has every item loaded for another reason.)
 *
 * @author CJ
 */
@Service
@Transactional(readOnly = true)      // Joins the report's own transaction, so every query sees the same snapshot of the data
public class ClosedCheckAmountsService {

    /**
     * What one closed check was worth.
     *
     * @param subtotal Sales before tax: what Financials calls revenue
     * @param tax      Tax on it
     * @param total    Subtotal + tax: what the guest paid, before any tip
     */
    public record Amounts(BigDecimal subtotal, BigDecimal tax, BigDecimal total) {
    }

    private final OrderItemRepository orderItemRepository;
    private final OrderCheckService orderCheckService;

    @Autowired
    public ClosedCheckAmountsService(OrderItemRepository orderItemRepository, OrderCheckService orderCheckService) {
        this.orderItemRepository = orderItemRepository;
        this.orderCheckService = orderCheckService;
    }

    /**
     * The amounts of each of the given checks, keyed by order ID, in the order given.
     */
    public Map<Long, Amounts> amountsOf(List<Order> closedOrders) {
        // Orders paid before the backend recorded the check hold zeros for subtotal, tax, and
        // total. Their amounts are worked out from their items - loaded with one query for all of them.
        Map<Long, OrderCheckView> checksByOrderId = checksForOrdersWithoutTotals(closedOrders);

        Map<Long, Amounts> amountsByOrderId = new LinkedHashMap<>();
        for (Order order : closedOrders) {
            OrderCheckView check = checksByOrderId.get(order.getOrderId());
            amountsByOrderId.put(order.getOrderId(), check != null
                    ? new Amounts(check.subtotal(), check.tax(), check.total())
                    : new Amounts(order.getSubtotal(), order.getTax(), order.getTotal()));
        }
        return amountsByOrderId;
    }

    private Map<Long, OrderCheckView> checksForOrdersWithoutTotals(List<Order> closedOrders) {
        List<Order> withoutTotals = closedOrders.stream()
                .filter(order -> order.getTotal() == null || order.getTotal().signum() == 0)
                .toList();
        if (withoutTotals.isEmpty()) {
            return Map.of();                                            // Every check has its totals recorded - no items needed
        }

        List<Long> orderIds = withoutTotals.stream().map(Order::getOrderId).toList();
        Map<Long, List<OrderItem>> itemsByOrderId = orderItemRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        return withoutTotals.stream().collect(Collectors.toMap(
                Order::getOrderId,
                order -> orderCheckService.buildCheck(order, itemsByOrderId.getOrDefault(order.getOrderId(), List.of()))));
    }
}
