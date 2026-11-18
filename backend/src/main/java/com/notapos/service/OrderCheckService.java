package com.notapos.service;

import com.notapos.dto.OrderCheckView;
import com.notapos.entity.Order;
import com.notapos.entity.OrderItem;
import com.notapos.enums.OrderItemStatus;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Works out the check for an order: subtotal, tax, and total.
 *
 * WHAT: The single place on the backend that adds up what a guest owes.
 * WHY:  PaymentService needs the amount due to take a payment, and the Payment
 *       screen needs the same numbers to show the guest. Both ask here, so
 *       the number on screen and the number charged can never differ.
 *
 * The tax rate comes from notta.tax-rate in application.properties.
 *
 * @author CJ
 */
@Service
@Transactional(readOnly = true)      // Every method runs in a database transaction; read-only unless a method says otherwise
public class OrderCheckService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final BigDecimal taxRate;                       // As a decimal: 0.03 = 3%

    @Autowired
    public OrderCheckService(OrderRepository orderRepository,
                             OrderItemRepository orderItemRepository,
                             @Value("${notta.tax-rate:0.03}") BigDecimal taxRate) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.taxRate = taxRate;
    }

    public OrderCheckView getCheck(Long orderId) {                      // Get the check for an order (404 if the order does not exist)
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return buildCheck(order);
    }

    public OrderCheckView buildCheck(Order order) {                     // Add up the check for an order that is already loaded
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getOrderId());

        BigDecimal subtotal = BigDecimal.ZERO;
        int itemCount = 0;
        int unsentItemCount = 0;

        for (OrderItem item : items) {
            // A draft was saved but never sent, so the kitchen never made it: it is not on the check
            if (OrderItemStatus.DRAFT.matches(item.getStatus())) {
                unsentItemCount++;
                continue;
            }

            // Price is for ONE item and already includes modifier upcharges
            subtotal = subtotal.add(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            itemCount++;
        }

        // Round to whole cents. HALF_UP is the rounding people expect: half a cent rounds up.
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(tax);

        return new OrderCheckView(
                order.getOrderId(),
                order.getStatus(),
                itemCount,
                unsentItemCount,
                subtotal,
                tax,
                total,
                taxRate);
    }
}
