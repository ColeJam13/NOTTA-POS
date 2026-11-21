package com.notapos.security;

import com.notapos.enums.StaffRole;

import java.time.Instant;

/**
 * One signed-in person.
 *
 * @param staffId  Who it is
 * @param name     Their name, as shown on orders
 * @param role     What they are allowed to do
 * @param lastUsed When the session was last used
 *
 * @author CJ
 */
public record StaffSession(Long staffId, String name, StaffRole role, Instant lastUsed) {
}
