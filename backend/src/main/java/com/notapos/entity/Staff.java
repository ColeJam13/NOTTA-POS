package com.notapos.entity;

import com.notapos.enums.StaffRole;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A person who works at the restaurant and signs in to the POS with a PIN.
 *
 * The PIN itself is never stored. Only its hash is (pinHash) - a scrambled
 * form that can be checked against a typed PIN but cannot be turned back
 * into one. See StaffService for how it is made and checked.
 *
 * This entity is never sent to the frontend directly, so the hash cannot
 * leak into a response by accident. Responses use StaffView instead.
 *
 * @author CJ
 */
@Entity
@Table(name = "staff")
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "staff_id")
    private Long staffId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "role", nullable = false, length = 20)
    private String role = StaffRole.SERVER.getValue();

    @Column(name = "pin_hash", nullable = false, length = 100)
    private String pinHash;

    // False = no longer works here. They cannot sign in, but stay in the table so
    // old orders still show who rang them in.
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // True = this person holds the starter PIN and must choose their own before doing
    // anything else. Set on the first manager of a new database (StaffService.ensureFirstManager).
    @Column(name = "must_change_pin", nullable = false)
    private Boolean mustChangePin = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Staff() {}

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
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

    public String getPinHash() {
        return pinHash;
    }

    public void setPinHash(String pinHash) {
        this.pinHash = pinHash;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Boolean getMustChangePin() {
        return mustChangePin;
    }

    public void setMustChangePin(Boolean mustChangePin) {
        this.mustChangePin = mustChangePin;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
