package com.notapos.dto;

/**
 * A staff member as the frontend sees them. There is no PIN and no PIN hash
 * in here on purpose: neither ever leaves the backend.
 *
 * @param staffId  Staff ID
 * @param name     Name, as shown on orders and in the nav bar
 * @param role     "server", "bartender", "manager", "host", or "kitchen"
 * @param isActive False when they no longer work here (they cannot sign in)
 * @param mustChangePin True while they hold the starter PIN. The frontend shows the
 *                 "choose a new PIN" screen and nothing else; the backend refuses
 *                 everything else too.
 */
public record StaffView(Long staffId, String name, String role, Boolean isActive, Boolean mustChangePin) {
}
