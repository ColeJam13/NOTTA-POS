package com.notapos.config;

import tools.jackson.databind.json.JsonMapper;
import com.notapos.security.RefusalResponder;
import com.notapos.security.StaffSession;
import com.notapos.security.StaffSessionStore;
import com.notapos.security.StaffTokenFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.logout.LogoutFilter;

/**
 * Sign-in and permissions, set up on Spring Security.
 *
 * Spring Security is the standard library for this in Spring. It replaced a
 * home-built check (StaffSessionInterceptor and @RequiresRole) in step A3a of
 * the Phase 2 plan. Step A3b then closed the API: before it, only seven
 * endpoints checked who was asking.
 *
 * THE THREE IDEAS:
 *
 *   The filter chain. Every request passes through a fixed line of checks
 *   ("filters") before it reaches a controller. securityFilterChain() below
 *   says which checks are in the line and how each is set.
 *
 *   Authentication: who are you? Answered by StaffTokenFilter, from the token
 *   in the X-Staff-Token header. A request with no good token is "anonymous".
 *
 *   Authorization: what may you do? Answered in two places.
 *
 *     1. In the filter chain, for every request: you must be signed in. The
 *        one exception is signing in itself. This is "default deny": a new
 *        endpoint is closed to outsiders without anyone remembering to close it.
 *
 *        (Since step P2 the frontend's own files can also be read without a
 *        sign-in, so that the sign-in screen can be shown at all. They are an
 *        exact list, FRONTEND_FILES below, not a loosening of the rule.)
 *
 *        One more rule lives here, since step A4: a person who signed in with
 *        the starter PIN may only choose a new one (and ask who they are, and
 *        sign out). It is in the chain, not on each controller method, for the
 *        same reason as default deny: nobody has to remember it for a new endpoint.
 *
 *     2. At the controller method, for anything only some roles may do, by a
 *        rule written next to it:
 *
 *            @PreAuthorize(Permissions.MANAGER_ONLY)
 *            @GetMapping
 *            public ResponseEntity<FinancialsReportView> getReport(...)
 *
 *        @EnableMethodSecurity on this class is what switches those rules on.
 *        Permissions holds the rules: it is the role matrix. A method with no
 *        rule is open to anyone who is signed in.
 *
 * WHAT A REFUSAL LOOKS LIKE: the same JSON body (ApiError) either way.
 *   Nobody signed in: 401 "Sign in to do that." The frontend shows the PIN pad.
 *       Written by RefusalResponder, because the chain refuses the request
 *       before any controller is reached.
 *   Signed in, role not allowed: 403 "Your role (...) is not allowed to do
 *       that." The rule on the method throws AccessDeniedException and
 *       GlobalExceptionHandler writes the answer.
 *   Signed in, still holding the starter PIN: 403 with the code
 *       PIN_CHANGE_REQUIRED. Written by RefusalResponder.
 *
 * The parts of Spring Security this app does not use are listed, and switched
 * off, in securityFilterChain(). PINs are still checked by StaffService.
 *
 * @author CJ
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * The rule for every request not named in securityFilterChain(): the person
     * is signed in, and is not still holding the starter PIN.
     *
     * An AuthorizationManager is Spring Security's name for "something that looks
     * at who is asking and answers yes or no". The ready-made ones (authenticated(),
     * hasRole(...)) know nothing about PINs, so this app supplies its own.
     *
     * "authentication.get()" is who StaffTokenFilter identified. For a request
     * with no good token Spring Security supplies a stand-in "anonymous" person,
     * whose principal is not a StaffSession, so the answer is no.
     *
     * What the refused person is told depends on who they are: nobody signed in
     * gets 401, the starter-PIN holder gets 403 PIN_CHANGE_REQUIRED (RefusalResponder).
     */
    static final AuthorizationManager<RequestAuthorizationContext> SIGNED_IN_WITH_A_PIN_OF_THEIR_OWN =
            (authentication, request) -> new AuthorizationDecision(
                    authentication.get() != null
                            && authentication.get().isAuthenticated()
                            && authentication.get().getPrincipal() instanceof StaffSession staff
                            && !staff.mustChangePin());

    /**
     * The frontend's own files, which anyone may read (GET) without signing in.
     *
     * WHY ANYTHING IS PUBLIC: in the packaged build the backend also serves the
     * frontend (see WebConfig). The sign-in screen is one of those files. It cannot
     * need a sign-in.
     *
     * WHY A LIST, and not "everything outside /api": a list keeps default deny
     * intact. Whatever is added to this backend later, at whatever address, starts
     * closed, exactly as before. Only what is named here is open, and only for
     * reading. These files hold no data: they are the same code and pictures for
     * everyone, and every number on every screen still comes from /api, which
     * needs a sign-in.
     *
     *   /              what a phone asks for when the address is typed in
     *   /index.html    the page itself (Spring Boot passes "/" on to it)
     *   /assets/**     the built JavaScript, CSS, fonts, and pictures (Vite puts
     *                  everything it builds in this one folder)
     *   the rest       the few files in frontend/public, which are copied as they are
     *
     * A file added to frontend/public must be added here, or it is answered 401.
     * In development none of these files are in the backend at all (the frontend
     * runs on its own port), and these addresses simply answer 404.
     */
    static final String[] FRONTEND_FILES = {
            "/", "/index.html", "/assets/**",
            "/favicon.ico", "/manifest.json", "/logo192.png", "/logo512.png", "/robots.txt"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, StaffSessionStore sessions,
                                                   JsonMapper jsonMapper) throws Exception {
        RefusalResponder refusalResponder = new RefusalResponder(jsonMapper);

        http
                // Request-level rules, checked in the order written; the first that matches decides.
                .authorizeHttpRequests(requests -> requests
                        // An "error dispatch" is the web server passing a request that already
                        // failed on to Spring Boot's error page, inside the server. It is not a
                        // new request from outside, and StaffTokenFilter does not run again for
                        // it, so without this line every such failure would turn into a 401 and
                        // hide the real error. (Asking for /error directly from outside is an
                        // ordinary request and needs a sign-in like any other.)
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                        // Signing in is the one thing a person who is not signed in may do
                        .requestMatchers(HttpMethod.POST, "/api/staff/sign-in").permitAll()

                        // ...and, in the packaged build, reading the frontend's own files: the page
                        // that shows the PIN pad has to reach someone who is not signed in yet.
                        // See FRONTEND_FILES for exactly which addresses, and why it is a list.
                        .requestMatchers(HttpMethod.GET, FRONTEND_FILES).permitAll()

                        // What a signed-in person may do even while they still hold the starter
                        // PIN: choose a new one, ask who they are (a reloaded page needs that to
                        // know which screen to show), and sign out.
                        .requestMatchers(HttpMethod.PUT, "/api/staff/me/pin").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/staff/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/staff/sign-out").authenticated()

                        // Everything else, whatever its address: only for a signed-in person who
                        // has a PIN of their own. What each ROLE may do is decided at the
                        // controller method (@PreAuthorize).
                        .anyRequest().access(SIGNED_IN_WITH_A_PIN_OF_THEIR_OWN))

                // What to answer when the rules above refuse a request: this app's own JSON
                // error, not Spring Security's default (an empty 403).
                .exceptionHandling(refusals -> refusals
                        .authenticationEntryPoint(refusalResponder)
                        .accessDeniedHandler(refusalResponder))

                // Who is asking: read the token header on every request. The filter is placed where
                // Spring Security's own sign-in filters go (straight after LogoutFilter's position).
                .addFilterAfter(new StaffTokenFilter(sessions), LogoutFilter.class)

                // Stateless: Spring Security keeps nothing between requests and creates no
                // server-side HTTP session, so no session cookie is ever sent. The token in the
                // header says who is asking, every time.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CSRF protection off. CSRF is an attack where another site makes a signed-in
                // person's browser send a request the person did not intend. It works because
                // browsers attach COOKIES to requests by themselves. This backend never uses a
                // cookie: the token is in a header that only this app's own code adds, and a
                // hostile page cannot make the browser add it. With nothing sent automatically,
                // there is nothing for the attack to ride on. (If sign-in ever moves to a
                // cookie, this line must go.)
                .csrf(csrf -> csrf.disable())

                // CORS (which web addresses may call this backend) is answered at the front of
                // the chain, before sign-in is looked at: the browser's "may I?" preflight
                // request never carries the token, so it must be answered before the
                // "be signed in" rule above is applied. withDefaults() uses the rules WebConfig
                // already gives Spring MVC, which read notta.cors.allowed-origins.
                .cors(Customizer.withDefaults())

                // No ready-made /logout address. Signing out is POST /api/staff/sign-out.
                .logout(logout -> logout.disable());

        // Not called on purpose, so they stay off: formLogin() (a ready-made login page) and
        // httpBasic() (the browser's own username-and-password box). Sign-in is the PIN pad.

        return http.build();
    }

    /**
     * Tells Spring Boot this app has no username-and-password users.
     *
     * WHY THIS EXISTS: with Spring Security on the classpath and nothing said about
     * users, Spring Boot invents a user named "user" with a random password and prints
     * the password in the log at start-up. Declaring an AuthenticationManager (the part
     * that would check a username and password) is one of the documented ways to stop
     * that. This one refuses everything, because nothing in this app signs in that way:
     * PINs are checked by StaffService, and requests are identified by StaffTokenFilter.
     * Nothing calls it.
     */
    @Bean
    public AuthenticationManager authenticationManager() {
        return authentication -> {
            throw new AuthenticationServiceException("Username and password sign-in is not used. Sign in with a PIN.");
        };
    }
}
