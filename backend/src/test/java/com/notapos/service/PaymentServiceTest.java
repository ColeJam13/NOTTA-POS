package com.notapos.service;

import com.notapos.dto.OrderCheckView;
import com.notapos.entity.Order;
import com.notapos.entity.Payment;
import com.notapos.entity.RestaurantTable;
import com.notapos.exception.OrderStateException;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.PaymentRepository;
import com.notapos.repository.TableRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentService.
 * 
 * Tests payment processing and tip tracking.
 * 
 * @author CJ
 */

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TableRepository tableRepository;

    @Mock
    private OrderCheckService orderCheckService;

    @Mock
    private OrderItemService orderItemService;

    @InjectMocks
    private PaymentService paymentService;

    private Payment testPayment;

    @BeforeEach
    void setUp() {
        // Create a test payment (credit card with tip)
        testPayment = new Payment();
        testPayment.setPaymentId(1L);
        testPayment.setOrderId(1L);
        testPayment.setAmount(new BigDecimal("50.00"));
        testPayment.setPaymentMethod("credit_card");
        testPayment.setTipAmount(new BigDecimal("10.00"));
        testPayment.setStatus("completed");
        testPayment.setTransactionReference("txn_12345");
    }

    // An open order on table 5
    private Order openOrder() {
        Order order = new Order();
        order.setOrderId(1L);
        order.setTableId(5L);
        order.setStatus("open");
        return order;
    }

    // The check the backend works out for order 1: sent items, unsent items, and the amounts
    private OrderCheckView check(int itemCount, int unsentItemCount, String subtotal, String tax, String total) {
        return new OrderCheckView(1L, "open", itemCount, unsentItemCount,
                new BigDecimal(subtotal), new BigDecimal(tax), new BigDecimal(total), new BigDecimal("0.03"));
    }
    @Test
    void testCreatePayment_ShouldSavePayment() {
        // WHAT: Test processing a new payment
        // WHY: Guest pays for their meal
        
        // Given - An open order whose check comes to exactly what is being paid
        Order order = openOrder();
        when(orderRepository.findByIdLockedForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderCheckService.buildCheck(order)).thenReturn(check(2, 0, "48.54", "1.46", "50.00"));
        when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);

        // When - Process payment
        Payment created = paymentService.createPayment(testPayment);

        // Then - Should save payment with all details
        assertNotNull(created);
        assertEquals(new BigDecimal("50.00"), created.getAmount());
        assertEquals(new BigDecimal("10.00"), created.getTipAmount());
        assertEquals("credit_card", created.getPaymentMethod());
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    void testCreatePayment_ShouldSaveANewPaymentBuiltFromTheRequestNotTheRequestItself() {
        // WHAT: Take a payment where the request carries a payment ID, a status, and its own amount
        // WHY: save() on an object that carries an ID means "update that row". A request that
        //      named an earlier payment's ID replaced that payment. The service now builds a new
        //      Payment and copies over only what a request may say: which order, how it was
        //      paid, the tip, and the card reference.

        // Given - a request that names payment 1 and calls itself refunded
        Order order = openOrder();
        testPayment.setStatus("refunded");
        when(orderRepository.findByIdLockedForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderCheckService.buildCheck(order)).thenReturn(check(2, 0, "48.54", "1.46", "50.00"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        paymentService.createPayment(testPayment);

        // Then - what was saved is a different object, with no ID (so it is always a new row)
        org.mockito.ArgumentCaptor<Payment> saved = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertNotSame(testPayment, saved.getValue());
        assertNull(saved.getValue().getPaymentId());

        // ...carrying what the request is allowed to say
        assertEquals(1L, saved.getValue().getOrderId());
        assertEquals("credit_card", saved.getValue().getPaymentMethod());
        assertEquals(new BigDecimal("10.00"), saved.getValue().getTipAmount());
        assertEquals("txn_12345", saved.getValue().getTransactionReference());

        // ...and not what it is not: the amount is the check's, the status is "completed"
        assertEquals(new BigDecimal("50.00"), saved.getValue().getAmount());
        assertEquals("completed", saved.getValue().getStatus());
    }

    @Test
    void testCreatePayment_WhenOrderDoesNotExist_ShouldThrowAndSaveNothing() {
        // WHAT: Test paying for an order that is not in the database
        // WHY: This used to save a payment that belonged to nothing. The Close Order button
        //      could be pressed before the order was ever saved.

        // Given - No such order
        when(orderRepository.findByIdLockedForUpdate(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> paymentService.createPayment(testPayment));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void testCreatePayment_WhenOrderAlreadyClosed_ShouldThrowAndSaveNothing() {
        // WHAT: Test paying for a check that is already closed
        // WHY: A second tap, or a second screen, must not charge the guest twice

        // Given
        Order order = openOrder();
        order.setStatus("closed");
        when(orderRepository.findByIdLockedForUpdate(1L)).thenReturn(Optional.of(order));

        // When & Then - OrderNotOpenException is the one that carries the code ORDER_NOT_OPEN,
        // which is how the payment window knows to close and reload the table
        com.notapos.exception.OrderNotOpenException exception = assertThrows(com.notapos.exception.OrderNotOpenException.class,
                () -> paymentService.createPayment(testPayment));
        assertTrue(exception.getMessage().contains("already closed"));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void testCreatePayment_WhenOrderHasUnsentItems_ShouldThrowAndSaveNothing() {
        // WHAT: Test closing a check that still has draft items on it
        // WHY: A draft never reached the kitchen. The server has to send it or remove it,
        //      otherwise the guest pays for food that was never made (or gets it for free).

        // Given - One sent item and two drafts
        Order order = openOrder();
        when(orderRepository.findByIdLockedForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderCheckService.buildCheck(order)).thenReturn(check(1, 2, "48.54", "1.46", "50.00"));

        // When & Then
        OrderStateException exception = assertThrows(OrderStateException.class,
                () -> paymentService.createPayment(testPayment));
        assertTrue(exception.getMessage().contains("2 unsent item(s)"));
        verify(paymentRepository, never()).save(any());
        assertEquals("open", order.getStatus());
    }

    @Test
    void testCreatePayment_WhenOrderHasNoItems_ShouldThrowAndSaveNothing() {
        // WHAT: Test paying for an empty order
        // WHY: There is nothing to pay for. An empty order is cleared off its table instead
        //      (OrderService.deleteOrder).

        // Given
        Order order = openOrder();
        when(orderRepository.findByIdLockedForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderCheckService.buildCheck(order)).thenReturn(check(0, 0, "0.00", "0.00", "0.00"));
        testPayment.setAmount(new BigDecimal("0.00"));

        // When & Then
        assertThrows(OrderStateException.class, () -> paymentService.createPayment(testPayment));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void testCreatePayment_WhenAmountDoesNotMatchTheCheck_ShouldThrowAndSaveNothing() {
        // WHAT: Test a payment for a different amount than the order's check
        // WHY: The amount comes from a screen. If another server added an item after that
        //      screen loaded, the guest was shown an old total and must not be charged it.

        // Given - The screen thinks the check is 50.00, the order really comes to 67.51
        Order order = openOrder();
        when(orderRepository.findByIdLockedForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderCheckService.buildCheck(order)).thenReturn(check(3, 0, "65.54", "1.97", "67.51"));

        // When & Then
        OrderStateException exception = assertThrows(OrderStateException.class,
                () -> paymentService.createPayment(testPayment));
        assertTrue(exception.getMessage().contains("67.51"));
        assertTrue(exception.getMessage().contains("50.00"));
        verify(paymentRepository, never()).save(any());
        verify(orderItemService, never()).sendOrderNow(any());
        assertEquals("open", order.getStatus());
    }

    @Test
    void testCreatePayment_ShouldAcceptTheSameAmountWrittenWithFewerDecimals() {
        // WHAT: Test that 50 and 50.00 count as the same amount
        // WHY: JSON drops trailing zeros (50.00 arrives as 50). BigDecimal.equals would call
        //      those different; compareTo does not.

        // Given
        Order order = openOrder();
        testPayment.setAmount(new BigDecimal("50"));
        when(orderRepository.findByIdLockedForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderCheckService.buildCheck(order)).thenReturn(check(2, 0, "48.54", "1.46", "50.00"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Payment created = paymentService.createPayment(testPayment);

        // Then - Saved with the backend's own number, to the cent
        assertEquals(new BigDecimal("50.00"), created.getAmount());
    }

    @Test
    void testCreatePayment_ShouldRecordTheCheckOnTheOrderAndEndTheEditWindow() {
        // WHAT: Test what else happens when a payment goes through
        // WHY: The order keeps its subtotal, tax, and total for the sales records, and any
        //      item still counting down is sent so it cannot be removed from a paid check

        // Given
        Order order = openOrder();
        when(orderRepository.findByIdLockedForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderCheckService.buildCheck(order)).thenReturn(check(2, 0, "48.54", "1.46", "50.00"));
        when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);

        // When
        paymentService.createPayment(testPayment);

        // Then
        assertEquals(new BigDecimal("48.54"), order.getSubtotal());
        assertEquals(new BigDecimal("1.46"), order.getTax());
        assertEquals(new BigDecimal("50.00"), order.getTotal());
        verify(orderItemService, times(1)).sendOrderNow(1L);
    }

    @Test
    void testCreatePayment_WhenNoTipIsGiven_ShouldSaveZeroTip() {
        // WHAT: Test a payment with no tip field
        // WHY: Reports add tips up; a missing tip must count as zero, not break the sum

        // Given
        Order order = openOrder();
        testPayment.setTipAmount(null);
        when(orderRepository.findByIdLockedForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderCheckService.buildCheck(order)).thenReturn(check(2, 0, "48.54", "1.46", "50.00"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Payment created = paymentService.createPayment(testPayment);

        // Then
        assertEquals(BigDecimal.ZERO, created.getTipAmount());
    }

    @Test
    void testGetAllPayments_ShouldReturnAllPayments() {
        // WHAT: Test retrieving all payments
        // WHY: Manager needs to see all transactions
        
        // Given - Mock returns 2 payments
        Payment payment2 = new Payment();
        List<Payment> payments = Arrays.asList(testPayment, payment2);
        when(paymentRepository.findAll()).thenReturn(payments);

        // When - Get all payments
        List<Payment> result = paymentService.getAllPayments();

        // Then - Should get both payments
        assertEquals(2, result.size());
        verify(paymentRepository, times(1)).findAll();
    }

    @Test
    void testGetPaymentById_WhenExists_ShouldReturnPayment() {
        // WHAT: Test finding a specific payment by ID
        // WHY: Need to load payment details for refunds/disputes
        
        // Given - Mock returns the payment
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));

        // When - Get payment by ID
        Optional<Payment> result = paymentService.getPaymentById(1L);

        // Then - Should find the payment
        assertTrue(result.isPresent());
        assertEquals("txn_12345", result.get().getTransactionReference());
        verify(paymentRepository, times(1)).findById(1L);
    }

    @Test
    void testGetPaymentsByOrder_ShouldReturnOrderPayments() {
        // WHAT: Test getting all payments for an order
        // WHY: Orders can have split payments (2 cards, cash + card, etc.)
        
        // Given - Mock returns payments for order
        List<Payment> orderPayments = Arrays.asList(testPayment);
        when(paymentRepository.findByOrderId(1L)).thenReturn(orderPayments);

        // When - Get payments for order 1
        List<Payment> result = paymentService.getPaymentsByOrder(1L);

        // Then - Should get that order's payments
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getOrderId());
        verify(paymentRepository, times(1)).findByOrderId(1L);
    }

    @Test
    void testGetPaymentsByMethod_ShouldFilterByPaymentMethod() {
        // WHAT: Test getting payments by payment method
        // WHY: Track cash vs credit card sales
        
        // Given - Mock returns credit card payments
        List<Payment> cardPayments = Arrays.asList(testPayment);
        when(paymentRepository.findByPaymentMethod("credit_card")).thenReturn(cardPayments);

        // When - Get credit card payments
        List<Payment> result = paymentService.getPaymentsByMethod("credit_card");

        // Then - Should get credit card payments only
        assertEquals(1, result.size());
        assertEquals("credit_card", result.get(0).getPaymentMethod());
        verify(paymentRepository, times(1)).findByPaymentMethod("credit_card");
    }

    @Test
    void testGetPaymentsByStatus_ShouldFilterByStatus() {
        // WHAT: Test getting payments by status
        // WHY: Find pending, completed, or refunded payments
        
        // Given - Mock returns completed payments
        List<Payment> completedPayments = Arrays.asList(testPayment);
        when(paymentRepository.findByStatus("completed")).thenReturn(completedPayments);

        // When - Get completed payments
        List<Payment> result = paymentService.getPaymentsByStatus("completed");

        // Then - Should get completed payments
        assertEquals(1, result.size());
        assertEquals("completed", result.get(0).getStatus());
        verify(paymentRepository, times(1)).findByStatus("completed");
    }

    @Test
    void testGetPaymentsBetween_ShouldReturnPaymentsInRange() {
        // WHAT: Test getting payments within a date range
        // WHY: Shift reports, daily close-out
        
        // Given - Mock returns payments in range
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 1, 23, 59);
        List<Payment> payments = Arrays.asList(testPayment);
        when(paymentRepository.findPaymentsBetween(start, end)).thenReturn(payments);

        // When - Get payments for Jan 1, 2025
        List<Payment> result = paymentService.getPaymentsBetween(start, end);

        // Then - Should get payments in that range
        assertEquals(1, result.size());
        verify(paymentRepository, times(1)).findPaymentsBetween(start, end);
    }

    @Test
    void testCalculateTotalTips_ShouldSumTips() {
        // WHAT: Test calculating total tips for a time period
        // WHY: Tip pool distribution, server reports
        
        // Given - Mock returns $100 in tips
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 1, 23, 59);
        when(paymentRepository.calculateTotalTips(start, end))
                .thenReturn(new BigDecimal("100.00"));

        // When - Calculate tips for the day
        BigDecimal totalTips = paymentService.calculateTotalTips(start, end);

        // Then - Should return total tips
        assertEquals(new BigDecimal("100.00"), totalTips);
        verify(paymentRepository, times(1)).calculateTotalTips(start, end);
    }

    @Test
    void testCalculateTotalTips_WhenNull_ShouldReturnZero() {
        // WHAT: Test handling when no tips exist
        // WHY: Prevent null pointer errors
        
        // Given - Mock returns null (no tips)
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 1, 23, 59);
        when(paymentRepository.calculateTotalTips(start, end)).thenReturn(null);

        // When - Calculate tips
        BigDecimal totalTips = paymentService.calculateTotalTips(start, end);

        // Then - Should return zero instead of null
        assertEquals(BigDecimal.ZERO, totalTips);
    }

    @Test
    void testCreatePayment_WhenOrderAndTableExist_ShouldCloseOrderAndFreeTable() {
        // WHAT: Test payment processing with order and table closure
        // WHY: When payment is made, close the order and free up the table
        
        // Given - Payment, order, and table all exist
        com.notapos.entity.Order order = new com.notapos.entity.Order();
        order.setOrderId(1L);
        order.setTableId(5L);
        order.setStatus("open");
        
        com.notapos.entity.RestaurantTable table = new com.notapos.entity.RestaurantTable();
        table.setTableId(5L);
        table.setStatus("occupied");
        
        when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);
        when(orderRepository.findByIdLockedForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderCheckService.buildCheck(order)).thenReturn(check(2, 0, "48.54", "1.46", "50.00"));
        when(tableRepository.findById(5L)).thenReturn(Optional.of(table));
        when(orderRepository.save(any(com.notapos.entity.Order.class))).thenReturn(order);
        when(tableRepository.save(any(com.notapos.entity.RestaurantTable.class))).thenReturn(table);

        // When - Create payment
        Payment created = paymentService.createPayment(testPayment);

        // Then - Payment saved, order closed, table freed
        assertNotNull(created);
        assertEquals("closed", order.getStatus());
        assertNotNull(order.getClosedAt());
        assertEquals("available", table.getStatus());
        
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(orderRepository, times(1)).save(order);
        verify(tableRepository, times(1)).save(table);
    }

    @Test
    void testCreatePayment_WhenOrderExistsButTableDoesNot_ShouldCloseOrderOnly() {
        // WHAT: Test payment processing when table doesn't exist
        // WHY: Handle edge case where table is missing but order exists
        
        // Given - Payment and order exist, but table doesn't
        com.notapos.entity.Order order = new com.notapos.entity.Order();
        order.setOrderId(1L);
        order.setTableId(5L);
        order.setStatus("open");
        
        when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);
        when(orderRepository.findByIdLockedForUpdate(1L)).thenReturn(Optional.of(order));
        when(orderCheckService.buildCheck(order)).thenReturn(check(2, 0, "48.54", "1.46", "50.00"));
        when(tableRepository.findById(5L)).thenReturn(Optional.empty());
        when(orderRepository.save(any(com.notapos.entity.Order.class))).thenReturn(order);

        // When - Create payment
        Payment created = paymentService.createPayment(testPayment);

        // Then - Payment saved, order closed, but no table update
        assertNotNull(created);
        assertEquals("closed", order.getStatus());
        assertNotNull(order.getClosedAt());
        
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(orderRepository, times(1)).save(order);
        verify(tableRepository, never()).save(any());
    }
}