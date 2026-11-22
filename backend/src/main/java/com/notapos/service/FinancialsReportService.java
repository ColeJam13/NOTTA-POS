package com.notapos.service;

import com.notapos.dto.FinancialsReportView;
import com.notapos.dto.FinancialsReportView.PaymentMethodTotal;
import com.notapos.dto.FinancialsReportView.Transaction;
import com.notapos.entity.Order;
import com.notapos.entity.Payment;
import com.notapos.entity.RestaurantTable;
import com.notapos.enums.OrderStatus;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.PaymentRepository;
import com.notapos.repository.TableRepository;
import com.notapos.service.ClosedCheckAmountsService.Amounts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the Financials screen's numbers for one time period.
 *
 * WHAT: Answers the Financials screen with ONE response.
 * HOW:  At most four database queries, however many checks the period holds:
 *         1. the orders closed in the period
 *         2. their payments                          (WHERE order_id IN (...))
 *         3. items, ONLY for orders with no recorded totals (WHERE order_id IN (...));
 *            ClosedCheckAmountsService runs this one, because the per-person sales
 *            report needs the same answer to "what was this check worth?"
 *         4. the tables of the 10 most recent checks  (WHERE table_id IN (...))
 * WHY:  The screen used to download every order, payment, order item, and table
 *       the restaurant has ever had and add them up in the browser. Money is added
 *       up here instead, with exact decimal arithmetic (BigDecimal), not in
 *       JavaScript where 0.1 + 0.2 is 0.30000000000000004.
 *
 * @author CJ
 */
@Service
@Transactional(readOnly = true)      // All the queries see the same consistent snapshot of the data
public class FinancialsReportService {

    // How many checks the "recent transactions" list shows
    private static final int RECENT_TRANSACTION_COUNT = 10;

    // The order payment methods are listed in. Anything else comes after these, alphabetically.
    private static final List<String> PAYMENT_METHOD_ORDER = List.of("cash", "credit_card", "debit_card", "gift_card");

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final TableRepository tableRepository;
    private final ClosedCheckAmountsService closedCheckAmountsService;      // What each closed check was worth (shared with StaffSalesReportService)

    @Autowired
    public FinancialsReportService(OrderRepository orderRepository,
                                   PaymentRepository paymentRepository,
                                   TableRepository tableRepository,
                                   ClosedCheckAmountsService closedCheckAmountsService) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.tableRepository = tableRepository;
        this.closedCheckAmountsService = closedCheckAmountsService;
    }

    public FinancialsReportView getReport(LocalDateTime from, LocalDateTime to) {     // Get the report for checks closed between two moments (both included)
        // Query 1: the checks closed in the period
        List<Order> closedOrders = orderRepository.findByStatusAndClosedAtBetween(OrderStatus.CLOSED.getValue(), from, to);
        if (closedOrders.isEmpty()) {
            return emptyReport();                                       // Nothing closed - skip the other queries
        }
        List<Long> orderIds = closedOrders.stream().map(Order::getOrderId).toList();

        // Query 2: payments, keyed by order. An order has one payment; if it somehow has more, the first is used.
        Map<Long, Payment> paymentsByOrderId = paymentRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.toMap(Payment::getOrderId, Function.identity(), (first, second) -> first));

        // Query 3: orders paid before the backend recorded the check hold zeros for subtotal, tax, and
        // total. Their amounts are worked out from their items - loaded with one query for all of them.
        Map<Long, Amounts> amountsByOrderId = closedCheckAmountsService.amountsOf(closedOrders);

        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal tips = BigDecimal.ZERO;
        Map<String, BigDecimal> amountByMethod = new LinkedHashMap<>();
        Map<Long, BigDecimal> totalByOrderId = new LinkedHashMap<>();

        for (Order order : closedOrders) {
            Amounts amounts = amountsByOrderId.get(order.getOrderId());

            revenue = revenue.add(amounts.subtotal());
            tax = tax.add(amounts.tax());
            totalByOrderId.put(order.getOrderId(), amounts.total());

            Payment payment = paymentsByOrderId.get(order.getOrderId());
            if (payment != null) {
                tips = tips.add(tipOf(payment));
                String method = payment.getPaymentMethod() != null ? payment.getPaymentMethod() : "unknown";
                amountByMethod.merge(method, payment.getAmount(), BigDecimal::add);
            }
        }

        int orderCount = closedOrders.size();
        BigDecimal avgOrderValue = revenue.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);
        BigDecimal avgTipPercent = revenue.signum() > 0
                ? tips.multiply(ONE_HUNDRED).divide(revenue, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new FinancialsReportView(
                orderCount,
                revenue,
                tax,
                tips,
                revenue.add(tax).add(tips),
                avgOrderValue,
                avgTipPercent,
                buildPaymentBreakdown(amountByMethod),
                buildRecentTransactions(closedOrders, paymentsByOrderId, totalByOrderId));
    }

    private FinancialsReportView emptyReport() {
        BigDecimal zero = new BigDecimal("0.00");
        return new FinancialsReportView(0, zero, zero, zero, zero, zero, BigDecimal.ZERO, List.of(), List.of());
    }

    private BigDecimal tipOf(Payment payment) {
        return payment.getTipAmount() != null ? payment.getTipAmount() : BigDecimal.ZERO;
    }

    // One row per payment method, each with its share of all the payment amounts
    private List<PaymentMethodTotal> buildPaymentBreakdown(Map<String, BigDecimal> amountByMethod) {
        BigDecimal allPayments = amountByMethod.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        // Known methods first, in their fixed order; anything else after them, alphabetically
        Comparator<String> byDisplayOrder = Comparator
                .comparingInt((String method) -> {
                    int position = PAYMENT_METHOD_ORDER.indexOf(method);
                    return position >= 0 ? position : PAYMENT_METHOD_ORDER.size();
                })
                .thenComparing(Comparator.naturalOrder());

        return amountByMethod.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(byDisplayOrder))
                .map(entry -> new PaymentMethodTotal(
                        entry.getKey(),
                        entry.getValue(),
                        allPayments.signum() > 0
                                ? entry.getValue().multiply(ONE_HUNDRED).divide(allPayments, 1, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO))
                .toList();
    }

    // The most recently closed checks, newest first
    private List<Transaction> buildRecentTransactions(List<Order> closedOrders,
                                                      Map<Long, Payment> paymentsByOrderId,
                                                      Map<Long, BigDecimal> totalByOrderId) {
        List<Order> newest = new ArrayList<>(closedOrders);
        newest.sort(Comparator.comparing(Order::getClosedAt).reversed());
        List<Order> recent = newest.subList(0, Math.min(RECENT_TRANSACTION_COUNT, newest.size()));

        // Query 4: the tables those checks were on, keyed by ID
        Set<Long> tableIds = recent.stream().map(Order::getTableId).collect(Collectors.toSet());
        Map<Long, RestaurantTable> tablesById = tableRepository.findAllById(tableIds).stream()
                .collect(Collectors.toMap(RestaurantTable::getTableId, Function.identity()));

        return recent.stream()
                .map(order -> {
                    RestaurantTable table = tablesById.get(order.getTableId());
                    Payment payment = paymentsByOrderId.get(order.getOrderId());
                    return new Transaction(
                            order.getOrderId(),
                            table != null ? table.getTableNumber() : null,
                            table != null ? table.getIsQuickOrder() : null,
                            order.getServerName(),
                            order.getClosedAt(),
                            totalByOrderId.get(order.getOrderId()),
                            payment != null ? tipOf(payment) : BigDecimal.ZERO,
                            payment != null ? payment.getPaymentMethod() : null);
                })
                .toList();
    }
}
