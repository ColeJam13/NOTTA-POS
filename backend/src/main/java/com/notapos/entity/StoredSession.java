package com.notapos.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One signed-in person on one device, as kept in the staff_sessions table.
 *
 * The row is found by the HASH of the device's token (tokenHash). The token
 * itself is never stored: see DatabaseStaffSessionStore for why and how.
 *
 * name, role, and mustChangePin are copies of the staff row made at sign-in,
 * so every request is identified by reading this one row.
 *
 * The times are Instant ("a moment", the same in every time zone), stored in
 * "timestamptz" columns. Other entities use LocalDateTime, which has no zone.
 *
 * Not to be confused with StaffSession (in security/), which is what the rest
 * of the backend sees: the signed-in person for the request being handled.
 *
 * @author CJ
 */
@Entity
@Table(name = "staff_sessions")
public class StoredSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    // SHA-256 of the token, as 64 hex characters
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "staff_id", nullable = false, updatable = false)
    private Long staffId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    // True while this person may do nothing but choose a new PIN
    @Column(name = "must_change_pin", nullable = false)
    private Boolean mustChangePin = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // When the session was last used. Written at most every few minutes, not on every request.
    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    public StoredSession() {}

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Long getStaffId() {
        return staffId;
    }

    public void setStaffId(Long staffId) {
        this.staffId = staffId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Boolean getMustChangePin() {
        return mustChangePin;
    }

    public void setMustChangePin(Boolean mustChangePin) {
        this.mustChangePin = mustChangePin;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }
}
