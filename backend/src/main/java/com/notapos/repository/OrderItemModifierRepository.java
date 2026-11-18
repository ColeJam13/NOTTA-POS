package com.notapos.repository;

import com.notapos.entity.OrderItemModifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

/**
 * Repository for OrderItemModifier entity.
 * 
 * Provides database access methods for managing order item modifiers.
 * 
 * @author CJ
 */

@Repository
public interface OrderItemModifierRepository extends JpaRepository<OrderItemModifier, Long> {

    List<OrderItemModifier> findByOrderItemId(Long orderItemId);                // Find all modifiers for a specific order item

    List<OrderItemModifier> findByModifierId(Long modifierId);                  // Find all order items that used a specific modifier

    List<OrderItemModifier> findByOrderItemIdIn(Collection<Long> orderItemIds); // Find modifiers for MANY order items in one query (avoids one query per item)

    // Remove every modifier from one order item (used when the item is deleted or its modifiers are replaced).
    // @Modifying marks this as a query that changes data instead of reading it. Returns how many rows were removed.
    @Modifying
    @Query("DELETE FROM OrderItemModifier oim WHERE oim.orderItemId = :orderItemId")
    int deleteByOrderItemId(Long orderItemId);
}