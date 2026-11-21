package com.notapos.exception;

import com.notapos.security.RefusalResponder;
import com.notapos.security.StaffSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    // The name of the database index that allows one open order per table (migration V3).
    // PostgreSQL names the broken rule in its error message, which is how it is recognised below.
    static final String ONE_OPEN_ORDER_PER_TABLE_INDEX = "orders_one_open_per_table";

    @ExceptionHandler(ResourceNotFoundException.class)                          // Record does not exist -> 404
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(NotSignedInException.class)                               // The PIN was not recognized -> 401
    public ResponseEntity<ApiError> handleNotSignedIn(NotSignedInException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), null);
    }

    // A "who may do this" rule (@PreAuthorize on a controller method) said no -> 403, naming the role.
    // The signed-in person is whoever StaffTokenFilter identified for this request.
    //
    // The 401 branch is a net. Since step A3b a request with no sign-in is refused in the
    // filter chain (see SecurityConfig and RefusalResponder) and never reaches a controller,
    // so in practice only signed-in people arrive here. If that ever changed, nobody would
    // still be answered 401 and not let through.
    //
    // This handler has to exist. Without it the refusal would fall through to the
    // catch-all at the bottom of this class and come out as a 500.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        Authentication whoIsAsking = SecurityContextHolder.getContext().getAuthentication();

        if (whoIsAsking != null && whoIsAsking.getPrincipal() instanceof StaffSession staff) {
            return buildResponse(HttpStatus.FORBIDDEN, RefusalResponder.roleNotAllowed(staff.role()), null);
        }
        return buildResponse(HttpStatus.UNAUTHORIZED, RefusalResponder.NOT_SIGNED_IN, null);
    }

    @ExceptionHandler(TooManyAttemptsException.class)                           // Too many wrong PINs in a row -> 429
    public ResponseEntity<ApiError> handleTooManyAttempts(TooManyAttemptsException ex) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), null);
    }

    @ExceptionHandler(StaffRuleException.class)                                 // Change breaks a rule about staff (PIN in use, last manager) -> 409
    public ResponseEntity<ApiError> handleStaffRule(StaffRuleException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    @ExceptionHandler(OrderNotOpenException.class)                              // The check was closed (usually on another terminal) -> 409 with a code the screen can check
    public ResponseEntity<ApiError> handleOrderNotOpen(OrderNotOpenException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), null, OrderNotOpenException.CODE);
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

    // Two terminals saved the same order, each starting from the same copy -> 409.
    // Hibernate throws this when the version number on the row is not the one the save started
    // from (see the version field on Order). Nothing from the losing request is saved.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleChangedElsewhere(OptimisticLockingFailureException ex) {
        return buildResponse(HttpStatus.CONFLICT,
                "This check was just changed on another terminal. Reopen it and try again.", null);
    }

    // The database refused a row because it breaks one of its own rules.
    // The one rule a person at a screen can run into is "one open order per table" (the index
    // orders_one_open_per_table from migration V3) -> 409 with words they can act on.
    // Anything else is a defect in the code, not something the user did -> 500, as before.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDatabaseRule(DataIntegrityViolationException ex) {
        String detail = ex.getMostSpecificCause().getMessage();
        if (detail != null && detail.contains(ONE_OPEN_ORDER_PER_TABLE_INDEX)) {
            return buildResponse(HttpStatus.CONFLICT,
                    "This table already has an open check. Reopen the table to see it.", null);
        }
        return handleUnexpected(ex);
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
                fieldErrors,
                null);

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)                                          // Anything unexpected -> 500 (details go to the log, not the user)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unexpected error while handling request", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on the server", null);
    }

    private ResponseEntity<ApiError> buildResponse(HttpStatus status, String message, Map<String, String> fieldErrors) {
        return buildResponse(status, message, fieldErrors, null);           // Most errors have no code
    }

    private ResponseEntity<ApiError> buildResponse(HttpStatus status, String message, Map<String, String> fieldErrors, String code) {
        ApiError body = new ApiError(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                fieldErrors,
                code);
        return ResponseEntity.status(status).body(body);
    }
}
