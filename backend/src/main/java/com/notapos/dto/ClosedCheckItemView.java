package com.notapos.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One line on a closed check: the item, what it cost, and how it was ordered.
 *
 * @param orderItemId         Order item ID
 * @param name                Menu item name
 * @param quantity            How many
 * @param price               Price of ONE, modifier upcharges already included
 * @param specialInstructions The kitchen note, or null
 * @param modifiers           The modifiers chosen for it (empty if none)
 */
public record ClosedCheckItemView(
        Long orderItemId,
        String name,
        Integer quantity,
        BigDecimal price,
        String specialInstructions,
        List<OrderItemModifierView> modifiers) {
}
