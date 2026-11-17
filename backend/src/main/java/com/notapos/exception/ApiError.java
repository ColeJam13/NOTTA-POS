package com.notapos.exception;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * The JSON body sent to the frontend whenever a request fails.
 *
 * Every error response has the same shape, so the frontend can always read
 * "message" to show the user what went wrong.
 *
 * Example:
 * {
 *   "timestamp": "2026-11-17T18:30:00",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Order item not found with id: 42",
 *   "fieldErrors": null
 * }
 *
 * @param timestamp   When the error happened
 * @param status      HTTP status code (404, 409, 400, 500)
 * @param error       Short name of the status ("Not Found")
 * @param message     Human-readable explanation
 * @param fieldErrors For validation failures only: field name -> what is wrong with it
 *
 * @author CJ
 */
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors) {
}
