package com.notapos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An open check found by the search box.
 *
 * It carries its table's fields because choosing it opens the order screen for
 * that table.
 *
 * @param orderId      The check number
 * @param tableId      Table ID
 * @param tableNumber  e.g. "W3" or "QO1"
 * @param status       The table's status ("occupied")
 * @param isQuickOrder True for a Quick Order
 * @param serverName   Who opened the check
 * @param itemCount    How many items are on it, sent or not
 * @param subtotal     Sum of price x quantity for every item on it (before tax)
 * @param openedAt     When it was opened
 */
public record SearchOpenCheckView(
        Long orderId,
        Long tableId,
        String tableNumber,
        String status,
        Boolean isQuickOrder,
        String serverName,
        int itemCount,
        BigDecimal subtotal,
        LocalDateTime openedAt) {
}
