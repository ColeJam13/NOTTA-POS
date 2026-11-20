package com.notapos.repository;

import com.notapos.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Repository for Payment entity.
 * 
 * @author CJ
 */

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderId(Long orderId);                                      // Find order by ID

    List<Payment> findByOrderIdIn(Collection<Long> orderIds);                       // Find payments for MANY orders in one query (avoids one query per order)

    List<Payment> findByPaymentMethod(String paymentMethod);                        // Find order by payment method

    List<Payment> findByStatus(String status);                                      // Find order by status

    @Query("SELECT p FROM Payment p WHERE p.createdAt BETWEEN :start AND :end")                     // Find payments between certain times
    List<Payment> findPaymentsBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT SUM(p.tipAmount) FROM Payment p WHERE p.createdAt BETWEEN :start AND :end")      // Find total tips between certain times
    BigDecimal calculateTotalTips(LocalDateTime start, LocalDateTime end);
}