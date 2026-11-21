package com.notapos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * The body of POST /api/staff/paused-devices/release: which paused terminal a
 * manager is letting sign in again.
 *
 * The address travels in the body, not in the URL: an address is full of dots
 * and (for IPv6) colons, which are awkward inside a path.
 *
 * It is checked to look like an address (letters, digits, dots, colons, and a
 * few joining characters) because it is written to the log, and text with a
 * line break in it could be used to fake a log line.
 *
 * @param device The terminal's address, exactly as GET /api/staff/paused-devices listed it
 */
public record ReleaseDeviceRequest(
        @NotBlank(message = "Say which terminal to release")
        @Pattern(regexp = "[0-9A-Za-z:.%_-]{1,64}", message = "That is not a terminal's address")
        String device) {
}
