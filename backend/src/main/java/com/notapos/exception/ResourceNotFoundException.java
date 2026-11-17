package com.notapos.exception;

/**
 * Thrown when a requested record does not exist (order, table, menu item, etc.).
 *
 * GlobalExceptionHandler turns this into an HTTP 404 Not Found response,
 * so services can simply throw it and controllers need no try/catch.
 *
 * @author CJ
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
