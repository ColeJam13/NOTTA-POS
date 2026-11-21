package com.notapos.testsupport;

import com.notapos.enums.StaffRole;
import com.notapos.security.StaffTokenFilter;
import com.notapos.security.StaffSessionStore;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The one way a controller test says "this request comes from a signed-in manager"
 * (or server, or cook...).
 *
 *     @Autowired private SignIn signIn;
 *
 *     mockMvc.perform(get("/api/staff").with(signIn.asManager()))         // a manager
 *     mockMvc.perform(put("/api/menu-items/1/86").with(signIn.asKitchen()))
 *     mockMvc.perform(post("/api/orders").with(signIn.as(7L, "Sarah", StaffRole.SERVER)))   // a particular person
 *     mockMvc.perform(get("/api/staff"))                                  // nobody: no .with(...)
 *
 * WHAT: Each call starts a real session in the real StaffSessionStore and puts
 *       its token on the request, exactly as the frontend does after the PIN
 *       pad. Nothing about sign-in is faked, so the request goes through the
 *       same check as a real one.
 *
 * WHY:  Tests used to create a session and write the header name by hand in
 *       every file. With one helper, a change to how the token travels is a
 *       change here and nowhere else.
 *
 * ".with(...)" is MockMvc's hook for adjusting a request before it is sent; the
 * thing passed to it is called a RequestPostProcessor.
 *
 * Available in any test marked @ControllerTest.
 *
 * @author CJ
 */
public class SignIn {

    // The header the frontend sends the token in
    public static final String TOKEN_HEADER = StaffTokenFilter.TOKEN_HEADER;

    private final StaffSessionStore sessions;

    public SignIn(StaffSessionStore sessions) {
        this.sessions = sessions;
    }

    // ------------------------------------------------------------------
    // "Some person with this role" - for tests about what a role may do
    // ------------------------------------------------------------------

    public RequestPostProcessor asManager() {
        return as(StaffRole.MANAGER);
    }

    public RequestPostProcessor asServer() {
        return as(StaffRole.SERVER);
    }

    public RequestPostProcessor asBartender() {
        return as(StaffRole.BARTENDER);
    }

    public RequestPostProcessor asHost() {
        return as(StaffRole.HOST);
    }

    public RequestPostProcessor asKitchen() {
        return as(StaffRole.KITCHEN);
    }

    public RequestPostProcessor as(StaffRole role) {                // A made-up person with that role
        return as(900L + role.ordinal(), "Test " + role.getValue(), role);
    }

    // ------------------------------------------------------------------
    // A particular person - for tests that check WHO was signed in
    // ------------------------------------------------------------------

    public RequestPostProcessor as(Long staffId, String name, StaffRole role) {
        return withToken(tokenFor(staffId, name, role));
    }

    // ------------------------------------------------------------------
    // The token itself - for tests about signing in and out
    // ------------------------------------------------------------------

    public String tokenFor(Long staffId, String name, StaffRole role) {    // Start a session and hand back its token
        return sessions.createSession(staffId, name, role);
    }

    public void endSession(String token) {                          // What signing out does to a token
        sessions.endSession(token);
    }

    public static RequestPostProcessor withToken(String token) {    // Send this exact token (real, ended, or made up)
        return request -> {
            request.addHeader(TOKEN_HEADER, token);
            return request;
        };
    }
}
