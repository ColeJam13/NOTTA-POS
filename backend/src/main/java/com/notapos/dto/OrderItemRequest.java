package com.notapos.dto;

import jakarta.validation.constraints.Max;
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

        List<Long> modifierIds,         // IDs of the chosen modifiers (optional - may be empty)

        // Where the item sits on the order as the server entered it: 0 for the first item,
        // 1 for the next. The screen sends it because a round of items is saved with several
        // requests at once, and only the screen knows which was tapped first (see
        // OrderItem.IN_RING_IN_ORDER). Optional: an item rung in without one goes after
        // everything already on the order.
        @Min(value = 0, message = "An item's position on the order cannot be below zero")
        @Max(value = MAX_RING_IN_POSITION, message = "An item's position on the order is too large")
        Integer ringInPosition) {

    // Far more items than any check will ever hold. The limit is here so a made-up
    // request cannot store a number so large that "one more than the highest" no
    // longer fits in the column.
    public static final long MAX_RING_IN_POSITION = 100_000;
}
