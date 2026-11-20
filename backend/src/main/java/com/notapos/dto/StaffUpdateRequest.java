package com.notapos.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The body of PUT /api/staff/{id}: changes to a staff member.
 *
 * Every field is optional. A field that is left out (null) is not changed,
 * so the screen can send only what the manager edited - for example just a
 * new PIN.
 *
 * @param name     New name
 * @param role     New role
 * @param pin      New PIN, exactly 4 digits
 * @param isActive False to stop them signing in, true to let them again
 */
public record StaffUpdateRequest(
        @Size(min = 1, max = 50, message = "Name must be 1 to 50 characters")
        String name,

        String role,

        @Pattern(regexp = "\\d{4}", message = "PIN must be exactly 4 digits")
        String pin,

        Boolean isActive) {
}
