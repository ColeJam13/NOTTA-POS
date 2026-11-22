package com.notapos.dto;

import java.math.BigDecimal;

/**
 * A menu item found by the search box: what it costs, and whether it can be
 * ordered right now.
 *
 * @param menuItemId Menu item ID
 * @param name       e.g. "Garden Toast"
 * @param category   e.g. "Savory"
 * @param price      The menu price, before any modifier upcharges
 * @param is86d      True when the kitchen is out of it for now
 */
public record SearchMenuItemView(
        Long menuItemId,
        String name,
        String category,
        BigDecimal price,
        Boolean is86d) {
}
