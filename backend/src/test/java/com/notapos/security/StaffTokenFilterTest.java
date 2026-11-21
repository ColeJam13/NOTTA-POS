package com.notapos.security;

import com.notapos.enums.StaffRole;
import com.notapos.testsupport.InMemoryStaffSessionStore;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StaffTokenFilter: turning the X-Staff-Token header into
 * "who is asking" for Spring Security.
 *
 * WHAT: The filter is run by hand with a fake request, and a fake "rest of the
 *       chain" that notes who Spring Security believed was asking at the moment
 *       the request would have reached a controller.
 * WHY:  Every permission rule depends on this one step being right. A person
 *       given the wrong role here would pass, or fail, every rule wrongly.
 *
 * (That the answers come out as 401 and 403 is tested through real requests,
 * in the controller tests and SignInRulesIntegrationTest.)
 *
 * @author CJ
 */
class StaffTokenFilterTest {

    private StaffSessionStore sessions;
    private StaffTokenFilter filter;

    // What the "rest of the chain" saw
    private final AtomicReference<Authentication> seenByTheController = new AtomicReference<>();
    private final AtomicInteger timesCarriedOn = new AtomicInteger();
    private final FilterChain restOfTheChain = (request, response) -> {
        seenByTheController.set(SecurityContextHolder.getContext().getAuthentication());
        timesCarriedOn.incrementAndGet();
    };

    @BeforeEach
    void setUp() {
        sessions = new InMemoryStaffSessionStore();          // The filter only asks the store; which kind makes no difference to it
        filter = new StaffTokenFilter(sessions);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        // In the running app Spring Security clears this after every request. Here the filter
        // is run on its own, so the test tidies up, or one test's person would leak into the next.
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/staff");
        if (token != null) {
            request.addHeader(StaffTokenFilter.TOKEN_HEADER, token);
        }
        return request;
    }

    @Test
    void testAGoodToken_ShouldTellSpringSecurityWhoIsAskingAndTheirRole() throws Exception {
        // WHAT: Test a request that carries a token the backend handed out
        // WHY: The person (the "principal") is what controllers read with @AuthenticationPrincipal,
        //      and the role (the "authority") is what the @PreAuthorize rules check

        // Given
        String token = sessions.createSession(7L, "Sarah", StaffRole.KITCHEN);

        // When
        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), restOfTheChain);

        // Then
        Authentication who = seenByTheController.get();
        assertNotNull(who);
        assertTrue(who.isAuthenticated());
        StaffSession person = assertInstanceOf(StaffSession.class, who.getPrincipal());
        assertEquals(7L, person.staffId());
        assertEquals("Sarah", person.name());
        assertEquals(StaffRole.KITCHEN, person.role());
        // Exactly one authority, spelled the way hasRole('KITCHEN') looks for it
        assertEquals(List.of("ROLE_KITCHEN"), who.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
        assertNull(who.getCredentials());                           // The PIN is not carried around
    }

    @Test
    void testEveryRole_ShouldBecomeItsOwnAuthority() throws Exception {
        // WHAT: Test the authority given for each of the five roles
        // WHY: The rules are written as text (hasRole('MANAGER')). If a role's name and its
        //      authority ever stopped matching, that role would silently lose its permissions.

        for (StaffRole role : StaffRole.values()) {
            String token = sessions.createSession(1L, "Someone", role);

            filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), restOfTheChain);

            assertEquals("ROLE_" + role.name(),
                    seenByTheController.get().getAuthorities().iterator().next().getAuthority());
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void testNoTokenOrOneNobodyWasGiven_ShouldLeaveTheRequestAsNobodyAndCarryOn() throws Exception {
        // WHAT: Test a request with no token, and with a made-up one
        // WHY: This filter only identifies; it never refuses. A request it cannot identify
        //      goes on as "nobody", and the rule on the endpoint (if any) decides.

        filter.doFilter(requestWithToken(null), new MockHttpServletResponse(), restOfTheChain);
        assertNull(seenByTheController.get());

        filter.doFilter(requestWithToken("not-a-real-token"), new MockHttpServletResponse(), restOfTheChain);
        assertNull(seenByTheController.get());

        assertEquals(2, timesCarriedOn.get());                      // Both requests were passed along
    }

    @Test
    void testATokenWhoseSessionHasEnded_ShouldCountAsNobody() throws Exception {
        // WHAT: Test a token after its person signed out
        // WHY: Signing out must take effect on the very next request

        // Given
        String token = sessions.createSession(7L, "Sarah", StaffRole.MANAGER);
        sessions.endSession(token);

        // When
        filter.doFilter(requestWithToken(token), new MockHttpServletResponse(), restOfTheChain);

        // Then
        assertNull(seenByTheController.get());
        assertEquals(1, timesCarriedOn.get());
    }
}
