package com.notapos.exception;

/**
 * Thrown when someone tries to edit or delete an order item that has already
 * been locked and sent to the kitchen/bar.
 *
 * GlobalExceptionHandler turns this into an HTTP 409 Conflict response
 * ("the request is valid, but the item's current state does not allow it").
 *
 * @author CJ
 */
public class ItemLockedException extends RuntimeException {

    public ItemLockedException(String message) {
        super(message);
    }
}
