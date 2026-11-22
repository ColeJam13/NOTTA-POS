package com.notapos.dto;

/**
 * A table found by the search box.
 *
 * It carries the table's own fields because choosing it opens the order screen
 * for that table, which needs them (the same reason ActiveTableView does).
 *
 * @param tableId      Table ID
 * @param tableNumber  e.g. "W3"
 * @param section      Where on the floor, e.g. "Window"
 * @param seatCount    How many seats
 * @param status       "available" or "occupied"
 * @param isQuickOrder Always false here: a Quick Order is found as a check, not as a table
 */
public record SearchTableView(
        Long tableId,
        String tableNumber,
        String section,
        Integer seatCount,
        String status,
        Boolean isQuickOrder) {
}
