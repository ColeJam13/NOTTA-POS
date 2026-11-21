package com.notapos.security;

import com.notapos.entity.StoredSession;
import com.notapos.enums.StaffRole;
import com.notapos.repository.StoredSessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * The list of who is signed in, kept in the database (table staff_sessions).
 *
 * See StaffSessionStore for what a session is and who asks for one.
 *
 * WHY THE DATABASE: the list used to be a map in this program's memory.
 * Restarting the backend emptied it and signed the whole floor out. A row in
 * the database is still there after a restart.
 *
 * THE TOKEN IS NOT STORED. The device gets the token; the database gets its
 * SHA-256 hash (see SessionTokens). To identify a request, hash the token it
 * sent and look for a row with that hash. Someone holding a copy of the
 * database holds hashes, which cannot be sent as tokens.
 *
 * EVERY REQUEST READS ONE ROW HERE (through StaffTokenFilter), by a unique
 * index. It writes only when "last used" is more than TOUCH_INTERVAL old.
 *
 * Each method is its own short database transaction (@Transactional), or joins
 * the caller's when StaffService calls it while saving a staff change: then
 * "the PIN was changed" and "their sessions were ended" succeed or fail together.
 *
 * @author CJ
 */
@Component
@Transactional
public class DatabaseStaffSessionStore implements StaffSessionStore {

    private final StoredSessionRepository storedSessions;
    private final Clock clock;                                      // "What time is it now?" (a test can hand in its own)

    public DatabaseStaffSessionStore(StoredSessionRepository storedSessions, Clock clock) {
        this.storedSessions = storedSessions;
        this.clock = clock;
    }

    @Override
    public String createSession(Long staffId, String name, StaffRole role, boolean mustChangePin) {
        String token = SessionTokens.newToken();
        Instant now = clock.instant();

        StoredSession stored = new StoredSession();
        stored.setTokenHash(SessionTokens.hash(token));             // The hash goes in the database...
        stored.setStaffId(staffId);
        stored.setName(name);
        stored.setRole(role.getValue());
        stored.setMustChangePin(mustChangePin);
        stored.setCreatedAt(now);
        stored.setLastUsedAt(now);
        storedSessions.save(stored);

        return token;                                               // ...and the token goes to the device, and is kept nowhere else
    }

    @Override
    public Optional<StaffSession> sessionFor(String token) {
        if (token == null) {
            return Optional.empty();
        }

        String tokenHash = SessionTokens.hash(token);
        Optional<StoredSession> found = storedSessions.findByTokenHash(tokenHash);
        if (found.isEmpty()) {
            return Optional.empty();                                // Not a token the backend handed out, or one that was ended
        }

        StoredSession stored = found.get();
        Instant now = clock.instant();
        Instant lastUsed = stored.getLastUsedAt();
        Duration idle = Duration.between(lastUsed, now);

        if (idle.compareTo(IDLE_LIMIT) > 0) {
            storedSessions.deleteByTokenHash(tokenHash);            // Not used for too long
            return Optional.empty();
        }

        // Remember that it was just used, so a session in use never times out mid-shift.
        // Only written when the note is getting old (see TOUCH_INTERVAL).
        if (idle.compareTo(TOUCH_INTERVAL) > 0) {
            storedSessions.touch(tokenHash, now);
            lastUsed = now;
        }

        // A role this code does not know (the row was edited by hand) gets the least access
        StaffRole role = StaffRole.fromValue(stored.getRole()).orElse(StaffRole.SERVER);

        return Optional.of(new StaffSession(
                stored.getStaffId(), stored.getName(), role, lastUsed, Boolean.TRUE.equals(stored.getMustChangePin())));
    }

    @Override
    public void endSession(String token) {
        if (token != null) {
            storedSessions.deleteByTokenHash(SessionTokens.hash(token));
        }
    }

    @Override
    public void endSessionsFor(Long staffId) {
        storedSessions.deleteByStaffId(staffId);
    }

    @Override
    public void pinChangedBy(Long staffId, String token) {
        if (token == null) {
            storedSessions.deleteByStaffId(staffId);                // No device to keep signed in
            return;
        }
        storedSessions.deleteOthersByStaffId(staffId, SessionTokens.hash(token));
        storedSessions.clearMustChangePin(staffId);                 // Only this device's session is left
    }

    @Override
    public void renameSessionsFor(Long staffId, String name) {
        storedSessions.rename(staffId, name);
    }

    @Override
    public int endIdleSessions() {
        return storedSessions.deleteLastUsedBefore(clock.instant().minus(IDLE_LIMIT));
    }
}
