package com.notapos.dto;

/**
 * A staff member as the frontend sees them. There is no PIN and no PIN hash
 * in here on purpose: neither ever leaves the backend.
 *
 * @param staffId  Staff ID
 * @param name     Name, as shown on orders and in the nav bar
 * @param role     "server", "bartender", "manager", "host", or "kitchen"
 * @param isActive False when they no longer work here (they cannot sign in)
 */
public record StaffView(Long staffId, String name, String role, Boolean isActive) {
}
