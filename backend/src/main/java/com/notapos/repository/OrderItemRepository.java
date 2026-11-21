package com.notapos.repository;

import com.notapos.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Collection;
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

    // Find all items for specific order, in the order the server rang them in.
    // (A list with no ORDER BY comes back however the database likes, and that changes
    // as rows are updated. The sort is written here once so no caller can forget it:
    // it is the same rule as OrderItem.IN_RING_IN_ORDER.)
    @Query("SELECT oi FROM OrderItem oi WHERE oi.orderId = :orderId ORDER BY oi.ringInPosition, oi.orderItemId")
    List<OrderItem> findByOrderId(Long orderId);

    // The highest position in use on an order, or -1 if the order has no items yet.
    // An item rung in without a position of its own goes one after this.
    // COALESCE(a, b) means "a, or b when a is missing": MAX of no rows is missing.
    @Query("SELECT COALESCE(MAX(oi.ringInPosition), -1) FROM OrderItem oi WHERE oi.orderId = :orderId")
    int findHighestRingInPosition(Long orderId);

    List<OrderItem> findByStatus(String status);            // Find items by status

    List<OrderItem> findByIsLocked(Boolean isLocked);         // Find all unlocked items (still editable)

    List<OrderItem> findByOrderIdAndStatus(Long orderId, String status);        // Find draft items (not sent yet)

    @Query("SELECT oi FROM OrderItem oi WHERE oi.delayExpiresAt <= :now and oi.isLocked = :isLocked")       // Find item whose delay has expired but aren't locked yet
    List<OrderItem> findExpiredUnlockedItems(LocalDateTime now, Boolean isLocked);

    @Query("SELECT oi FROM OrderItem oi WHERE oi.orderId = :orderId AND oi.status = :status")               // Find pending items for an order (timer active)
    List<OrderItem> findPendingItemsByOrder(Long orderId, String status);

    List<OrderItem> findByOrderIdIn(Collection<Long> orderIds);     // Find items for MANY orders in one query (avoids one query per order)

    List<OrderItem> findByMenuItemId(Long menuItemId);              // Find items by menu item ID
}
