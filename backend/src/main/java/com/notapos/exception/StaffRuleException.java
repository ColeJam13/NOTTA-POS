package com.notapos.exception;

/**
 * Thrown when a change to a staff member is valid on its own but breaks a
 * rule about staff as a whole. Examples:
 *   - giving someone a PIN another person already uses
 *   - deactivating or demoting the last manager (nobody could manage staff any more)
 *
 * GlobalExceptionHandler turns this into an HTTP 409 Conflict response. The
 * message is written for the manager at the screen, who sees it as-is.
 *
 * @author CJ
 */
public class StaffRuleException extends RuntimeException {

    public StaffRuleException(String message) {
        super(message);
    }
}
