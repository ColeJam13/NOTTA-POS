package com.notapos.dto;

import java.math.BigDecimal;

/**
 * One choice inside a modifier group, ready for the order screen's picker.
 * Example: "French Toast" with a $2.00 upcharge.
 *
 * @author CJ
 */
public record ModifierOptionView(
        Long modifierId,
        String name,                    // e.g. "French Toast"
        BigDecimal priceAdjustment) {   // Upcharge added to the item price (0.00 when the choice is free)
}
