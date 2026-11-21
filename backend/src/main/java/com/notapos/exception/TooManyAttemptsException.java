package com.notapos.exception;

/**
 * Thrown when wrong PINs have been typed too many times, and sign-in is paused
 * for a while (see SignInGuard for the rules).
 *
 * GlobalExceptionHandler turns this into an HTTP 429 Too Many Requests response,
 * with the standard Retry-After header saying how many seconds to wait.
 *
 * @author CJ
 */
public class TooManyAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;       // How long until it is worth trying again

    public TooManyAttemptsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
