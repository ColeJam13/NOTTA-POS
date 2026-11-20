package com.notapos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything the Financials screen shows for one time period, in one response.
 *
 * All amounts cover the checks CLOSED in the period (by the time they were paid).
 *
 * @param orderCount         How many checks were closed
 * @param revenue            Sales before tax
 * @param tax                Tax collected
 * @param tips               Tips
 * @param totalCollected     revenue + tax + tips: everything that was paid
 * @param avgOrderValue      revenue / orderCount (zero when nothing was closed)
 * @param avgTipPercent      tips as a percentage of revenue, e.g. 20.6 (zero when there is no revenue)
 * @param paymentBreakdown   One row per payment method, in a fixed order
 * @param recentTransactions The 10 most recently closed checks, newest first
 */
public record FinancialsReportView(
        int orderCount,
        BigDecimal revenue,
        BigDecimal tax,
        BigDecimal tips,
        BigDecimal totalCollected,
        BigDecimal avgOrderValue,
        BigDecimal avgTipPercent,
        List<PaymentMethodTotal> paymentBreakdown,
        List<Transaction> recentTransactions) {

    /**
     * What one payment method took in.
     *
     * @param paymentMethod "cash", "credit_card", "debit_card", "gift_card", ...
     * @param amount        The check amounts paid this way (tax included, tips not)
     * @param percent       This method's share of all payment amounts, e.g. 62.5
     */
    public record PaymentMethodTotal(String paymentMethod, BigDecimal amount, BigDecimal percent) {
    }

    /**
     * One closed check in the "recent transactions" list.
     *
     * @param orderId       Order ID
     * @param tableNumber   e.g. "W3" or "QO1" (null if the table was deleted)
     * @param isQuickOrder  True for a Quick Order
     * @param serverName    The server on the order
     * @param closedAt      When it was paid
     * @param total         The check as paid, tax included
     * @param tip           Tip (zero if none)
     * @param paymentMethod How it was paid, or null if no payment is on record
     */
    public record Transaction(
            Long orderId,
            String tableNumber,
            Boolean isQuickOrder,
            String serverName,
            LocalDateTime closedAt,
            BigDecimal total,
            BigDecimal tip,
            String paymentMethod) {
    }
}
