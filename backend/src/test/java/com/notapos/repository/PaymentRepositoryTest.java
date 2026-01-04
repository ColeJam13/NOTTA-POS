package com.notapos.repository;

import com.notapos.entity.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for PaymentRepository.
 * 
 * Tests database queries for payment management.
 * 
 * @author CJ
 */

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    private Payment cashPayment;
    private Payment cardPayment;
    private Payment refundedPayment;

    @BeforeEach
    void setUp() {
        // Clear database before each test
        paymentRepository.deleteAll();

        // Create cash payment for order 1
        cashPayment = new Payment();
        cashPayment.setOrderId(1L);
        cashPayment.setAmount(new BigDecimal("50.00"));
        cashPayment.setPaymentMethod("cash");
        cashPayment.setTipAmount(new BigDecimal("10.00"));
        cashPayment.setStatus("completed");
        cashPayment.setTransactionReference("CASH-001");
        cashPayment = paymentRepository.save(cashPayment);

        // Create credit card payment for order 1
        cardPayment = new Payment();
        cardPayment.setOrderId(1L);
        cardPayment.setAmount(new BigDecimal("30.00"));
        cardPayment.setPaymentMethod("credit_card");
        cardPayment.setTipAmount(new BigDecimal("5.00"));
        cardPayment.setStatus("completed");
        cardPayment.setTransactionReference("CC-12345");
        cardPayment = paymentRepository.save(cardPayment);

        // Create refunded payment for order 2
        refundedPayment = new Payment();
        refundedPayment.setOrderId(2L);
        refundedPayment.setAmount(new BigDecimal("25.00"));
        refundedPayment.setPaymentMethod("credit_card");
        refundedPayment.setTipAmount(new BigDecimal("3.00"));
        refundedPayment.setStatus("refunded");
        refundedPayment.setTransactionReference("CC-67890");
        refundedPayment = paymentRepository.save(refundedPayment);
    }

    @Test
    void testSave_ShouldPersistPayment() {
        // WHAT: Test saving a new payment to database
        // WHY: Ensure basic create operation works
        
        // Given - New payment
        Payment newPayment = new Payment();
        newPayment.setOrderId(3L);
        newPayment.setAmount(new BigDecimal("45.00"));
        newPayment.setPaymentMethod("debit_card");
        newPayment.setTipAmount(new BigDecimal("7.00"));
        newPayment.setStatus("completed");

        // When - Save to database
        Payment saved = paymentRepository.save(newPayment);

        // Then - Should persist with generated ID
        assertNotNull(saved.getPaymentId());
        assertEquals(new BigDecimal("45.00"), saved.getAmount());
        assertEquals("debit_card", saved.getPaymentMethod());
    }

    @Test
    void testFindById_WhenExists_ShouldReturnPayment() {
        // WHAT: Test finding payment by ID
        // WHY: Need to load specific payments for refunds/viewing
        
        // Given - Cash payment exists in database (from setUp)
        
        // When - Find by ID
        Optional<Payment> result = paymentRepository.findById(cashPayment.getPaymentId());

        // Then - Should find the payment
        assertTrue(result.isPresent());
        assertEquals("cash", result.get().getPaymentMethod());
        assertEquals(new BigDecimal("50.00"), result.get().getAmount());
    }

    @Test
    void testFindById_WhenNotExists_ShouldReturnEmpty() {
        // WHAT: Test finding non-existent payment
        // WHY: Handle missing payments gracefully
        
        // Given - Non-existent ID
        
        // When - Try to find
        Optional<Payment> result = paymentRepository.findById(999L);

        // Then - Should return empty
        assertFalse(result.isPresent());
    }

    @Test
    void testFindAll_ShouldReturnAllPayments() {
        // WHAT: Test retrieving all payments
        // WHY: Get complete payment history
        
        // Given - 3 payments in database (from setUp)
        
        // When - Find all
        List<Payment> payments = paymentRepository.findAll();

        // Then - Should get all 3 payments
        assertEquals(3, payments.size());
    }

    @Test
    void testFindByOrderId_ShouldReturnPaymentsForOrder() {
        // WHAT: Test finding all payments for a specific order
        // WHY: Handle split checks (multiple payments per order)
        
        // Given - Order 1 has 2 payments, Order 2 has 1 payment (from setUp)
        
        // When - Find payments for Order 1
        List<Payment> order1Payments = paymentRepository.findByOrderId(1L);

        // Then - Should get 2 payments for Order 1
        assertEquals(2, order1Payments.size());
        assertTrue(order1Payments.stream().allMatch(p -> p.getOrderId().equals(1L)));
    }

    @Test
    void testFindByPaymentMethod_Cash_ShouldReturnCashPayments() {
        // WHAT: Test finding payments by payment method
        // WHY: Track cash vs card transactions for accounting
        
        // Given - 1 cash payment exists (from setUp)
        
        // When - Find cash payments
        List<Payment> cashPayments = paymentRepository.findByPaymentMethod("cash");

        // Then - Should get only cash payments
        assertEquals(1, cashPayments.size());
        assertEquals("cash", cashPayments.get(0).getPaymentMethod());
    }

    @Test
    void testFindByPaymentMethod_CreditCard_ShouldReturnCardPayments() {
        // WHAT: Test finding credit card payments
        // WHY: Track card transactions for reconciliation
        
        // Given - 2 credit card payments exist (from setUp)
        
        // When - Find credit card payments
        List<Payment> cardPayments = paymentRepository.findByPaymentMethod("credit_card");

        // Then - Should get 2 card payments
        assertEquals(2, cardPayments.size());
        assertTrue(cardPayments.stream().allMatch(p -> "credit_card".equals(p.getPaymentMethod())));
    }

    @Test
    void testFindByStatus_Completed_ShouldReturnCompletedPayments() {
        // WHAT: Test finding completed payments
        // WHY: Show successful transactions for reports
        
        // Given - 2 completed payments exist (from setUp)
        
        // When - Find completed payments
        List<Payment> completed = paymentRepository.findByStatus("completed");

        // Then - Should get 2 completed payments
        assertEquals(2, completed.size());
        assertTrue(completed.stream().allMatch(p -> "completed".equals(p.getStatus())));
    }

    @Test
    void testFindByStatus_Refunded_ShouldReturnRefundedPayments() {
        // WHAT: Test finding refunded payments
        // WHY: Track refunds for accounting
        
        // Given - 1 refunded payment exists (from setUp)
        
        // When - Find refunded payments
        List<Payment> refunded = paymentRepository.findByStatus("refunded");

        // Then - Should get 1 refunded payment
        assertEquals(1, refunded.size());
        assertEquals("refunded", refunded.get(0).getStatus());
    }

    @Test
    void testFindPaymentsBetween_ShouldReturnPaymentsInTimeRange() {
        // WHAT: Test finding payments within a time range
        // WHY: Generate shift reports and sales analytics
        
        // Given - Payments created at different times
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(1);
        
        // When - Find payments in time range
        List<Payment> paymentsInRange = paymentRepository.findPaymentsBetween(start, end);

        // Then - Should find all 3 payments (they were just created)
        assertEquals(3, paymentsInRange.size());
    }

    @Test
    void testCalculateTotalTips_ShouldSumTipAmounts() {
        // WHAT: Test calculating total tips in a time range
        // WHY: Track server earnings for shift reports
        
        // Given - 3 payments with tips: $10, $5, $3 (from setUp)
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(1);
        
        // When - Calculate total tips
        BigDecimal totalTips = paymentRepository.calculateTotalTips(start, end);

        // Then - Should sum to $18.00
       assertEquals(0, new BigDecimal("18.00").compareTo(totalTips));
    }

    @Test
    void testDeleteById_ShouldRemovePayment() {
        // WHAT: Test deleting a payment
        // WHY: Remove test data or cancelled transactions
        
        // Given - Cash payment exists
        Long paymentId = cashPayment.getPaymentId();
        
        // When - Delete the payment
        paymentRepository.deleteById(paymentId);

        // Then - Payment should no longer exist
        Optional<Payment> deleted = paymentRepository.findById(paymentId);
        assertFalse(deleted.isPresent());
    }

    @Test
    void testUpdate_ShouldModifyExistingPayment() {
        // WHAT: Test updating a payment's fields
        // WHY: Update payment status or amounts
        
        // Given - Cash payment exists
        Long paymentId = cashPayment.getPaymentId();
        
        // When - Update tip amount and status
        cashPayment.setTipAmount(new BigDecimal("12.00"));
        cashPayment.setStatus("adjusted");
        Payment updated = paymentRepository.save(cashPayment);

        // Then - Changes should persist
        Payment reloaded = paymentRepository.findById(paymentId).orElseThrow();
        assertEquals(new BigDecimal("12.00"), reloaded.getTipAmount());
        assertEquals("adjusted", reloaded.getStatus());
    }
}