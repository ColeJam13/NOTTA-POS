package com.notapos.security;

import com.notapos.enums.StaffRole;
import com.notapos.testsupport.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What any list of who is signed in must do, whichever way it keeps the list.
 *
 * WHAT: A "contract test": the tests are written once, here, against the
 *       StaffSessionStore interface. Each kind of store has a small test class
 *       that implements this interface and says which store to test:
 *         - DatabaseStaffSessionStoreTest (the real one, against PostgreSQL)
 *         - InMemoryStaffSessionStoreTest (the stand-in used by controller tests)
 * WHY:  Controller tests run against the stand-in. If it behaved differently
 *       from the real store, those tests could pass while the app was wrong.
 *       Running the same tests against both keeps them alike.
 *
 * HOW:  JUnit runs the @Test methods of an interface for every class that
 *       implements it. A method with a body in an interface is called a
 *       "default" method.
 *
 * Time is a clock the tests move by hand (MutableClock), so "13 hours later"
 * takes no time at all.
 *
 * WHY @Transactional IS ON THIS INTERFACE: the database tests rely on each
 * test running in a transaction that is rolled back afterwards
 * (BaseRepositoryTest). Spring looks for @Transactional on the type a test
 * method is WRITTEN in, and these are written here, not in the class that
 * extends BaseRepositoryTest. Without it they would run outside that
 * transaction and leave their rows behind. It does nothing for the in-memory
 * test, which does not use Spring.
 *
 * @author CJ
 */
@Transactional
interface StaffSessionStoreContract {

    StaffSessionStore sessions();       // The store under test, built on clock()

    MutableClock clock();               // The clock that store reads the time from

    // ------------------------------------------------------------------
    // Starting, finding, and ending a session
    // ------------------------------------------------------------------

    @Test
    default void testCreateSession_ShouldGiveATokenThatLeadsBackToThePerson() {
        // WHAT: Test starting a session and looking it up by its token
        // WHY: This is how every later request says who is asking

        // When
        String token = sessions().createSession(7L, "Sarah", StaffRole.SERVER);

        // Then
        Optional<StaffSession> session = sessions().sessionFor(token);
        assertTrue(session.isPresent());
        assertEquals(7L, session.get().staffId());
        assertEquals("Sarah", session.get().name());
        assertEquals(StaffRole.SERVER, session.get().role());
        assertFalse(session.get().mustChangePin());
    }

    @Test
    default void testCreateSession_ShouldGiveEachSessionADifferentLongToken() {
        // WHAT: Test that tokens are long and never repeat
        // WHY: A token that could be guessed would let anyone act as that person

        // When - the same person signs in twice
        String first = sessions().createSession(7L, "Sarah", StaffRole.SERVER);
        String second = sessions().createSession(7L, "Sarah", StaffRole.SERVER);

        // Then
        assertNotEquals(first, second);
        assertTrue(first.length() >= 40);
    }

    @Test
    default void testSessionFor_WhenTokenIsUnknownOrMissing_ShouldReturnEmpty() {
        // WHAT: Test looking up a token nobody was given
        // WHY: A made-up or missing token is the same as not being signed in

        assertTrue(sessions().sessionFor("not-a-real-token").isEmpty());
        assertTrue(sessions().sessionFor(null).isEmpty());
    }

    @Test
    default void testSessionFor_GivenTheHashInsteadOfTheToken_ShouldReturnEmpty() {
        // WHAT: Send the stored hash of a token as if it were the token
        // WHY: The hash is what a copy of the database would give away. It must not
        //      work as a token, or hashing would protect nothing.

        // Given
        String token = sessions().createSession(7L, "Sarah", StaffRole.SERVER);

        // Then
        assertTrue(sessions().sessionFor(SessionTokens.hash(token)).isEmpty());
        assertTrue(sessions().sessionFor(token).isPresent());
    }

    @Test
    default void testEndSession_ShouldStopTheTokenWorking() {
        // WHAT: Test signing out
        // WHY: After sign-out the old token must be worthless

        // Given
        String token = sessions().createSession(7L, "Sarah", StaffRole.SERVER);

        // When
        sessions().endSession(token);

        // Then
        assertTrue(sessions().sessionFor(token).isEmpty());
    }

    @Test
    default void testEndSession_WithATokenNobodyHasOrNone_ShouldDoNothing() {
        // WHAT: Test signing out with a token that is not a session
        // WHY: Signing out twice, or with nothing, must not be an error or touch anyone else

        // Given
        String sarah = sessions().createSession(7L, "Sarah", StaffRole.SERVER);

        // When
        sessions().endSession("not-a-real-token");
        sessions().endSession(null);

        // Then
        assertTrue(sessions().sessionFor(sarah).isPresent());
    }

    @Test
    default void testEndSessionsFor_ShouldSignOnePersonOutEverywhereAndLeaveOthersAlone() {
        // WHAT: Test ending every session a person has
        // WHY: When a manager changes someone's PIN or role, or deactivates them, it must
        //      take effect on every screen they are signed in at - right away

        // Given - Sarah is signed in on two screens, Marcus on one
        String sarahBar = sessions().createSession(7L, "Sarah", StaffRole.SERVER);
        String sarahPatio = sessions().createSession(7L, "Sarah", StaffRole.SERVER);
        String marcus = sessions().createSession(8L, "Marcus", StaffRole.BARTENDER);

        // When
        sessions().endSessionsFor(7L);

        // Then
        assertTrue(sessions().sessionFor(sarahBar).isEmpty());
        assertTrue(sessions().sessionFor(sarahPatio).isEmpty());
        assertTrue(sessions().sessionFor(marcus).isPresent());
    }

    // ------------------------------------------------------------------
    // Choosing a new PIN, and a corrected name
    // ------------------------------------------------------------------

    @Test
    default void testCreateSession_ForSomeoneWhoMustChangeTheirPin_ShouldSaySoOnTheSession() {
        // WHAT: Test a session started with the starter PIN
        // WHY: SecurityConfig reads this flag to refuse everything but choosing a new PIN

        String token = sessions().createSession(1L, "Manager", StaffRole.MANAGER, true);

        assertTrue(sessions().sessionFor(token).orElseThrow().mustChangePin());
    }

    @Test
    default void testPinChangedBy_ShouldKeepThatDeviceSignedInAndEndThePersonsOtherSessions() {
        // WHAT: Test what happens to sessions when a person chooses a new PIN themselves
        // WHY: The device they did it on should carry on (they just proved who they are),
        //      now with nothing left to change. Anywhere else they were signed in with the
        //      old PIN is signed out. Nobody else is affected.

        // Given - the manager is signed in at the office and at the bar; Sarah at the patio
        String office = sessions().createSession(1L, "Manager", StaffRole.MANAGER, true);
        String bar = sessions().createSession(1L, "Manager", StaffRole.MANAGER, true);
        String sarah = sessions().createSession(7L, "Sarah", StaffRole.SERVER);

        // When - the PIN is changed from the office
        sessions().pinChangedBy(1L, office);

        // Then
        assertFalse(sessions().sessionFor(office).orElseThrow().mustChangePin());
        assertTrue(sessions().sessionFor(bar).isEmpty());
        assertTrue(sessions().sessionFor(sarah).isPresent());
    }

    @Test
    default void testRenameSessionsFor_ShouldChangeTheNameAndKeepThePersonSignedIn() {
        // WHAT: Test a manager correcting someone's name while they are signed in
        // WHY: A session can now last for days. Without this, the orders that person
        //      opens would keep carrying the misspelled name until they signed out.

        // Given
        String sarah = sessions().createSession(7L, "Sara", StaffRole.SERVER);
        String marcus = sessions().createSession(8L, "Marcus", StaffRole.BARTENDER);

        // When
        sessions().renameSessionsFor(7L, "Sarah");

        // Then
        assertEquals("Sarah", sessions().sessionFor(sarah).orElseThrow().name());
        assertEquals("Marcus", sessions().sessionFor(marcus).orElseThrow().name());
    }

    // ------------------------------------------------------------------
    // Time
    // ------------------------------------------------------------------

    @Test
    default void testSessionFor_WhenNotUsedForLongerThanTheIdleLimit_ShouldReturnEmptyFromThenOn() {
        // WHAT: Test a session nobody has used for more than 12 hours
        // WHY: A tablet left signed in overnight must not still be signed in the next day

        // Given
        String token = sessions().createSession(7L, "Sarah", StaffRole.SERVER);

        // When
        clock().advance(StaffSessionStore.IDLE_LIMIT.plusMinutes(1));

        // Then - refused, and gone for good (not revived by having been asked for)
        assertTrue(sessions().sessionFor(token).isEmpty());
        assertTrue(sessions().sessionFor(token).isEmpty());
    }

    @Test
    default void testSessionFor_WhenUsedThroughALongShift_ShouldNeverTimeOut() {
        // WHAT: Test a session that is used every hour for 30 hours
        // WHY: The idle limit counts from the LAST use, not from sign-in. Being used is
        //      what keeps a session alive. (Owner's decision D3: no sign-out mid-shift.)

        // Given
        String token = sessions().createSession(7L, "Sarah", StaffRole.SERVER);

        // When & Then
        for (int hour = 0; hour < 30; hour++) {
            clock().advance(Duration.ofHours(1));
            assertTrue(sessions().sessionFor(token).isPresent(), "Signed out after " + (hour + 1) + " hours of use");
        }
    }

    @Test
    default void testSessionFor_ShouldNoteTheLastUseOnlyEveryFewMinutes() {
        // WHAT: Test how often "last used" is written down
        // WHY: The screens ask the backend something every few seconds. Writing to the
        //      database each time would be thousands of writes an hour for a time that
        //      only matters to the nearest hour.

        // Given
        String token = sessions().createSession(7L, "Sarah", StaffRole.SERVER);
        var signedInAt = sessions().sessionFor(token).orElseThrow().lastUsed();

        // When - used a minute later: too soon to be worth noting
        clock().advance(Duration.ofMinutes(1));
        assertEquals(signedInAt, sessions().sessionFor(token).orElseThrow().lastUsed());

        // When - used again, more than TOUCH_INTERVAL after the note
        clock().advance(StaffSessionStore.TOUCH_INTERVAL);
        var noted = sessions().sessionFor(token).orElseThrow().lastUsed();

        // Then - noted now, and the note is what the next lookup reads back
        assertEquals(clock().instant(), noted);
        assertEquals(noted, sessions().sessionFor(token).orElseThrow().lastUsed());
    }

    @Test
    default void testEndIdleSessions_ShouldRemoveOnlySessionsPastTheIdleLimit() {
        // WHAT: Test the sweep that the scheduled task runs
        // WHY: A session whose token is never sent again is never looked up, so nothing
        //      else would ever remove it. Sessions still in use must be left alone.

        // Given - Marcus signed in and went home; Sarah signed in 11 hours later
        String marcus = sessions().createSession(8L, "Marcus", StaffRole.BARTENDER);
        clock().advance(Duration.ofHours(11));
        String sarah = sessions().createSession(7L, "Sarah", StaffRole.SERVER);
        clock().advance(Duration.ofHours(2));                        // Marcus: 13 hours unused. Sarah: 2.

        // When
        int removed = sessions().endIdleSessions();

        // Then
        assertEquals(1, removed);
        assertTrue(sessions().sessionFor(marcus).isEmpty());
        assertTrue(sessions().sessionFor(sarah).isPresent());
    }
}
