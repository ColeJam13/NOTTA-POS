package com.notapos.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a prep station in the restaurant.
 * 
 * Prep stations are where menu items are prepared (Kitchen, Bar, etc.)
 * Each MenuItem is assigned to a prep station.
 * 
 * @author CJ
 */

@Entity
@Table(name = "prep_stations")
public class PrepStation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prep_station_id")
    private Long prepStationId;

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PrepStation() {}

    public Long getPrepStationId() {
        return prepStationId;
    }

    public void setPrepStationId(Long prepStationId) {
        this.prepStationId = prepStationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

