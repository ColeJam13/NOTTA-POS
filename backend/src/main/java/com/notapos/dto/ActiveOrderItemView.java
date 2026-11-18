package com.notapos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One line on an active order ticket, ready for the screen.
 *
 * Unlike the raw OrderItem entity (which only holds a menuItemId), this
 * already includes the menu item's name, so the frontend does not need a
 * second request to look it up. It also carries the item's modifiers and
 * kitchen note, which is everything the kitchen needs to make the dish.
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
        String specialInstructions,     // Free-text kitchen note, e.g. "no onions" (null when there is none)
        List<OrderItemModifierView> modifiers,      // Chosen modifiers with names, e.g. "French Toast" (empty when there are none)
        Long prepStationId,             // Which station makes it (Kitchen, Bar, ...). Null if the menu item has no station.
        LocalDateTime sentAt) {         // When the server pressed Send. The kitchen view shows how long a ticket has been waiting. Null for drafts.
}
