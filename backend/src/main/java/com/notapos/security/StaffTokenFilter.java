package com.notapos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Works out who is asking, for every request, and tells Spring Security.
 *
 * WHAT: A "filter" - a check a request passes through on its way to a
 *       controller. Spring Security is a line of such filters (the "filter
 *       chain"); this is the one this app adds to it (see SecurityConfig).
 *
 * This filter does AUTHENTICATION: "who are you?". It never refuses anything.
 * Deciding what that person may do is AUTHORIZATION, and happens later, at
 * the controller method (the @PreAuthorize rules).
 *
 * HOW:
 *   1. Read the token from the X-Staff-Token header.
 *   2. Ask StaffSessionStore whose session it is.
 *   3. If it is someone's, hand Spring Security an Authentication: an object
 *      saying who the person is (the StaffSession, called the "principal")
 *      and what they hold (their role, called an "authority").
 *   4. If there is no token, or it is not one the backend handed out, do
 *      nothing. Spring Security then treats the request as "anonymous":
 *      endpoints with no rule still answer, endpoints with a rule answer 401.
 *
 * The Authentication is kept only for this one request. Nothing is remembered
 * on the server between requests except the session list itself, so the token
 * must come with every request.
 *
 * WHY THE ROLE IS WRITTEN "ROLE_MANAGER": Spring Security's rule
 * hasRole('MANAGER') looks for an authority spelled ROLE_MANAGER. The prefix
 * is its convention for telling roles from other kinds of permission.
 *
 * OncePerRequestFilter is Spring's base class for a filter that must run one
 * time per request, even when a request is passed along inside the server.
 *
 * This class is deliberately NOT a Spring bean (no @Component). Spring Boot
 * registers every filter bean with the web server by itself, so as a bean it
 * would run twice: once there and once inside the security filter chain.
 * SecurityConfig creates it with "new" and puts it in the chain, once.
 *
 * @author CJ
 */
public class StaffTokenFilter extends OncePerRequestFilter {

    // The header the frontend sends the token in
    public static final String TOKEN_HEADER = "X-Staff-Token";

    // What Spring Security puts in front of a role name (see the class comment)
    private static final String ROLE_PREFIX = "ROLE_";

    // The list of who is signed in
    private final StaffSessionStore sessions;

    // Where Spring Security keeps "who is asking" while a request is being handled
    private final SecurityContextHolderStrategy securityContextHolder = SecurityContextHolder.getContextHolderStrategy();

    public StaffTokenFilter(StaffSessionStore sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        sessions.sessionFor(request.getHeader(TOKEN_HEADER)).ifPresent(session -> {
            SecurityContext context = securityContextHolder.createEmptyContext();
            context.setAuthentication(authenticationFor(session));
            securityContextHolder.setContext(context);
        });

        filterChain.doFilter(request, response);            // Always carry on to the next filter: this one only identifies
    }

    /**
     * Describe a signed-in person the way Spring Security understands.
     *
     * The three parts: the principal (the StaffSession - controllers read it with
     * @AuthenticationPrincipal), the credentials (none: the PIN was checked at sign-in
     * and is not kept), and the authorities (their one role).
     */
    static Authentication authenticationFor(StaffSession session) {
        return UsernamePasswordAuthenticationToken.authenticated(
                session,
                null,
                List.of(new SimpleGrantedAuthority(ROLE_PREFIX + session.role().name())));
    }
}
