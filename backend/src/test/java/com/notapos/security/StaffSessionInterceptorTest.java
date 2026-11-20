package com.notapos.security;

import com.notapos.enums.StaffRole;
import com.notapos.security.StaffSessionInterceptor.StaffSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the session-keeping half of StaffSessionInterceptor.
 *
 * (The half that refuses requests - 401 and 403 - is tested through real
 * requests in the controller tests, for example StaffControllerTest.)
 *
 * @author CJ
 */
class StaffSessionInterceptorTest {

    private StaffSessionInterceptor sessions;

    @BeforeEach
    void setUp() {
        sessions = new StaffSessionInterceptor();
    }

    @Test
    void testCreateSession_ShouldGiveATokenThatLeadsBackToThePerson() {
        // WHAT: Test starting a session and looking it up by its token
        // WHY: This is how every later request says who is asking

        // When
        String token = sessions.createSession(7L, "Sarah", StaffRole.SERVER);

        // Then
        Optional<StaffSession> session = sessions.sessionFor(token);
        assertTrue(session.isPresent());
        assertEquals(7L, session.get().staffId());
        assertEquals("Sarah", session.get().name());
        assertEquals(StaffRole.SERVER, session.get().role());
    }

    @Test
    void testCreateSession_ShouldGiveEachSessionADifferentLongToken() {
        // WHAT: Test that tokens are long and never repeat
        // WHY: A token that could be guessed would let anyone act as that person

        // When - the same person signs in twice
        String first = sessions.createSession(7L, "Sarah", StaffRole.SERVER);
        String second = sessions.createSession(7L, "Sarah", StaffRole.SERVER);

        // Then
        assertNotEquals(first, second);
        assertTrue(first.length() >= 40);
    }

    @Test
    void testSessionFor_WhenTokenIsUnknownOrMissing_ShouldReturnEmpty() {
        // WHAT: Test looking up a token nobody was given
        // WHY: A made-up or missing token is the same as not being signed in

        assertTrue(sessions.sessionFor("not-a-real-token").isEmpty());
        assertTrue(sessions.sessionFor(null).isEmpty());
    }

    @Test
    void testEndSession_ShouldStopTheTokenWorking() {
        // WHAT: Test signing out
        // WHY: After sign-out the old token must be worthless

        // Given
        String token = sessions.createSession(7L, "Sarah", StaffRole.SERVER);

        // When
        sessions.endSession(token);

        // Then
        assertTrue(sessions.sessionFor(token).isEmpty());
    }

    @Test
    void testEndSessionsFor_ShouldSignOnePersonOutEverywhereAndLeaveOthersAlone() {
        // WHAT: Test ending every session a person has
        // WHY: When a manager changes someone's PIN or role, or deactivates them, it must
        //      take effect on every screen they are signed in at - right away

        // Given - Sarah is signed in on two screens, Marcus on one
        String sarahBar = sessions.createSession(7L, "Sarah", StaffRole.SERVER);
        String sarahPatio = sessions.createSession(7L, "Sarah", StaffRole.SERVER);
        String marcus = sessions.createSession(8L, "Marcus", StaffRole.BARTENDER);

        // When
        sessions.endSessionsFor(7L);

        // Then
        assertTrue(sessions.sessionFor(sarahBar).isEmpty());
        assertTrue(sessions.sessionFor(sarahPatio).isEmpty());
        assertTrue(sessions.sessionFor(marcus).isPresent());
    }
}
