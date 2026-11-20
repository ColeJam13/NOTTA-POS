package com.notapos.exception;

/**
 * Thrown when someone tries to ring in a menu item that is 86'd
 * (the kitchen or bar has run out of it for now).
 *
 * GlobalExceptionHandler turns this into an HTTP 409 Conflict response. The
 * message names the item, because the order screen shows it to the server.
 *
 * @author CJ
 */
public class ItemUnavailableException extends RuntimeException {

    public ItemUnavailableException(String message) {
        super(message);
    }
}
