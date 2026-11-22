package com.notapos.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * What each person sold in one time period: the leaderboard, in one response.
 *
 * Covers the checks CLOSED in the period, and gives each to the person who OPENED it:
 * the same two rules the Financials report and tips follow. A check with nobody on
 * record (closed before staff profiles existed) belongs to nobody and is in no row.
 *
 * There is no tip anywhere in this answer, on purpose. Everyone signed in may read it
 * (the owner's decision D6), and a tip is shown only to the person who earned it and
 * to managers (D10). Do not add one, not even to hide on the screen.
 *
 * @param staff One row per person who opened at least one check closed in the period,
 *              best sales first. Empty when nobody sold anything.
 */
public record StaffSalesView(List<Row> staff) {

    /**
     * One person's period.
     *
     * @param staffId      Who. The screen marks the signed-in person's own row by this, not by name
     * @param name         Their name as it is today (someone since deactivated still appears)
     * @param rank         1 for the most sales. People with equal sales share a rank, and the
     *                     next rank is skipped: 1, 1, 3
     * @param checksClosed How many of their checks were closed
     * @param sales        Those checks added up, before tax: the figure Financials calls revenue
     * @param checkAverage sales / checksClosed, to the cent
     * @param biggestCheck Their largest single check, before tax
     * @param itemsSold    The items on those checks, counted by quantity
     */
    public record Row(
            Long staffId,
            String name,
            int rank,
            int checksClosed,
            BigDecimal sales,
            BigDecimal checkAverage,
            BigDecimal biggestCheck,
            long itemsSold) {
    }
}
