package com.notapos.repository;

import com.notapos.entity.Payment;
import com.notapos.entity.Order;
import com.notapos.entity.RestaurantTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for PaymentRepository.
 * 
 * Tests database queries for payment management using PostgreSQL Testcontainer.
 * 
 * CHANGES FROM ORIGINAL:
 * - Now extends BaseRepositoryTest (provides PostgreSQL container)
 * - Removed @DataJpaTest, @AutoConfigureTestDatabase, @ActiveProfiles (inherited from base)
 * - Creates actual Order and RestaurantTable entities first (proper foreign key handling)
 * - Tests now run against real PostgreSQL 16 in Docker
 * 
 * @author CJ
 */
class PaymentRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private TableRepository tableRepository;

    private RestaurantTable table1;
    private RestaurantTable table2;
    private RestaurantTable table3;
    private Order order1;
    private Order order2;
    private Order order3;
    private Payment cashPayment;
    private Payment cardPayment;
    private Payment refundedPayment;

    @BeforeEach
    void setUp() {
        // Clear database before each test (in proper order due to foreign keys)
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        tableRepository.deleteAll();

        // Create tables first
        table1 = new RestaurantTable();
        table1.setTableNumber("F1");
        table1.setSection("Front");
        table1.setSeatCount(2);
        table1.setStatus("occupied");
        table1 = tableRepository.save(table1);

        table2 = new RestaurantTable();
        table2.setTableNumber("F2");
        table2.setSection("Front");
        table2.setSeatCount(4);
        table2.setStatus("occupied");
        table2 = tableRepository.save(table2);

        table3 = new RestaurantTable();
        table3.setTableNumber("B1");
        table3.setSection("Back");
        table3.setSeatCount(4);
        table3.setStatus("available");
        table3 = tableRepository.save(table3);

        // Create orders
        order1 = new Order();
        order1.setTableId(table1.getTableId());
        order1.setOrderType("dine_in");
        order1.setStatus("open");
        order1.setSubtotal(new BigDecimal("80.00"));
        order1.setTax(new BigDecimal("6.40"));
        order1.setTotal(new BigDecimal("86.40"));
        order1 = orderRepository.save(order1);

        order2 = new Order();
        order2.setTableId(table2.getTableId());
        order2.setOrderType("dine_in");
        order2.setStatus("open");
        order2.setSubtotal(new BigDecimal("25.00"));
        order2.setTax(new BigDecimal("2.00"));
        order2.setTotal(new BigDecimal("27.00"));
        order2 = orderRepository.save(order2);

        order3 = new Order();
        order3.setTableId(table3.getTableId());
        order3.setOrderType("takeout");
        order3.setStatus("open");
        order3.setSubtotal(new BigDecimal("45.00"));
        order3.setTax(new BigDecimal("3.60"));
        order3.setTotal(new BigDecimal("48.60"));
        order3 = orderRepository.save(order3);

        // Now create payments with valid order foreign keys
        // Create cash payment for order 1
        cashPayment = new Payment();
        cashPayment.setOrderId(order1.getOrderId());
        cashPayment.setAmount(new BigDecimal("50.00"));
        cashPayment.setPaymentMethod("cash");
        cashPayment.setTipAmount(new BigDecimal("10.00"));
        cashPayment.setStatus("completed");
        cashPayment.setTransactionReference("CASH-001");
        cashPayment = paymentRepository.save(cashPayment);

        // Create credit card payment for order 1 (split check)
        cardPayment = new Payment();
        cardPayment.setOrderId(order1.getOrderId());
        cardPayment.setAmount(new BigDecimal("30.00"));
        cardPayment.setPaymentMethod("credit_card");
        cardPayment.setTipAmount(new BigDecimal("5.00"));
        cardPayment.setStatus("completed");
        cardPayment.setTransactionReference("CC-12345");
        cardPayment = paymentRepository.save(cardPayment);

        // Create refunded payment for order 2
        refundedPayment = new Payment();
        refundedPayment.setOrderId(order2.getOrderId());
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
        
        // Given - New payment for order 3
        Payment newPayment = new Payment();
        newPayment.setOrderId(order3.getOrderId());
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
        List<Payment> order1Payments = paymentRepository.findByOrderId(order1.getOrderId());

        // Then - Should get 2 payments for Order 1
        assertEquals(2, order1Payments.size());
        assertTrue(order1Payments.stream().allMatch(p -> p.getOrderId().equals(order1.getOrderId())));
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