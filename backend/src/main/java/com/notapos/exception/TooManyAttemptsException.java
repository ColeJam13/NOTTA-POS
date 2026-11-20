package com.notapos.exception;

/**
 * Thrown when wrong PINs have been typed too many times in a row, and sign-in
 * is paused for a short while.
 *
 * GlobalExceptionHandler turns this into an HTTP 429 Too Many Requests response.
 *
 * @author CJ
 */
public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException(String message) {
        super(message);
    }
}
