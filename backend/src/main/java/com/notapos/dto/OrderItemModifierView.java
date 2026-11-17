package com.notapos.dto;

import java.math.BigDecimal;

/**
 * One modifier that was chosen for an order item, ready for the screen.
 * Example: order item 42 (Combo Plate) has "French Toast" at +$2.00.
 *
 * Unlike the raw OrderItemModifier entity (which only holds a modifierId),
 * this already includes the modifier's name for the kitchen ticket.
 *
 * @author CJ
 */
public record OrderItemModifierView(
        Long orderItemId,               // The order item this modifier belongs to
        Long modifierId,
        String name,                    // e.g. "French Toast"
        BigDecimal priceAdjustment) {   // The upcharge that was charged when the item was rung in
}
