package com.notapos.exception;

/**
 * Thrown when a request needs someone to be signed in and nobody is
 * (no token was sent, or the session behind it has ended).
 *
 * GlobalExceptionHandler turns this into an HTTP 401 Unauthorized response.
 * The frontend answers a 401 by showing the PIN screen.
 *
 * @author CJ
 */
public class NotSignedInException extends RuntimeException {

    public NotSignedInException(String message) {
        super(message);
    }
}
