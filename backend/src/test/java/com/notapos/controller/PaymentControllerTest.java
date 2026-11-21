package com.notapos.controller;

import com.notapos.entity.Payment;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.exception.OrderStateException;
import com.notapos.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.notapos.testsupport.ControllerTest;
import com.notapos.testsupport.SignIn;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for PaymentController.
 * 
 * Tests REST API endpoints for payment processing and management.
 * Covers payment creation, filtering by status/method, and split checks.
 * Uses MockMvc to simulate HTTP requests without starting full server.
 * 
 * @author CJ
 */

@ControllerTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignIn signIn;              // Says who a request comes from: .with(signIn.asServer())

    @MockBean
    private PaymentService paymentService;

    private Payment testPayment;

    @BeforeEach
    void setUp() {
        testPayment = new Payment();
        testPayment.setPaymentId(1L);
        testPayment.setOrderId(1L);
        testPayment.setAmount(new BigDecimal("54.00"));
        testPayment.setPaymentMethod("credit_card");
        testPayment.setTipAmount(new BigDecimal("10.00"));
        testPayment.setStatus("completed");
    }

    @Test
    void testGetAllPayments_ShouldReturnList() throws Exception {
        // WHAT: Test GET /api/payments
        // WHY: Retrieve all payments
        
        List<Payment> payments = Arrays.asList(testPayment);
        when(paymentService.getAllPayments()).thenReturn(payments);

        mockMvc.perform(get("/api/payments").with(signIn.asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentId").value(1))
                .andExpect(jsonPath("$[0].paymentMethod").value("credit_card"));

        verify(paymentService).getAllPayments();
    }

    @Test
    void testGetAllPayments_WithStatusFilter_ShouldReturnFiltered() throws Exception {
        // WHAT: Test GET /api/payments?status=completed
        // WHY: Filter payments by status
        
        List<Payment> payments = Arrays.asList(testPayment);
        when(paymentService.getPaymentsByStatus("completed")).thenReturn(payments);

        mockMvc.perform(get("/api/payments").with(signIn.asManager()).param("status", "completed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("completed"));

        verify(paymentService).getPaymentsByStatus("completed");
    }

    @Test
    void testGetAllPayments_WithMethodFilter_ShouldReturnFiltered() throws Exception {
        // WHAT: Test GET /api/payments?method=credit_card
        // WHY: Filter payments by payment method
        
        List<Payment> payments = Arrays.asList(testPayment);
        when(paymentService.getPaymentsByMethod("credit_card")).thenReturn(payments);

        mockMvc.perform(get("/api/payments").with(signIn.asManager()).param("method", "credit_card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentMethod").value("credit_card"));

        verify(paymentService).getPaymentsByMethod("credit_card");
    }

    @Test
    void testGetPaymentById_WhenExists_ShouldReturnPayment() throws Exception {
        // WHAT: Test GET /api/payments/{id}
        // WHY: Retrieve specific payment
        
        when(paymentService.getPaymentById(1L)).thenReturn(Optional.of(testPayment));

        mockMvc.perform(get("/api/payments/1").with(signIn.asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(1));

        verify(paymentService).getPaymentById(1L);
    }

    @Test
    void testGetPaymentById_WhenNotExists_ShouldReturn404() throws Exception {
        // WHAT: Test GET /api/payments/{id} when not found
        // WHY: Handle missing payments
        
        when(paymentService.getPaymentById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/payments/999").with(signIn.asManager()))
                .andExpect(status().isNotFound());

        verify(paymentService).getPaymentById(999L);
    }

    @Test
    void testGetPaymentsByOrder_ShouldReturnOrderPayments() throws Exception {
        // WHAT: Test GET /api/payments/order/{orderId}
        // WHY: Get all payments for specific order
        
        List<Payment> payments = Arrays.asList(testPayment);
        when(paymentService.getPaymentsByOrder(1L)).thenReturn(payments);

        mockMvc.perform(get("/api/payments/order/1").with(signIn.asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(1));

        verify(paymentService).getPaymentsByOrder(1L);
    }

    @Test
    void testCreatePayment_ShouldReturnCreated() throws Exception {
        // WHAT: Test POST /api/payments
        // WHY: Create new payment
        
        when(paymentService.createPayment(any(Payment.class))).thenReturn(testPayment);

        mockMvc.perform(post("/api/payments").with(signIn.asServer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":1,\"amount\":54.00,\"paymentMethod\":\"credit_card\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(1));

        verify(paymentService).createPayment(any(Payment.class));
    }

    @Test
    void testCreatePayment_WhenOrderCannotBePaid_ShouldReturn409WithTheReason() throws Exception {
        // WHAT: Test POST /api/payments when the order is not ready to be paid
        // WHY: The Payment screen shows this message to the server as-is

        when(paymentService.createPayment(any(Payment.class))).thenThrow(new OrderStateException(
                "This order has 2 unsent item(s). Send or remove them before closing the check."));

        mockMvc.perform(post("/api/payments").with(signIn.asServer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":1,\"amount\":54.00,\"paymentMethod\":\"credit_card\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "This order has 2 unsent item(s). Send or remove them before closing the check."));
    }

    @Test
    void testCreatePayment_WhenOrderNotFound_ShouldReturn404() throws Exception {
        // WHAT: Test POST /api/payments for an order that does not exist
        // WHY: A payment must belong to a real order

        when(paymentService.createPayment(any(Payment.class)))
                .thenThrow(new ResourceNotFoundException("Order not found with id: 99"));

        mockMvc.perform(post("/api/payments").with(signIn.asServer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":99,\"amount\":54.00,\"paymentMethod\":\"credit_card\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreatePayment_WithNegativeTip_ShouldReturn400() throws Exception {
        // WHAT: Test POST /api/payments with a tip below zero
        // WHY: A negative tip would quietly take money off the check

        mockMvc.perform(post("/api/payments").with(signIn.asServer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":1,\"amount\":54.00,\"tipAmount\":-5.00,\"paymentMethod\":\"credit_card\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.tipAmount").value("Tip cannot be negative"));

        verify(paymentService, never()).createPayment(any());
    }
}
