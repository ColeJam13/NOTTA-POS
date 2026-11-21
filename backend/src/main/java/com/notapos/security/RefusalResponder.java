package com.notapos.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notapos.enums.StaffRole;
import com.notapos.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Writes the answer when Spring Security's filter chain refuses a request.
 *
 * WHAT: Since step A3b every request must come from a signed-in person, and
 *       SecurityConfig checks that in the filter chain, BEFORE the request
 *       reaches any controller. A refusal there never gets as far as
 *       GlobalExceptionHandler, which only sees what controllers throw. This
 *       class writes the same JSON body (ApiError) that handler would have, so
 *       the frontend reads one shape of error wherever the refusal came from.
 *
 * Spring Security asks two different interfaces, depending on who was refused:
 *
 *   AuthenticationEntryPoint ("commence")  nobody is signed in      -> 401
 *   AccessDeniedHandler ("handle")         signed in, not allowed   -> 403
 *
 * The name "entry point" comes from web sites, where the answer to "who are
 * you?" is to send the browser to a login page. Here the answer is a 401, and
 * the frontend shows the PIN pad when it sees one.
 *
 * NO "WWW-Authenticate" HEADER is added to the 401, on purpose: that header
 * makes a browser pop up its own username-and-password box.
 *
 * The wording lives here once (NOT_SIGNED_IN, roleNotAllowed) and
 * GlobalExceptionHandler uses the same, for refusals by a rule on a controller
 * method (@PreAuthorize).
 *
 * Not a Spring bean: SecurityConfig creates it, the same as StaffTokenFilter.
 *
 * @author CJ
 */
public class RefusalResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    // What the frontend shows when a request arrives with no sign-in
    public static final String NOT_SIGNED_IN = "Sign in to do that.";

    // What the frontend shows when the signed-in person's role may not do it
    public static String roleNotAllowed(StaffRole role) {
        return "Your role (" + role.getValue() + ") is not allowed to do that.";
    }

    private final ObjectMapper objectMapper;        // Turns the ApiError into JSON, the same way controllers' answers are

    public RefusalResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override                                       // Nobody is signed in (no token, or one the backend does not know) -> 401
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, NOT_SIGNED_IN);
    }

    // Signed in, but a rule in the filter chain said no -> 403, naming the role.
    // Nothing reaches this today: the chain's only rule is "be signed in", and role rules
    // sit on controller methods, where GlobalExceptionHandler answers. It is here so that a
    // role rule added to SecurityConfig later answers in the same shape as everything else,
    // instead of with Spring Boot's own error page.
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        Authentication whoIsAsking = SecurityContextHolder.getContext().getAuthentication();

        if (whoIsAsking != null && whoIsAsking.getPrincipal() instanceof StaffSession staff) {
            write(response, HttpStatus.FORBIDDEN, roleNotAllowed(staff.role()));
        } else {
            write(response, HttpStatus.UNAUTHORIZED, NOT_SIGNED_IN);
        }
    }

    private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        ApiError body = new ApiError(LocalDateTime.now(), status.value(), status.getReasonPhrase(), message, null, null);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
