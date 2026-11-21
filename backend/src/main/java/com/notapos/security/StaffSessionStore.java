package com.notapos.security;

import com.notapos.enums.StaffRole;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the list of who is signed in.
 *
 * WHAT: A "session" is the backend remembering that a person signed in. Each
 *       one is filed under a long random "token", which the frontend sends
 *       back with every request to say who is asking.
 *
 * This class only keeps the list: start a session, look one up, end one. It
 * does not decide what anyone may do. Other classes ask it:
 *   - StaffService starts a session when a PIN is right, and ends sessions on
 *     sign-out or when a person's PIN, role, or active flag changes.
 *   - The check that runs before each request looks the token up here.
 *
 * WHY IT IS ITS OWN CLASS: the list used to live inside the class that also
 * refused requests. Keeping it apart means the way requests are checked and
 * the place sessions are stored can each change without the other noticing.
 * (Step A4 of the Phase 2 plan moves the list from memory into the database.)
 *
 * Sessions are kept in memory, in this object. Restarting the backend signs
 * everyone out, and a session that has not been used for 12 hours is forgotten.
 * (A restaurant with several backend servers would keep sessions in the
 * database instead; one server is all this app runs on today.)
 *
 * @author CJ
 */
@Component
public class StaffSessionStore {

    // A session that has not been used for this long is forgotten
    private static final Duration IDLE_LIMIT = Duration.ofHours(12);

    // token -> who it belongs to. ConcurrentHashMap is safe to use from many requests at once.
    private final Map<String, StaffSession> sessions = new ConcurrentHashMap<>();

    // SecureRandom produces numbers that cannot be predicted, which is what a token needs
    private final SecureRandom random = new SecureRandom();

    /**
     * Start a session for someone who just signed in.
     *
     * @return The token the frontend must send with each request
     */
    public String createSession(Long staffId, String name, StaffRole role) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        sessions.put(token, new StaffSession(staffId, name, role, Instant.now()));
        return token;
    }

    public void endSession(String token) {                          // Sign out
        if (token != null) {
            sessions.remove(token);
        }
    }

    public void endSessionsFor(Long staffId) {                      // Sign a person out everywhere (their PIN or role changed, or they were deactivated)
        sessions.values().removeIf(session -> session.staffId().equals(staffId));
    }

    public Optional<StaffSession> sessionFor(String token) {        // Who a token belongs to, if it is still good
        if (token == null) {
            return Optional.empty();
        }

        StaffSession session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }

        if (Duration.between(session.lastUsed(), Instant.now()).compareTo(IDLE_LIMIT) > 0) {
            sessions.remove(token);                                 // Not used for too long
            return Optional.empty();
        }

        // Remember that it was just used, so an active session never times out mid-shift
        StaffSession refreshed = new StaffSession(session.staffId(), session.name(), session.role(), Instant.now());
        sessions.put(token, refreshed);
        return Optional.of(refreshed);
    }
}
