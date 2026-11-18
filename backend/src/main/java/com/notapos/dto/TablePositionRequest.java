package com.notapos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Where one table sits on the Floor Map. The Floor Map's edit mode sends a
 * list of these when the manager presses Save.
 *
 * Only the layout is here on purpose. A table's number, seats, status, and
 * server cannot be changed through the layout endpoint, so dragging tables
 * around can never mark an occupied table as free.
 *
 * The fields are named x and y (not xPosition / yPosition) to stay clear of
 * the JSON quirk on RestaurantTable, where those serialize as "xposition".
 *
 * @param tableId  The table to move
 * @param x        Pixels from the left edge of the floor canvas
 * @param y        Pixels from the top edge of the floor canvas
 * @param rotation Degrees clockwise, 0 to 359
 */
public record TablePositionRequest(
        @NotNull(message = "Table ID is required")
        Long tableId,

        @NotNull(message = "x is required")
        @Min(value = 0, message = "x cannot be negative")
        Integer x,

        @NotNull(message = "y is required")
        @Min(value = 0, message = "y cannot be negative")
        Integer y,

        @NotNull(message = "Rotation is required")
        @Min(value = 0, message = "Rotation must be between 0 and 359")
        @Max(value = 359, message = "Rotation must be between 0 and 359")
        Integer rotation) {
}
