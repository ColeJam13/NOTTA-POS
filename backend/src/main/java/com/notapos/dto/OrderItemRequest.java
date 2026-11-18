package com.notapos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * What the order screen sends to ring in an item that has modifiers and/or a
 * kitchen note.
 *
 * WHY A SEPARATE CLASS: There is no price in here on purpose. The backend
 * looks up the menu price and the modifier upcharges itself and adds them
 * together, so the amount charged never depends on what the browser sends.
 *
 * @author CJ
 */
public record OrderItemRequest(
        @NotNull(message = "An order item must belong to an order")
        Long orderId,

        @NotNull(message = "An order item must reference a menu item")
        Long menuItemId,

        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity,               // Optional - treated as 1 when left out

        @Size(max = 500, message = "Kitchen note cannot be longer than 500 characters")
        String specialInstructions,     // Free-text kitchen note, e.g. "no onions" (optional)

        List<Long> modifierIds) {       // IDs of the chosen modifiers (optional - may be empty)
}
