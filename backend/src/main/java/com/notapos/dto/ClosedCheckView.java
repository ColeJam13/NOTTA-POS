package com.notapos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One card in the CLOSED view of the Tables and Quick Orders screens: a paid
 * check with its items, amounts, and how it was paid.
 *
 * subtotal, tax, and total are the amounts recorded when the check was paid.
 * Checks closed before those were recorded hold zeros in the database; for
 * those the backend works the amounts out from the items.
 *
 * @param orderId       Order ID
 * @param tableId       Table ID
 * @param tableNumber   e.g. "W3" or "QO1"
 * @param isQuickOrder  True for a Quick Order
 * @param serverName    The server on the order
 * @param createdAt     When the order was opened
 * @param closedAt      When it was paid
 * @param subtotal      Items before tax
 * @param tax           Tax
 * @param total         Subtotal + tax (the amount charged, before tip)
 * @param tipAmount     Tip (zero if none)
 * @param paymentMethod "cash", "credit_card", ... or null if no payment is on record
 * @param items         The items on the check
 */
public record ClosedCheckView(
        Long orderId,
        Long tableId,
        String tableNumber,
        Boolean isQuickOrder,
        String serverName,
        LocalDateTime createdAt,
        LocalDateTime closedAt,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal total,
        BigDecimal tipAmount,
        String paymentMethod,
        List<ClosedCheckItemView> items) {
}
