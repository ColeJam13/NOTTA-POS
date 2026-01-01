package com.notapos.repository;

import com.notapos.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for OrderItem entity.
 * 
 * Provides database access methods for managing order items.
 * Includes queries for the delay timer functionality.
 * 
 * @author CJ
 */

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);            // Find all items for specific order

    List<OrderItem> findByStatus(String status);            // Find items by status

    List<OrderItem> findByIsLocked(Boolean isLocked);         // Find all unlocked items (still editable)

    List<OrderItem> findByOrderIdAndStatus(Long orderId, String status);        // Find draft items (not sent yet)

    @Query("SELECT oi FROM OrderItem oi WHERE oi.delayExpiresAt <= :now and oi.isLocked = :isLocked")       // Find item whose delay has expired but aren't locked yet
    List<OrderItem> findExpiredUnlockedItems(LocalDateTime now, Boolean isLocked);

    @Query("SELECT oi FROM OrderItem oi WHERE oi.orderId = :orderId AND oi.status = :status")               // Find pending items for an order (timer active)
    List<OrderItem> findPendingItemsByOrder(Long orderId, String status);

    List<OrderItem> findByMenuItemId(Long menuItemId);              // Find items by menu item ID
}
