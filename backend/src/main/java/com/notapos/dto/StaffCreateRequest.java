package com.notapos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The body of POST /api/staff: a new staff member.
 *
 * @param name Their name
 * @param role "server", "bartender", "manager", "host", or "kitchen"
 * @param pin  Their sign-in PIN, exactly 4 digits
 */
public record StaffCreateRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 50, message = "Name can be at most 50 characters")
        String name,

        @NotBlank(message = "Role is required")
        String role,

        @NotNull(message = "PIN is required")
        @Pattern(regexp = "\\d{4}", message = "PIN must be exactly 4 digits")
        String pin) {
}
