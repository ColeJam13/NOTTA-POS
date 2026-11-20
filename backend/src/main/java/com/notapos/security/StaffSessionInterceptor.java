package com.notapos.security;

import com.notapos.enums.StaffRole;
import com.notapos.exception.NotAllowedException;
import com.notapos.exception.NotSignedInException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Knows who is signed in, and stops requests that the signed-in person's
 * role does not allow.
 *
 * WHAT: An "interceptor" - Spring runs preHandle() before every controller
 *       method, which makes it the one place to check permissions.
 *
 * HOW SIGN-IN WORKS:
 *   1. Someone types their PIN. StaffService checks it and calls
 *      createSession(), which hands back a long random "token".
 *   2. The frontend sends that token with every request, in a header
 *      named X-Staff-Token.
 *   3. preHandle() looks the token up. If it belongs to a session, the
 *      request is marked with who is asking (the "currentStaff" attribute).
 *   4. If the controller method is marked @RequiresRole, the request is
 *      refused unless that person has one of the listed roles:
 *      no valid token -> 401, wrong role -> 403.
 *
 * Sessions are kept in memory, in this object. Restarting the backend signs
 * everyone out, and a session that has not been used for 12 hours is forgotten.
 * (A restaurant with several backend servers would keep sessions in the
 * database instead; one server is all this app runs on today.)
 *
 * WHY THE SESSIONS LIVE HERE AND NOT IN A SERVICE: controller tests
 * (@WebMvcTest) load interceptors but not services. Keeping the sessions in
 * the interceptor means every controller test runs with the real permission
 * check, with nothing to mock.
 *
 * @author CJ
 */
@Component
public class StaffSessionInterceptor implements HandlerInterceptor {

    // The header the frontend sends the token in
    public static final String TOKEN_HEADER = "X-Staff-Token";

    // The name the signed-in person is stored under on the request
    // (controllers read it with @RequestAttribute)
    public static final String CURRENT_STAFF = "currentStaff";

    // A session that has not been used for this long is forgotten
    private static final Duration IDLE_LIMIT = Duration.ofHours(12);

    // token -> who it belongs to. ConcurrentHashMap is safe to use from many requests at once.
    private final Map<String, StaffSession> sessions = new ConcurrentHashMap<>();

    // SecureRandom produces numbers that cannot be predicted, which is what a token needs
    private final SecureRandom random = new SecureRandom();

    /**
     * Start a session for someone who just signed in.
     *
     * @return The token the frontend must send with each request
     */
    public String createSession(Long staffId, String name, StaffRole role) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        sessions.put(token, new StaffSession(staffId, name, role, Instant.now()));
        return token;
    }

    public void endSession(String token) {                          // Sign out
        if (token != null) {
            sessions.remove(token);
        }
    }

    public void endSessionsFor(Long staffId) {                      // Sign a person out everywhere (their PIN or role changed, or they were deactivated)
        sessions.values().removeIf(session -> session.staffId().equals(staffId));
    }

    public Optional<StaffSession> sessionFor(String token) {        // Who a token belongs to, if it is still good
        if (token == null) {
            return Optional.empty();
        }

        StaffSession session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }

        if (Duration.between(session.lastUsed(), Instant.now()).compareTo(IDLE_LIMIT) > 0) {
            sessions.remove(token);                                 // Not used for too long
            return Optional.empty();
        }

        // Remember that it was just used, so an active session never times out mid-shift
        StaffSession refreshed = new StaffSession(session.staffId(), session.name(), session.role(), Instant.now());
        sessions.put(token, refreshed);
        return Optional.of(refreshed);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Optional<StaffSession> session = sessionFor(request.getHeader(TOKEN_HEADER));
        session.ifPresent(current -> request.setAttribute(CURRENT_STAFF, current));

        // Only controller methods can carry @RequiresRole (requests for other things pass through)
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        RequiresRole rule = method.getMethodAnnotation(RequiresRole.class);
        if (rule == null) {
            return true;                                            // No rule on this endpoint
        }

        if (session.isEmpty()) {
            throw new NotSignedInException("Sign in to do that.");
        }

        boolean allowed = Arrays.asList(rule.value()).contains(session.get().role());
        if (!allowed) {
            throw new NotAllowedException("Your role (" + session.get().role().getValue() + ") is not allowed to do that.");
        }

        return true;
    }

    /**
     * One signed-in person.
     *
     * @param staffId  Who it is
     * @param name     Their name, as shown on orders
     * @param role     What they are allowed to do
     * @param lastUsed When the session was last used
     */
    public record StaffSession(Long staffId, String name, StaffRole role, Instant lastUsed) {
    }
}
