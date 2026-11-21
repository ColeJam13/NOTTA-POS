package com.notapos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One setting a manager chooses while the app is running, as kept in the
 * settings table: a name ("template") and its value ("8-bit").
 *
 * This class is only ever READ. A setting is written by
 * SettingRepository.put, one SQL statement that adds the row or replaces it,
 * so there are no setters here.
 *
 * The ID is the setting's name. That is unlike the other entities, whose IDs
 * are numbers the database hands out: a setting is looked up by what it is
 * called, and there can only ever be one row with that name.
 *
 * @author CJ
 */
@Entity
@Table(name = "settings")
public class Setting {

    @Id
    @Column(name = "setting_key", length = 50)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 200)
    private String value;

    // When it was last changed. Instant = a moment, the same in every time zone (timestamptz).
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Who last changed it: a plain ID (no JPA relationship) and a copy of their name
    @Column(name = "updated_by_staff_id")
    private Long updatedByStaffId;

    @Column(name = "updated_by_name", length = 50)
    private String updatedByName;

    public Setting() {}

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedByStaffId() {
        return updatedByStaffId;
    }

    public String getUpdatedByName() {
        return updatedByName;
    }
}
