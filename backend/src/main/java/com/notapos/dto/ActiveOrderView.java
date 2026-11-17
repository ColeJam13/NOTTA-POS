package com.notapos.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One open order with everything the Active Orders screen needs to draw it:
 * the order, the table it belongs to, and its items (with names).
 *
 * WHY: The screen used to download ALL orders, ALL order items, ALL menu
 *      items, and ALL tables, then match them up in the browser - and repeat
 *      two of those downloads every 3 seconds. This is one request that
 *      returns only open orders, already matched up.
 *
 * @author CJ
 */
public record ActiveOrderView(
        Long orderId,
        Long tableId,
        String tableNumber,             // e.g. "W3" or "QO1" (null if the table was deleted)
        Boolean isQuickOrder,
        String serverName,
        LocalDateTime createdAt,
        List<ActiveOrderItemView> items) {
}
