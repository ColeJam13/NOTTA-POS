package com.notapos.repository;

import com.notapos.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Order entity.
 * 
 * @author CJ
 */

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByTableId(Long tableId);            // Find all orders for specific table

    List<Order> findByStatus(String status);            // Find all orders with specific status

    List<Order> findByTableIdAndStatus(Long tableId, String status);                        // Find orders by status and table

    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :start AND :end")               // Find orders created within a time range
    List<Order> findOrdersBetween(LocalDateTime start, LocalDateTime end);
}
