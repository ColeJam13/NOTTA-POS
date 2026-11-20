package com.notapos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * The body of POST /api/staff/sign-in: the PIN that was typed.
 *
 * @param pin Exactly 4 digits
 */
public record SignInRequest(
        @NotNull(message = "PIN is required")
        @Pattern(regexp = "\\d{4}", message = "PIN must be exactly 4 digits")
        String pin) {
}
