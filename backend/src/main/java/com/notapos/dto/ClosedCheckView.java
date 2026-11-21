package com.notapos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

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
 * THE TIP AND THE PAYMENT METHOD ARE NOT FOR EVERYONE. A person sees them on
 * checks they opened; a manager sees them on every check (the owner's decision
 * D10; TableOverviewService decides). For anyone else both are null here, and
 * @JsonInclude(NON_NULL) then LEAVES THE FIELD OUT of the JSON altogether:
 * the answer does not say "tipAmount": null, it does not mention a tip. So
 * the screen has one question to ask: is "tipAmount" there or not.
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
 * @param tipAmount     Tip (zero if none). Null, and so left out, when the person asking may not see it.
 * @param paymentMethod "cash", "credit_card", ... Null, and so left out, when the person asking may not
 *                      see it, or when no payment is on record.
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
        @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal tipAmount,
        @JsonInclude(JsonInclude.Include.NON_NULL) String paymentMethod,
        List<ClosedCheckItemView> items) {
}
