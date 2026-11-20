package com.notapos.exception;

/**
 * Thrown when something is rung in, sent, or changed on an order that is no
 * longer open - almost always because the check was paid on another terminal
 * while this screen still had it up.
 *
 * It is a kind of OrderStateException (so it becomes an HTTP 409 Conflict),
 * with one difference: its response carries the code "ORDER_NOT_OPEN". The
 * order screen looks for that code so it can tell this case apart from other
 * 409s, say what happened, and reload the table.
 *
 * @author CJ
 */
public class OrderNotOpenException extends OrderStateException {

    // The text the frontend checks for (ApiError.code)
    public static final String CODE = "ORDER_NOT_OPEN";

    public OrderNotOpenException(String message) {
        super(message);
    }
}
