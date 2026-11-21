package com.notapos.controller;

import jakarta.validation.Valid;
import com.notapos.entity.Payment;
import com.notapos.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import com.notapos.security.Permissions;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * REST API Controller for Payment operations
 * 
 * @author CJ
 */

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @Autowired
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PreAuthorize(Permissions.MANAGER_ONLY)                                     // Managers only
    @GetMapping
    public ResponseEntity<List<Payment>> getAllPayments(                                    // Get all payments
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String method) {

        if (status != null) {
            return ResponseEntity.ok(paymentService.getPaymentsByStatus(status));
        }
        if (method != null) {
            return ResponseEntity.ok(paymentService.getPaymentsByMethod(method));
        }
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    @PreAuthorize(Permissions.MANAGER_ONLY)                                     // Managers only
    @GetMapping("/{id}")
    public ResponseEntity<Payment> getPaymentById(@PathVariable Long id) {                      // Get payments by ID
        return paymentService.getPaymentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize(Permissions.MANAGER_ONLY)                                     // Managers only
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<Payment>> getPaymentsByOrder(@PathVariable Long orderId) {           // Get payment by orderID
        return ResponseEntity.ok(paymentService.getPaymentsByOrder(orderId));
    }

    @PreAuthorize(Permissions.TAKE_ORDERS)                                      // Anyone who takes orders
    @PostMapping
    public ResponseEntity<Payment> createPayment(@Valid @RequestBody Payment payment) {                    // Create new payment
        Payment created = paymentService.createPayment(payment);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // There is no endpoint to change or delete a payment. A payment is a record of money
    // taken: rewriting one after the fact is not something any screen should be able to do.
    // Refunds and voids are their own feature (step B5 of the Phase 2 plan), which will add
    // to the record, not edit it.
}
