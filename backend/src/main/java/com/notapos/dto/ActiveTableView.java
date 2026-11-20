package com.notapos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One card in the ACTIVE view of the Tables and Quick Orders screens: a table
 * with an open check, and a summary of what is on it.
 *
 * The table's own fields are included because tapping the card opens the
 * order screen for that table.
 *
 * @param tableId        Table ID
 * @param tableNumber    e.g. "W3" or "QO1"
 * @param status         The table's status ("occupied")
 * @param serverName     The server assigned to the table
 * @param isQuickOrder   True for a Quick Order
 * @param itemCount      How many items are on the open check, sent or not
 * @param limboCount     Items still in their edit window
 * @param pendingCount   Items locked and waiting for the kitchen
 * @param firedCount     Items the kitchen has started
 * @param completedCount Items the kitchen has finished
 * @param subtotal       Sum of price x quantity for every item on the check (before tax)
 * @param openedAt       When the check was opened
 */
public record ActiveTableView(
        Long tableId,
        String tableNumber,
        String status,
        String serverName,
        Boolean isQuickOrder,
        int itemCount,
        int limboCount,
        int pendingCount,
        int firedCount,
        int completedCount,
        BigDecimal subtotal,
        LocalDateTime openedAt) {
}
