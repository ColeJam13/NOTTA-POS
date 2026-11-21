package com.notapos.security;

import com.notapos.enums.StaffRole;

import java.time.Duration;
import java.util.Optional;

/**
 * Keeps the list of who is signed in.
 *
 * WHAT: A "session" is the backend remembering that a person signed in. Each
 *       one is filed under a long random "token", which the frontend sends
 *       back with every request to say who is asking.
 *
 * This only keeps the list: start a session, look one up, end one. It does not
 * decide what anyone may do. Other classes ask it:
 *   - StaffService starts a session when a PIN is right, and ends sessions on
 *     sign-out or when a person's PIN, role, or active flag changes.
 *   - The check that runs before each request (StaffTokenFilter) looks the
 *     token up here.
 *
 * WHY IT IS AN INTERFACE: an interface says WHAT can be asked without saying
 * how it is done. The list used to live in the backend's memory; since step A4
 * of the Phase 2 plan it lives in the database (DatabaseStaffSessionStore), so
 * a backend restart no longer signs everyone out. Nothing that asks the store
 * had to change. Controller tests, which start no database, use a list kept in
 * memory (InMemoryStaffSessionStore, in the test folder); one set of tests
 * (StaffSessionStoreContract) is run against both to keep them behaving alike.
 *
 * WHEN A SESSION ENDS: on SIGN OUT, when a manager changes that person's PIN
 * or role or deactivates them, or when it has not been used for IDLE_LIMIT.
 * It does not end while it is in use. (The owner's decision D3: a shared
 * terminal signs a person out only when SIGN OUT is pressed.)
 *
 * @author CJ
 */
public interface StaffSessionStore {

    // A session that has not been used for this long is forgotten
    Duration IDLE_LIMIT = Duration.ofHours(12);

    // "Last used" is written down at most this often, not on every request. The screens ask
    // the backend something every few seconds; a database write for each would be thousands
    // an hour to record a time that only matters to the nearest hour. The price: a session
    // can be forgotten up to this much sooner than IDLE_LIMIT after its last real use.
    Duration TOUCH_INTERVAL = Duration.ofMinutes(5);

    /**
     * Start a session for someone who just signed in.
     *
     * @param mustChangePin True if they signed in with the starter PIN and must choose their own
     * @return The token the frontend must send with each request
     */
    String createSession(Long staffId, String name, StaffRole role, boolean mustChangePin);

    default String createSession(Long staffId, String name, StaffRole role) {      // The usual case: nothing to change
        return createSession(staffId, name, role, false);
    }

    Optional<StaffSession> sessionFor(String token);                // Who a token belongs to, if it is still good

    void endSession(String token);                                  // Sign out

    void endSessionsFor(Long staffId);                              // Sign a person out everywhere (their PIN or role changed, or they were deactivated)

    /**
     * A person chose a new PIN for themselves, on the device this token belongs to.
     * Sign them out everywhere else, and let this session carry on with nothing
     * left to change.
     */
    void pinChangedBy(Long staffId, String token);

    void renameSessionsFor(Long staffId, String name);              // A manager corrected someone's name: orders they open from now on carry the new one

    int endIdleSessions();                                          // Drop every session not used for IDLE_LIMIT. Returns how many.
}
