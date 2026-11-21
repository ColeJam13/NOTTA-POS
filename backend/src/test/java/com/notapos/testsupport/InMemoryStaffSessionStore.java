package com.notapos.testsupport;

import com.notapos.enums.StaffRole;
import com.notapos.security.SessionTokens;
import com.notapos.security.StaffSession;
import com.notapos.security.StaffSessionStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The list of who is signed in, kept in memory. FOR TESTS ONLY.
 *
 * WHY IT EXISTS: the real list is in the database (DatabaseStaffSessionStore).
 * Controller tests and service tests start no database: they check one layer
 * with the rest faked. They still need real sessions, so that requests go
 * through the real "who may do this" check. This keeps the list in a map
 * instead.
 *
 * It is what the app itself used until step A4 of the Phase 2 plan.
 *
 * IT MUST BEHAVE LIKE THE REAL ONE, or tests that pass with it would say
 * nothing about the app. StaffSessionStoreContract is one set of tests that is
 * run against both (InMemoryStaffSessionStoreTest, DatabaseStaffSessionStoreTest).
 *
 * @author CJ
 */
public class InMemoryStaffSessionStore implements StaffSessionStore {

    // hash of the token -> who it belongs to. Filed by hash, like the real store.
    // ConcurrentHashMap is safe to use from many requests at once.
    private final Map<String, StaffSession> sessions = new ConcurrentHashMap<>();

    private final Clock clock;

    public InMemoryStaffSessionStore() {                            // The real time (what @ControllerTest uses)
        this(Clock.systemUTC());
    }

    public InMemoryStaffSessionStore(Clock clock) {                 // A clock the test moves by hand
        this.clock = clock;
    }

    @Override
    public String createSession(Long staffId, String name, StaffRole role, boolean mustChangePin) {
        String token = SessionTokens.newToken();
        sessions.put(SessionTokens.hash(token), new StaffSession(staffId, name, role, clock.instant(), mustChangePin));
        return token;
    }

    @Override
    public Optional<StaffSession> sessionFor(String token) {
        if (token == null) {
            return Optional.empty();
        }

        String tokenHash = SessionTokens.hash(token);
        StaffSession session = sessions.get(tokenHash);
        if (session == null) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        Duration idle = Duration.between(session.lastUsed(), now);
        if (idle.compareTo(IDLE_LIMIT) > 0) {
            sessions.remove(tokenHash);                             // Not used for too long
            return Optional.empty();
        }

        if (idle.compareTo(TOUCH_INTERVAL) > 0) {                   // Note that it was just used, as often as the real store does
            session = new StaffSession(session.staffId(), session.name(), session.role(), now, session.mustChangePin());
            sessions.put(tokenHash, session);
        }
        return Optional.of(session);
    }

    @Override
    public void endSession(String token) {
        if (token != null) {
            sessions.remove(SessionTokens.hash(token));
        }
    }

    @Override
    public void endSessionsFor(Long staffId) {
        sessions.values().removeIf(session -> session.staffId().equals(staffId));
    }

    @Override
    public void pinChangedBy(Long staffId, String token) {
        String keep = token == null ? null : SessionTokens.hash(token);
        sessions.entrySet().removeIf(entry -> entry.getValue().staffId().equals(staffId) && !entry.getKey().equals(keep));
        sessions.replaceAll((tokenHash, session) -> session.staffId().equals(staffId)
                ? new StaffSession(session.staffId(), session.name(), session.role(), session.lastUsed(), false)
                : session);
    }

    @Override
    public void renameSessionsFor(Long staffId, String name) {
        sessions.replaceAll((tokenHash, session) -> session.staffId().equals(staffId)
                ? new StaffSession(session.staffId(), name, session.role(), session.lastUsed(), session.mustChangePin())
                : session);
    }

    @Override
    public int endIdleSessions() {
        Instant cutoff = clock.instant().minus(IDLE_LIMIT);
        int before = sessions.size();
        sessions.values().removeIf(session -> session.lastUsed().isBefore(cutoff));
        return before - sessions.size();
    }
}
