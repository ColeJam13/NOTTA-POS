package com.notapos.security;

import com.notapos.exception.NotAllowedException;
import com.notapos.exception.NotSignedInException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Optional;

/**
 * Knows who is signed in, and stops requests that the signed-in person's
 * role does not allow.
 *
 * WHAT: An "interceptor" - Spring runs preHandle() before every controller
 *       method, which makes it the one place to check permissions.
 *
 * HOW SIGN-IN WORKS:
 *   1. Someone types their PIN. StaffService checks it and starts a session
 *      in StaffSessionStore, which hands back a long random "token".
 *   2. The frontend sends that token with every request, in a header
 *      named X-Staff-Token.
 *   3. preHandle() looks the token up in the store. If it belongs to a
 *      session, the request is marked with who is asking (the "currentStaff"
 *      attribute).
 *   4. If the controller method is marked @RequiresRole, the request is
 *      refused unless that person has one of the listed roles:
 *      no valid token -> 401, wrong role -> 403.
 *
 * The sessions themselves are kept by StaffSessionStore.
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

    // The list of who is signed in
    private final StaffSessionStore sessions;

    public StaffSessionInterceptor(StaffSessionStore sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Optional<StaffSession> session = sessions.sessionFor(request.getHeader(TOKEN_HEADER));
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
}
