package com.notapos.service;

import org.springframework.transaction.annotation.Transactional;
import com.notapos.entity.OrderItemModifier;
import com.notapos.repository.OrderItemModifierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for OrderItemModifier operations.
 * 
 * Contains business logic for managing order item modifiers.
 * 
 * @author CJ
 */

@Service
@Transactional(readOnly = true)      // Every method runs in a database transaction; read-only unless a method says otherwise
public class OrderItemModifierService {
    private final OrderItemModifierRepository orderItemModifierRepository;

    @Autowired
    public OrderItemModifierService(OrderItemModifierRepository orderItemModifierRepository) {
        this.orderItemModifierRepository = orderItemModifierRepository;
    }

    public List<OrderItemModifier> getAllOrderItemModifiers() {                                     // Get all order item modifiers
        return orderItemModifierRepository.findAll();
    }

    public Optional<OrderItemModifier> getOrderItemModifierById(Long id) {                          // Get order item modifier by ID
        return orderItemModifierRepository.findById(id);
    }

    public List<OrderItemModifier> getModifiersForOrderItem(Long orderItemId) {                     // Get all modifiers for an order item
        return orderItemModifierRepository.findByOrderItemId(orderItemId);
    }

    public List<OrderItemModifier> getOrderItemsWithModifier(Long modifierId) {                     // Get all order items that used a specific modifier
        return orderItemModifierRepository.findByModifierId(modifierId);
    }

    @Transactional
    public OrderItemModifier createOrderItemModifier(OrderItemModifier orderItemModifier) {         // Create a new order item modifier
        return orderItemModifierRepository.save(orderItemModifier);
    }

    @Transactional
    public void deleteOrderItemModifier(Long id) {                                                  // Delete an order item modifier
        orderItemModifierRepository.deleteById(id);
    }
}
