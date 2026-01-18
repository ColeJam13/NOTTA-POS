package com.notapos.service;
import com.notapos.entity.Payment;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.PaymentRepository;
import com.notapos.repository.TableRepository;
import com.notapos.entity.Order;
import com.notapos.entity.RestaurantTable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for Payment operations.
 * 
 * Handles payment processing, tips, and split checks.
 * 
 * @author CJ
 */

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final TableRepository tableRepository;
    
    @Autowired
    public PaymentService(PaymentRepository paymentRepository,
                         OrderRepository orderRepository,
                         TableRepository tableRepository) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.tableRepository = tableRepository;
    }

    public List<Payment> getAllPayments() {                         // Get all payments
        return paymentRepository.findAll();
    }

    public Optional<Payment> getPaymentById(Long id) {              // Get payment by ID
        return paymentRepository.findById(id);
    }

    public List<Payment> getPaymentsByOrder(Long orderId) {         // Get payment by Order
        return paymentRepository.findByOrderId(orderId);
    }

    public List<Payment> getPaymentsByMethod(String paymentMethod) {    // Get payments by method (cash, card, etc.)
        return paymentRepository.findByPaymentMethod(paymentMethod);
    }

    public List<Payment> getPaymentsByStatus(String status) {           // Get payment by status
        return paymentRepository.findByStatus(status);
    }

    public List<Payment> getPaymentsBetween(LocalDateTime start, LocalDateTime end) {           // Get payments between certain times
        return paymentRepository.findPaymentsBetween(start, end);
    }

    public BigDecimal calculateTotalTips(LocalDateTime start, LocalDateTime end) {              // Get total tips between certain times
        BigDecimal total = paymentRepository.calculateTotalTips(start, end);
        return total != null ? total : BigDecimal.ZERO;
    }

    public Payment createPayment(Payment payment) {
        // Save the payment
        Payment savedPayment = paymentRepository.save(payment);
        
        // Close the order
        Optional<Order> orderOpt = orderRepository.findById(payment.getOrderId());
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            order.setStatus("closed");
            order.setClosedAt(LocalDateTime.now());
            orderRepository.save(order);
            
            // Free up the table
            Optional<RestaurantTable> tableOpt = tableRepository.findById(order.getTableId());
            if (tableOpt.isPresent()) {
                RestaurantTable table = tableOpt.get();
                table.setStatus("available");
                tableRepository.save(table);
            }
        }
        return savedPayment;
    }

    public Payment updatePayment(Long id, Payment updatedPayment) {                                     // Update existing payment
        Payment existing = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + id));
        existing.setAmount(updatedPayment.getAmount());
        existing.setTipAmount(updatedPayment.getTipAmount());
        existing.setStatus(updatedPayment.getStatus());
        return paymentRepository.save(existing);
    }

    public void deletePayment(Long id) {                                                        // Delete existing payment
        paymentRepository.deleteById(id);
    }
}