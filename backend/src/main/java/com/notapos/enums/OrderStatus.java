package com.notapos.enums;

/**
 * The allowed status values for an order (the whole check/tab for a table).
 *
 * WHAT: Each constant carries the exact lowercase text stored in the database
 *       and sent to the frontend (for example OPEN -> "open").
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
public enum OrderStatus {

    OPEN("open"),                    // Guests are still ordering; items can be added
    CLOSED("closed");                // Paid and finished

    private final String value;

    OrderStatus(String value) {
        this.value = value;
    }

    public String getValue() {                    // The text stored in the database / sent as JSON
        return value;
    }

    public boolean matches(String status) {       // Null-safe comparison against a raw status string
        return value.equals(status);
    }
}
