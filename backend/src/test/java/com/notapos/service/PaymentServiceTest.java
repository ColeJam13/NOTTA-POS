package com.notapos.service;

import com.notapos.entity.Payment;
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

    @Test
    void testCreatePayment_ShouldSavePayment() {
        // WHAT: Test processing a new payment
        // WHY: Guest pays for their meal
        
        // Given - Mock returns saved payment and order doesn't exist (payment succeeds anyway)
        when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        // When - Process payment
        Payment created = paymentService.createPayment(testPayment);

        // Then - Should save payment with all details
        assertNotNull(created);
        assertEquals(new BigDecimal("50.00"), created.getAmount());
        assertEquals(new BigDecimal("10.00"), created.getTipAmount());
        assertEquals("credit_card", created.getPaymentMethod());
        verify(paymentRepository, times(1)).save(testPayment);
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
    void testUpdatePayment_ShouldUpdateFields() {
        // WHAT: Test updating payment details
        // WHY: Adjust tips, fix transaction references
        
        // Given - Payment exists
        Payment updatedData = new Payment();
        updatedData.setAmount(new BigDecimal("55.00"));
        updatedData.setTipAmount(new BigDecimal("15.00"));
        updatedData.setStatus("completed");

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);

        // When - Update payment
        Payment result = paymentService.updatePayment(1L, updatedData);

        // Then - Should update amount and tip
        assertEquals(new BigDecimal("55.00"), result.getAmount());
        assertEquals(new BigDecimal("15.00"), result.getTipAmount());
        verify(paymentRepository, times(1)).save(testPayment);
    }

    @Test
    void testUpdatePayment_WhenNotFound_ShouldThrowException() {
        // WHAT: Test error handling when payment doesn't exist
        // WHY: Can't update non-existent payment
        
        // Given - Payment doesn't exist
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then - Should throw exception
        assertThrows(RuntimeException.class, () -> {
            paymentService.updatePayment(999L, new Payment());
        });
    }

    @Test
    void testDeletePayment_ShouldCallRepository() {
        // WHAT: Test deleting a payment
        // WHY: Remove test transactions (not recommended for production)
        
        // Given - Mock repository
        doNothing().when(paymentRepository).deleteById(1L);

        // When - Delete payment
        paymentService.deletePayment(1L);

        // Then - Repository delete should be called
        verify(paymentRepository, times(1)).deleteById(1L);
    }
}