package com.notapos.controller;

import com.notapos.entity.Payment;
import com.notapos.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
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

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

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

        mockMvc.perform(get("/api/payments"))
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

        mockMvc.perform(get("/api/payments").param("status", "completed"))
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

        mockMvc.perform(get("/api/payments").param("method", "credit_card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentMethod").value("credit_card"));

        verify(paymentService).getPaymentsByMethod("credit_card");
    }

    @Test
    void testGetPaymentById_WhenExists_ShouldReturnPayment() throws Exception {
        // WHAT: Test GET /api/payments/{id}
        // WHY: Retrieve specific payment
        
        when(paymentService.getPaymentById(1L)).thenReturn(Optional.of(testPayment));

        mockMvc.perform(get("/api/payments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(1));

        verify(paymentService).getPaymentById(1L);
    }

    @Test
    void testGetPaymentById_WhenNotExists_ShouldReturn404() throws Exception {
        // WHAT: Test GET /api/payments/{id} when not found
        // WHY: Handle missing payments
        
        when(paymentService.getPaymentById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/payments/999"))
                .andExpect(status().isNotFound());

        verify(paymentService).getPaymentById(999L);
    }

    @Test
    void testGetPaymentsByOrder_ShouldReturnOrderPayments() throws Exception {
        // WHAT: Test GET /api/payments/order/{orderId}
        // WHY: Get all payments for specific order
        
        List<Payment> payments = Arrays.asList(testPayment);
        when(paymentService.getPaymentsByOrder(1L)).thenReturn(payments);

        mockMvc.perform(get("/api/payments/order/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(1));

        verify(paymentService).getPaymentsByOrder(1L);
    }

    @Test
    void testCreatePayment_ShouldReturnCreated() throws Exception {
        // WHAT: Test POST /api/payments
        // WHY: Create new payment
        
        when(paymentService.createPayment(any(Payment.class))).thenReturn(testPayment);

        mockMvc.perform(post("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":1,\"amount\":54.00,\"paymentMethod\":\"credit_card\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(1));

        verify(paymentService).createPayment(any(Payment.class));
    }

    @Test
    void testUpdatePayment_ShouldReturnUpdated() throws Exception {
        // WHAT: Test PUT /api/payments/{id}
        // WHY: Update payment details
        
        when(paymentService.updatePayment(eq(1L), any(Payment.class))).thenReturn(testPayment);

        mockMvc.perform(put("/api/payments/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":60.00,\"tipAmount\":12.00}"))
                .andExpect(status().isOk());

        verify(paymentService).updatePayment(eq(1L), any(Payment.class));
    }

    @Test
    void testDeletePayment_ShouldReturn204() throws Exception {
        // WHAT: Test DELETE /api/payments/{id}
        // WHY: Delete payment
        
        doNothing().when(paymentService).deletePayment(1L);

        mockMvc.perform(delete("/api/payments/1"))
                .andExpect(status().isNoContent());

        verify(paymentService).deletePayment(1L);
    }
}