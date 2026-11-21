package com.notapos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * The body of PUT /api/staff/me/pin: a person choosing a new PIN for themselves.
 *
 * @param currentPin The PIN they have now. Asked for again even though they are
 *                   signed in, so that someone walking up to a terminal another
 *                   person left signed in cannot take that person's sign-in over.
 * @param newPin     The PIN they want, exactly 4 digits
 */
public record ChangePinRequest(
        @NotNull(message = "Current PIN is required")
        @Pattern(regexp = "\\d{4}", message = "Current PIN must be exactly 4 digits")
        String currentPin,

        @NotNull(message = "New PIN is required")
        @Pattern(regexp = "\\d{4}", message = "New PIN must be exactly 4 digits")
        String newPin) {
}
