package com.notapos.security;

import com.notapos.enums.StaffRole;

import java.time.Instant;

/**
 * One signed-in person.
 *
 * @param staffId       Who it is
 * @param name          Their name, as shown on orders
 * @param role          What they are allowed to do
 * @param lastUsed      When the session was last used (as last written down: see StaffSessionStore)
 * @param mustChangePin True while they may do nothing but choose a new PIN (they signed in
 *                      with the starter PIN). SecurityConfig refuses everything else.
 *
 * @author CJ
 */
public record StaffSession(Long staffId, String name, StaffRole role, Instant lastUsed, boolean mustChangePin) {
}
