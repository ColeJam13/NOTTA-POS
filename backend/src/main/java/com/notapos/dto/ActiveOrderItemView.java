package com.notapos.dto;

import java.math.BigDecimal;

/**
 * One line on an active order ticket, ready for the screen.
 *
 * Unlike the raw OrderItem entity (which only holds a menuItemId), this
 * already includes the menu item's name, so the frontend does not need a
 * second request to look it up.
 *
 * A "record" is a compact Java class for holding data: the fields listed in
 * the parentheses automatically get a constructor and read methods.
 *
 * @author CJ
 */
public record ActiveOrderItemView(
        Long orderItemId,
        Long menuItemId,
        String name,                    // Menu item name, e.g. "Chicken Cutty"
        Integer quantity,
        BigDecimal price,
        String status,                  // draft, limbo, pending, fired, completed
        String specialInstructions) {
}
