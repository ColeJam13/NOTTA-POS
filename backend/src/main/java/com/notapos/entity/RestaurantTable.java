package com.notapos.entity;

import java.time.LocalDateTime;                   // JPA annotations

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Entity representing a physical table in the restaurant.
 * 
 * Tracks table metadata, current status, and assignment.
 * Status changes are logged in TableStatusLog for audit trail.
 * 
 * @author CJ
 */

@Entity                                     // Tells JPA "this is a database table"
@Table(name = "tables")                     // table name in database is "tables"

public class RestaurantTable {

    @Id                                                         // Primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY)         // auto-increment ID value
    @Column(name = "table_id")                                  // column name in database
    private Long tableId;                                       // field name in java

    @Column(name = "table_number", nullable = false, unique = true, length = 10)        // nullable = false (means required field) - unique = true (means no duplicate table numbers)
    private String tableNumber;

    @Column(name = "section", length = 20)
    private String section;

    @Column(name = "seat_count", nullable = false)
    private Integer seatCount;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "available";

    @Column(name = "x_position")
    private Integer xPosition;

    @Column(name = "y_position")
    private Integer yPosition;

    @Column(name = "shape", length = 20)
    private String shape = "square";

    @Column(name = "rotation")
    private Integer rotation = 0;

    @Column(name = "server_name", length = 50)
    private String serverName;

    @Column(name = "is_quick_order")
    private Boolean isQuickOrder = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public RestaurantTable() {}

    public Long getTableId() {
        return tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(String tableNumber) {
        this.tableNumber = tableNumber;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public Integer getSeatCount() {
        return seatCount;
    }

    public void setSeatCount(Integer seatCount) {
        this.seatCount = seatCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getXPosition() {
        return xPosition;
    }

    public void setXPosition(Integer xPosition) {
        this.xPosition = xPosition;
    }

    public Integer getYPosition() {
        return yPosition;
    }

    public void setYPosition(Integer yPosition) {
        this.yPosition = yPosition;
    }

    public String getShape() {
        return shape;
    }

    public void setShape(String shape) {
        this.shape = shape;
    }

    public Integer getRotation() {
        return rotation;
    }

    public void setRotation(Integer rotation) {
        this.rotation = rotation;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public Boolean getIsQuickOrder() {
        return isQuickOrder;
    }

    public void setIsQuickOrder(Boolean isQuickOrder) {
        this.isQuickOrder = isQuickOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}