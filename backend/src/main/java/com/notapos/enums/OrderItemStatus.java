package com.notapos.enums;

/**
 * The allowed status values for an order item as it moves from the server's screen to the kitchen.
 *
 * WHAT: Each constant carries the exact lowercase text stored in the database
 *       and sent to the frontend (for example DRAFT -> "draft").
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
public enum OrderItemStatus {

    DRAFT("draft"),                  // Added to the order but not sent; fully editable
    LIMBO("limbo"),                  // Sent by the server; delay timer is counting down and the item is still editable
    PENDING("pending"),              // Timer expired (or Send Now pressed); locked and waiting for the kitchen/bar to start
    FIRED("fired"),                  // Kitchen/bar has started making it
    COMPLETED("completed");          // Finished and ready to run

    private final String value;

    OrderItemStatus(String value) {
        this.value = value;
    }

    public String getValue() {                    // The text stored in the database / sent as JSON
        return value;
    }

    public boolean matches(String status) {       // Null-safe comparison against a raw status string
        return value.equals(status);
    }
}
