package com.notapos.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One place that turns exceptions into HTTP error responses for every controller.
 *
 * WHAT: @RestControllerAdvice tells Spring "when any controller method throws,
 *       look here for a matching @ExceptionHandler".
 * WHY:  Controllers used to wrap every call in try/catch (RuntimeException) and
 *       guess the status code. Now services throw a specific exception and the
 *       mapping to a status code lives here, once.
 *
 * Extending ResponseEntityExceptionHandler keeps Spring's built-in handling for
 * its own errors (malformed JSON -> 400, wrong HTTP method -> 405, etc.).
 *
 * @author CJ
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)                          // Record does not exist -> 404
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(NotSignedInException.class)                               // Nobody is signed in -> 401
    public ResponseEntity<ApiError> handleNotSignedIn(NotSignedInException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), null);
    }

    @ExceptionHandler(NotAllowedException.class)                                // Signed in, but this role may not do it -> 403
    public ResponseEntity<ApiError> handleNotAllowed(NotAllowedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), null);
    }

    @ExceptionHandler(TooManyAttemptsException.class)                           // Too many wrong PINs in a row -> 429
    public ResponseEntity<ApiError> handleTooManyAttempts(TooManyAttemptsException ex) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), null);
    }

    @ExceptionHandler(StaffRuleException.class)                                 // Change breaks a rule about staff (PIN in use, last manager) -> 409
    public ResponseEntity<ApiError> handleStaffRule(StaffRuleException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    @ExceptionHandler(ItemLockedException.class)                                // Item already sent to kitchen -> 409
    public ResponseEntity<ApiError> handleItemLocked(ItemLockedException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    @ExceptionHandler(OrderStateException.class)                                // Order is not in a state that allows the request -> 409
    public ResponseEntity<ApiError> handleOrderState(OrderStateException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    @ExceptionHandler(ItemUnavailableException.class)                           // Menu item is 86'd -> 409
    public ResponseEntity<ApiError> handleItemUnavailable(ItemUnavailableException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidModifierSelectionException.class)                  // Chosen modifiers break the menu's rules -> 400
    public ResponseEntity<ApiError> handleInvalidModifierSelection(InvalidModifierSelectionException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    @Override                                                                   // @Valid failed on a request body -> 400 with per-field messages
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));

        ApiError body = new ApiError(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed",
                fieldErrors);

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)                                          // Anything unexpected -> 500 (details go to the log, not the user)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unexpected error while handling request", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on the server", null);
    }

    private ResponseEntity<ApiError> buildResponse(HttpStatus status, String message, Map<String, String> fieldErrors) {
        ApiError body = new ApiError(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
