package com.notapos.exception;

/**
 * Thrown when the modifiers chosen for an order item break the menu's rules:
 * a required group was not answered, too many were picked from one group, or
 * a modifier does not belong to that menu item at all.
 *
 * GlobalExceptionHandler turns this into an HTTP 400 Bad Request response
 * ("the request itself is wrong - fix it and send it again").
 *
 * @author CJ
 */
public class InvalidModifierSelectionException extends RuntimeException {

    public InvalidModifierSelectionException(String message) {
        super(message);
    }
}
