package com.notapos.enums;

/**
 * The allowed status values for a physical table.
 *
 * WHAT: Each constant carries the exact lowercase text stored in the database
 *       and sent to the frontend (for example AVAILABLE -> "available").
 * WHY:  These used to be typed as raw strings all over the services. A typo
 *       like "penidng" would compile and silently break the flow. With an enum
 *       the compiler catches it.
 *
 * NOTE: The entity field is still a String column, so the database and the
 *       JSON the frontend receives are unchanged. Use getValue() when setting
 *       or querying, and matches() when comparing.
 *
 * @author CJ
 */
public enum TableStatus {

    AVAILABLE("available"),          // Empty and ready to be sat
    OCCUPIED("occupied");            // Has an open order

    private final String value;

    TableStatus(String value) {
        this.value = value;
    }

    public String getValue() {                    // The text stored in the database / sent as JSON
        return value;
    }

    public boolean matches(String status) {       // Null-safe comparison against a raw status string
        return value.equals(status);
    }
}
