package com.notapos.security;

import com.notapos.entity.StoredSession;
import com.notapos.enums.StaffRole;
import com.notapos.repository.BaseRepositoryTest;
import com.notapos.repository.StoredSessionRepository;
import com.notapos.testsupport.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DatabaseStaffSessionStore, the real list of who is signed in,
 * against a real PostgreSQL database.
 *
 * Most of the tests are not in this file. They are in StaffSessionStoreContract,
 * which this class implements, and are run against this store and against the
 * in-memory stand-in alike. The tests written here are the ones only a database
 * can be asked: what exactly is in the row, and does the list outlive the
 * object that keeps it.
 *
 * Like the other repository tests (BaseRepositoryTest), each test runs in a
 * transaction that is rolled back afterwards, so the table starts empty.
 *
 * @author CJ
 */
class DatabaseStaffSessionStoreTest extends BaseRepositoryTest implements StaffSessionStoreContract {

    @Autowired
    private StoredSessionRepository storedSessions;

    private MutableClock clock;
    private DatabaseStaffSessionStore sessions;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        sessions = new DatabaseStaffSessionStore(storedSessions, clock);
    }

    @Override
    public StaffSessionStore sessions() {
        return sessions;
    }

    @Override
    public MutableClock clock() {
        return clock;
    }

    @Test
    void testCreateSession_ShouldStoreTheHashOfTheTokenAndNeverTheToken() {
        // WHAT: Read the row a sign-in leaves behind
        // WHY: Anyone with a copy of the database must not be able to act as a signed-in
        //      person. A hash can be checked against a token, not turned back into one.

        // When
        String token = sessions.createSession(7L, "Sarah", StaffRole.SERVER);

        // Then
        List<StoredSession> rows = storedSessions.findAll();
        assertEquals(1, rows.size());
        StoredSession row = rows.get(0);
        assertEquals(SessionTokens.hash(token), row.getTokenHash());
        assertNotEquals(token, row.getTokenHash());
        assertEquals(64, row.getTokenHash().length());               // SHA-256 as hex
        assertEquals(7L, row.getStaffId());
        assertEquals("Sarah", row.getName());
        assertEquals("server", row.getRole());
        assertEquals(clock.instant(), row.getCreatedAt());
        assertEquals(clock.instant(), row.getLastUsedAt());
    }

    @Test
    void testASession_ShouldOutliveTheStoreObjectThatStartedIt() {
        // WHAT: Start a session, then look it up with a brand-new store object
        // WHY: This is what a backend restart is: every object is thrown away and built
        //      again, and only the database is left. The old store kept its list in a
        //      field, so a restart signed the whole floor out.

        // Given
        String token = sessions.createSession(7L, "Sarah", StaffRole.SERVER);

        // When - "the backend restarts"
        StaffSessionStore afterRestart = new DatabaseStaffSessionStore(storedSessions, clock);

        // Then
        assertEquals("Sarah", afterRestart.sessionFor(token).orElseThrow().name());
    }

    @Test
    void testSessionFor_WhenTheRowHoldsARoleThisCodeDoesNotKnow_ShouldGiveTheLeastAccess() {
        // WHAT: Test a session row whose role was edited by hand to something unknown
        // WHY: An unreadable role must never turn into more access. Server is the role
        //      with the least; nothing may crash either, or one bad row breaks sign-in.

        // Given
        String token = sessions.createSession(7L, "Sarah", StaffRole.MANAGER);
        StoredSession row = storedSessions.findAll().get(0);
        row.setRole("owner");
        storedSessions.saveAndFlush(row);

        // Then
        assertEquals(StaffRole.SERVER, sessions.sessionFor(token).orElseThrow().role());
    }
}
