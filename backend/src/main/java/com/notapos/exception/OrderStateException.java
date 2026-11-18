package com.notapos.exception;

/**
 * Thrown when a request is valid on its own, but the order is not in a state
 * that allows it. Examples:
 *   - paying for an order that is already closed
 *   - closing a check that still has items the server never sent
 *   - clearing a table whose order still has items on it
 *
 * GlobalExceptionHandler turns this into an HTTP 409 Conflict response. The
 * message is written for the person at the screen, because the frontend
 * shows it to them as-is.
 *
 * @author CJ
 */
public class OrderStateException extends RuntimeException {

    public OrderStateException(String message) {
        super(message);
    }
}
