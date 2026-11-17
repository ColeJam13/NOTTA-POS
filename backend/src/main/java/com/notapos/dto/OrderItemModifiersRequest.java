package com.notapos.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * What the order screen sends to change the modifiers and kitchen note on an
 * item that is already saved (and not yet locked).
 *
 * The lists REPLACE what the item had before: whatever is sent here is the
 * item's complete new set of modifiers.
 *
 * @author CJ
 */
public record OrderItemModifiersRequest(
        @Size(max = 500, message = "Kitchen note cannot be longer than 500 characters")
        String specialInstructions,     // New kitchen note (blank or missing = no note)

        List<Long> modifierIds) {       // The item's complete new set of modifier IDs (may be empty)
}
