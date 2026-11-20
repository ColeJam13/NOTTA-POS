package com.notapos.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * The jobs a staff member can have. A person's role decides which screens
 * they see and what they are allowed to change.
 *
 * WHAT: Each constant carries the exact lowercase text stored in the database
 *       and sent to the frontend (for example MANAGER -> "manager").
 *
 * Like the other status enums, the entity field is a String column. Use
 * getValue() when setting or querying, matches() when comparing, and
 * fromValue() to turn text from a request back into a role.
 *
 * @author CJ
 */
public enum StaffRole {

    SERVER("server"),                // Takes orders at tables
    BARTENDER("bartender"),          // Takes orders and makes drinks
    MANAGER("manager"),              // Sees and can change everything
    HOST("host"),                    // Seats guests; does not take orders
    KITCHEN("kitchen");              // Makes the food; sees the kitchen display

    private final String value;

    StaffRole(String value) {
        this.value = value;
    }

    public String getValue() {                    // The text stored in the database / sent as JSON
        return value;
    }

    public boolean matches(String role) {         // Null-safe comparison against a raw role string
        return value.equals(role);
    }

    public static Optional<StaffRole> fromValue(String role) {     // "manager" -> MANAGER; empty if the text is not a role
        return Arrays.stream(values())
                .filter(candidate -> candidate.matches(role))
                .findFirst();
    }
}
