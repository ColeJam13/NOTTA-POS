package com.notapos.exception;

/**
 * Thrown when someone is signed in but their role does not allow what they
 * asked for (for example, a server asking for the Financials report).
 *
 * GlobalExceptionHandler turns this into an HTTP 403 Forbidden response.
 * 401 means "we do not know who you are"; 403 means "we know, and no".
 *
 * @author CJ
 */
public class NotAllowedException extends RuntimeException {

    public NotAllowedException(String message) {
        super(message);
    }
}
