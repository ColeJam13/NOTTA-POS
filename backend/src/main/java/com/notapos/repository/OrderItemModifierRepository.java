package com.notapos.repository;

import com.notapos.entity.OrderItemModifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
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
}