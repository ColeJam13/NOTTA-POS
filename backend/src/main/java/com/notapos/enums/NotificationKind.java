package com.notapos.enums;

/**
 * The kinds of notification the bell in the top bar can hold.
 *
 * WHAT: Each constant carries the exact lowercase text stored in the database
 *       and sent to the frontend, like the status enums (see OrderItemStatus).
 *
 * WHO SENDS WHICH (the owner's decision D5): the app itself sends the first
 * three when the thing happens. A message is written by a person.
 *
 * @author CJ
 */
public enum NotificationKind {
    ITEM_86("item_86"),              // An item was 86'd. For everyone but the person who 86'd it.
    ITEM_BACK("item_back"),          // An 86'd item is back on. For everyone but the person who put it back.
    ORDER_UP("order_up"),            // Everything sent on a check has been made. For the person who opened the check.
    MESSAGE("message");              // Written by the kitchen, a bartender, or a manager. For everyone but the writer.

    private final String value;

    NotificationKind(String value) {
        this.value = value;
    }

    public String getValue() {                    // The text stored in the database / sent as JSON
        return value;
    }
}
