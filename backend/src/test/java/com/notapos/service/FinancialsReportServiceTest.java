package com.notapos.service;

import com.notapos.dto.FinancialsReportView;
import com.notapos.dto.OrderCheckView;
import com.notapos.entity.Order;
import com.notapos.entity.OrderItem;
import com.notapos.entity.Payment;
import com.notapos.entity.RestaurantTable;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.PaymentRepository;
import com.notapos.repository.TableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FinancialsReportService.
 *
 * Tests the numbers behind the Financials screen.
 *
 * @author CJ
 */

@ExtendWith(MockitoExtension.class)
class FinancialsReportServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TableRepository tableRepository;

    @Mock
    private OrderCheckService orderCheckService;

    private FinancialsReportService financialsReportService;

    // The report asks ClosedCheckAmountsService what each check was worth. The real one is
    // used here, over the same faked repositories, so these tests still cover the whole
    // sum exactly as they did when that code lived inside the report.
    @BeforeEach
    void setUp() {
        financialsReportService = new FinancialsReportService(orderRepository, paymentRepository, tableRepository,
                new ClosedCheckAmountsService(orderItemRepository, orderCheckService));
    }

    private final LocalDateTime from = LocalDateTime.of(2026, 11, 18, 0, 0);
    private final LocalDateTime to = LocalDateTime.of(2026, 11, 18, 23, 59, 59);

    // A closed order with its check recorded, paid at the given minute past noon
    private Order closedOrder(Long id, String subtotal, String tax, String total, int minute) {
        Order order = new Order();
        order.setOrderId(id);
        order.setTableId(1L);
        order.setStatus("closed");
        order.setServerName("CJ");
        order.setSubtotal(new BigDecimal(subtotal));
        order.setTax(new BigDecimal(tax));
        order.setTotal(new BigDecimal(total));
        order.setClosedAt(LocalDateTime.of(2026, 11, 18, 12, minute));
        return order;
    }

    private Payment payment(Long orderId, String amount, String tip, String method) {
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(new BigDecimal(amount));
        payment.setTipAmount(tip != null ? new BigDecimal(tip) : null);
        payment.setPaymentMethod(method);
        return payment;
    }

    private RestaurantTable table() {
        RestaurantTable table = new RestaurantTable();
        table.setTableId(1L);
        table.setTableNumber("W1");
        table.setIsQuickOrder(false);
        return table;
    }

    @Test
    void testGetReport_ShouldAddUpRevenueTaxTipsAndAverages() {
        // WHAT: Test the headline numbers for a period with two closed checks
        // WHY: These are the numbers an owner reads at the end of the night

        // Given - 33.00 + 0.99 tax with a 6.80 tip, and 10.00 + 0.30 tax with no tip
        when(orderRepository.findByStatusAndClosedAtBetween("closed", from, to)).thenReturn(List.of(
                closedOrder(1L, "33.00", "0.99", "33.99", 5),
                closedOrder(2L, "10.00", "0.30", "10.30", 9)));
        when(paymentRepository.findByOrderIdIn(List.of(1L, 2L))).thenReturn(List.of(
                payment(1L, "33.99", "6.80", "credit_card"),
                payment(2L, "10.30", "0.00", "cash")));
        when(tableRepository.findAllById(any())).thenReturn(List.of(table()));

        // When
        FinancialsReportView report = financialsReportService.getReport(from, to);

        // Then
        assertEquals(2, report.orderCount());
        assertEquals(new BigDecimal("43.00"), report.revenue());               // Sales before tax
        assertEquals(new BigDecimal("1.29"), report.tax());
        assertEquals(new BigDecimal("6.80"), report.tips());
        assertEquals(new BigDecimal("51.09"), report.totalCollected());        // 43.00 + 1.29 + 6.80
        assertEquals(new BigDecimal("21.50"), report.avgOrderValue());         // 43.00 / 2
        assertEquals(new BigDecimal("15.8"), report.avgTipPercent());          // 6.80 / 43.00

        // Every check had its totals recorded, so no items were loaded
        verify(orderItemRepository, never()).findByOrderIdIn(anyCollection());
    }

    @Test
    void testGetReport_ShouldBreakPaymentsDownByMethodInAFixedOrder() {
        // WHAT: Test the payment method breakdown
        // WHY: Cash has to be counted against the drawer; the order of the rows must not
        //      change from one day to the next

        // Given - Paid by gift card, cash, and credit card (in that order in the database)
        when(orderRepository.findByStatusAndClosedAtBetween("closed", from, to)).thenReturn(List.of(
                closedOrder(1L, "10.00", "0.30", "10.30", 1),
                closedOrder(2L, "20.00", "0.60", "20.60", 2),
                closedOrder(3L, "20.00", "0.60", "20.60", 3)));
        when(paymentRepository.findByOrderIdIn(List.of(1L, 2L, 3L))).thenReturn(List.of(
                payment(1L, "10.30", "0.00", "gift_card"),
                payment(2L, "20.60", "0.00", "cash"),
                payment(3L, "20.60", "0.00", "credit_card")));
        when(tableRepository.findAllById(any())).thenReturn(List.of(table()));

        // When
        List<FinancialsReportView.PaymentMethodTotal> breakdown = financialsReportService.getReport(from, to).paymentBreakdown();

        // Then - cash, credit card, gift card; shares of the 51.50 paid in total
        assertEquals(List.of("cash", "credit_card", "gift_card"),
                breakdown.stream().map(FinancialsReportView.PaymentMethodTotal::paymentMethod).toList());
        assertEquals(new BigDecimal("20.60"), breakdown.get(0).amount());
        assertEquals(new BigDecimal("40.0"), breakdown.get(0).percent());
        assertEquals(new BigDecimal("40.0"), breakdown.get(1).percent());
        assertEquals(new BigDecimal("20.0"), breakdown.get(2).percent());
    }

    @Test
    void testGetReport_ShouldWorkOutTotalsForChecksClosedBeforeTheyWereRecorded() {
        // WHAT: Test a period holding an older check whose subtotal, tax, and total are zero
        // WHY: Orders paid before the backend recorded the check would otherwise count as $0.00 of sales

        // Given - Order 1 has zeros; order 2 has its totals
        Order older = closedOrder(1L, "0.00", "0.00", "0.00", 5);
        Order newer = closedOrder(2L, "10.00", "0.30", "10.30", 9);
        OrderItem omelette = new OrderItem();
        omelette.setOrderId(1L);

        when(orderRepository.findByStatusAndClosedAtBetween("closed", from, to)).thenReturn(List.of(older, newer));
        when(paymentRepository.findByOrderIdIn(List.of(1L, 2L))).thenReturn(List.of());
        when(orderItemRepository.findByOrderIdIn(List.of(1L))).thenReturn(List.of(omelette));      // Items for the older order ONLY
        when(orderCheckService.buildCheck(older, List.of(omelette))).thenReturn(new OrderCheckView(
                1L, "closed", 1, 0, new BigDecimal("16.00"), new BigDecimal("0.48"), new BigDecimal("16.48"), new BigDecimal("0.03")));
        when(tableRepository.findAllById(any())).thenReturn(List.of(table()));

        // When
        FinancialsReportView report = financialsReportService.getReport(from, to);

        // Then - 16.00 worked out + 10.00 recorded
        assertEquals(new BigDecimal("26.00"), report.revenue());
        assertEquals(new BigDecimal("0.78"), report.tax());
        // With no payments on record there are no tips and nothing in the breakdown
        assertEquals(BigDecimal.ZERO, report.tips());
        assertTrue(report.paymentBreakdown().isEmpty());
        // The older check's transaction shows its worked-out total
        assertEquals(new BigDecimal("16.48"), report.recentTransactions().get(1).total());
    }

    @Test
    void testGetReport_ShouldListTheTenMostRecentChecksNewestFirst() {
        // WHAT: Test the recent transactions list
        // WHY: The list is a quick look at the latest checks, not the whole period

        // Given - 12 checks, closed one minute apart
        List<Order> orders = new ArrayList<>();
        List<Payment> payments = new ArrayList<>();
        for (long id = 1; id <= 12; id++) {
            orders.add(closedOrder(id, "10.00", "0.30", "10.30", (int) id));
            payments.add(payment(id, "10.30", "2.00", "cash"));
        }
        when(orderRepository.findByStatusAndClosedAtBetween("closed", from, to)).thenReturn(orders);
        when(paymentRepository.findByOrderIdIn(anyCollection())).thenReturn(payments);
        when(tableRepository.findAllById(any())).thenReturn(List.of(table()));

        // When
        FinancialsReportView report = financialsReportService.getReport(from, to);

        // Then - All 12 are counted, 10 are listed, the last one closed comes first
        assertEquals(12, report.orderCount());
        assertEquals(10, report.recentTransactions().size());
        FinancialsReportView.Transaction newest = report.recentTransactions().get(0);
        assertEquals(12L, newest.orderId());
        assertEquals("W1", newest.tableNumber());
        assertEquals(new BigDecimal("10.30"), newest.total());
        assertEquals(new BigDecimal("2.00"), newest.tip());
        assertEquals("cash", newest.paymentMethod());
        assertEquals(3L, report.recentTransactions().get(9).orderId());
    }

    @Test
    void testGetReport_WhenNothingWasClosed_ShouldReturnZerosAndSkipTheOtherQueries() {
        // WHAT: Test a period with no closed checks
        // WHY: A quiet day is zeros on screen, not an error (and not a divide by zero)

        // Given
        when(orderRepository.findByStatusAndClosedAtBetween("closed", from, to)).thenReturn(List.of());

        // When
        FinancialsReportView report = financialsReportService.getReport(from, to);

        // Then
        assertEquals(0, report.orderCount());
        assertEquals(new BigDecimal("0.00"), report.revenue());
        assertEquals(new BigDecimal("0.00"), report.avgOrderValue());
        assertEquals(BigDecimal.ZERO, report.avgTipPercent());
        assertTrue(report.paymentBreakdown().isEmpty());
        assertTrue(report.recentTransactions().isEmpty());
        verifyNoInteractions(paymentRepository, orderItemRepository, tableRepository);
    }
}
