package com.notapos.exception;

/**
 * Thrown when a request about a challenge is well formed but the challenge is
 * not in a state that allows it: deleting one that has a prize marked awarded,
 * ending one that has not started, marking an award for someone who has not
 * completed it.
 *
 * GlobalExceptionHandler turns this into an HTTP 409 Conflict response. The
 * message is written for the manager at the screen, because the frontend shows
 * it to them as-is.
 *
 * @author CJ
 */
public class ChallengeRuleException extends RuntimeException {

    public ChallengeRuleException(String message) {
        super(message);
    }
}
