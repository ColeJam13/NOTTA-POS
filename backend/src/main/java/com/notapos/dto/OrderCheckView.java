package com.notapos.dto;

import java.math.BigDecimal;

/**
 * The check for one order: what the guest owes right now.
 *
 * WHAT: The numbers the Payment screen shows before taking payment, worked
 *       out by the backend from the order's saved items.
 * WHY:  The amount charged used to be whatever the browser had added up from
 *       the items on its own screen. If the screen held an item that was never
 *       saved, or was missing one another server had added, the guest was
 *       charged the wrong amount. Now the backend's number is the only one
 *       that counts (see OrderCheckService and PaymentService).
 *
 * Items the server has not sent yet (drafts) are NOT on the check. They are
 * counted in unsentItemCount so the screen can say "send or remove these first".
 *
 * @param orderId         The order this check is for
 * @param status          The order's status ("open" or "closed")
 * @param itemCount       How many sent items are on the check
 * @param unsentItemCount How many saved items are still drafts (not on the check)
 * @param subtotal        Sum of price x quantity for the sent items (prices already include modifier upcharges)
 * @param tax             Subtotal x tax rate, rounded to cents
 * @param total           Subtotal + tax - the amount due before tip
 * @param taxRate         The tax rate used, as a decimal (0.03 = 3%)
 */
public record OrderCheckView(
        Long orderId,
        String status,
        int itemCount,
        int unsentItemCount,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal total,
        BigDecimal taxRate) {
}
