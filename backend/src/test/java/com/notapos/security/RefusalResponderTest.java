package com.notapos.security;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.notapos.enums.StaffRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RefusalResponder: the answers written when Spring Security's
 * filter chain refuses a request before it reaches a controller.
 *
 * No Spring is started here. The responder is handed a fake request and a fake
 * response (MockHttpServletRequest / MockHttpServletResponse, which record what
 * was written to them), and the test reads the response back.
 *
 * The same answers are checked through the whole backend in
 * SignInRulesIntegrationTest and RoleMatrixIntegrationTest.
 *
 * @author CJ
 */
class RefusalResponderTest {

    // The JSON converter, built the plain way. Date-and-time support is part of it now
    // (before Jackson 3 it had to be added with findAndRegisterModules()).
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    private RefusalResponder responder;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        responder = new RefusalResponder(objectMapper);
        request = new MockHttpServletRequest("GET", "/api/tables");
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();          // Do not leave a signed-in person behind for the next test
    }

    @Test
    void testCommence_ShouldWrite401InTheApiErrorShape() throws Exception {
        // WHAT: Nobody is signed in
        // WHY: The frontend reads "status" and "message" from every error, and shows the
        //      PIN pad on a 401. The body must look like the ones GlobalExceptionHandler writes.

        responder.commence(request, response, new InsufficientAuthenticationException("no sign-in"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals(401, body.get("status").asInt());
        assertEquals("Unauthorized", body.get("error").asString());
        assertEquals("Sign in to do that.", body.get("message").asString());
        assertTrue(body.get("fieldErrors").isNull());
        assertTrue(body.get("code").isNull());
        assertFalse(body.get("timestamp").isNull());
    }

    @Test
    void testCommence_ShouldNotAskTheBrowserForAPassword() throws Exception {
        // WHAT: Check the headers on the 401
        // WHY: A "WWW-Authenticate" header makes a browser pop up its own
        //      username-and-password box. Sign-in here is the PIN pad.

        responder.commence(request, response, new InsufficientAuthenticationException("no sign-in"));

        assertNull(response.getHeader("WWW-Authenticate"));
    }

    @Test
    void testHandle_WhenSomeoneIsSignedIn_ShouldWrite403NamingTheirRole() throws Exception {
        // WHAT: A signed-in host is refused
        // WHY: 403 means "we know who you are, and no". Naming the role tells the person why.

        StaffSession host = new StaffSession(4L, "Hannah", StaffRole.HOST, Instant.now(), false);
        SecurityContextHolder.getContext().setAuthentication(StaffTokenFilter.authenticationFor(host));

        responder.handle(request, response, new AccessDeniedException("no"));

        assertEquals(403, response.getStatus());
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals(403, body.get("status").asInt());
        assertEquals("Forbidden", body.get("error").asString());
        assertEquals("Your role (host) is not allowed to do that.", body.get("message").asString());
    }

    @Test
    void testHandle_WhenTheyStillHoldTheStarterPin_ShouldWrite403WithTheCodeTheScreenChecks() throws Exception {
        // WHAT: The first manager, signed in with 1234, is refused something
        // WHY: The reason is not their role (a manager may do everything). The code tells
        //      the frontend to show the "choose a new PIN" screen; screens react to the
        //      code, never to the wording.

        StaffSession firstManager = new StaffSession(1L, "Manager", StaffRole.MANAGER, Instant.now(), true);
        SecurityContextHolder.getContext().setAuthentication(StaffTokenFilter.authenticationFor(firstManager));

        responder.handle(request, response, new AccessDeniedException("no"));

        assertEquals(403, response.getStatus());
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals("PIN_CHANGE_REQUIRED", body.get("code").asString());
        assertEquals("Choose a new PIN before doing anything else.", body.get("message").asString());
    }

    @Test
    void testHandle_WhenNobodyIsSignedIn_ShouldWrite401() throws Exception {
        // WHAT: The "not allowed" path is reached with nobody signed in
        // WHY: Spring Security normally sends that case to commence(). If it ever arrives
        //      here instead, the answer must still be "sign in", not a 403 naming no role.

        responder.handle(request, response, new AccessDeniedException("no"));

        assertEquals(401, response.getStatus());
        assertEquals("Sign in to do that.", objectMapper.readTree(response.getContentAsString()).get("message").asString());
    }
}
