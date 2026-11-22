package com.notapos.exception;

/**
 * Thrown when a challenge a manager is making or changing does not add up: no
 * category or item for a challenge that counts items, an end before the start,
 * a menu item that does not exist.
 *
 * These are rules ACROSS fields, which the annotations on ChallengeRequest
 * cannot express (they look at one field at a time).
 *
 * GlobalExceptionHandler turns this into an HTTP 400 Bad Request response. The
 * message is written for the manager at the form, because the frontend shows it
 * to them as-is.
 *
 * @author CJ
 */
public class InvalidChallengeException extends RuntimeException {

    public InvalidChallengeException(String message) {
        super(message);
    }
}
