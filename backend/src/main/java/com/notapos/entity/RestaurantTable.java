package com.notapos.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import com.notapos.enums.TableStatus;
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

    @NotBlank(message = "Table number is required")
    @Column(name = "table_number", nullable = false, unique = true, length = 10)        // nullable = false (means required field) - unique = true (means no duplicate table numbers)
    private String tableNumber;

    @Column(name = "section", length = 20)
    private String section;

    @NotNull(message = "Seat count is required")
    @Min(value = 0, message = "Seat count cannot be negative")
    @Column(name = "seat_count", nullable = false)
    private Integer seatCount;

    @Column(name = "status", nullable = false, length = 20)
    private String status = TableStatus.AVAILABLE.getValue();

    // WHERE THE TABLE SITS ON THE FLOOR MAP. In JSON these two are "xposition" and
    // "yposition", all lower case, and @JsonProperty (further down) is what says so.
    //
    // The spelling started as an accident. The JSON library (Jackson) worked the name
    // out from the getter, getXPosition(), and its old rule turned a run of capitals at
    // the front into lower case: "XPosition" became "xposition". The Floor Map was
    // written against that. Jackson 3 (which came with Spring Boot 4) fixed the rule and
    // would have sent "xPosition", and every table would have been drawn in the corner.
    // JsonShapeIntegrationTest caught it during the upgrade. The name is now written
    // down, on the four getters and setters below, so no library rule decides it again.
    // (On the getters and setters, not on these fields: with the note on a field, the
    // library sent the field under one name and the getter under another.)
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

    // "default false" is for rows inserted by plain SQL (the seed file), which never mention this
    // column. Without it those rows got NULL, and the Floor Map (which asks for
    // is_quick_order = false) showed no tables at all on a freshly seeded database.
    // The default itself is set in the database by a migration (V2__default_quick_order_flag.sql),
    // which also changed the NULLs that older databases held to false. The text here only has
    // to describe the column truthfully so Hibernate's start-up check passes.
    @Column(name = "is_quick_order", columnDefinition = "boolean default false")
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

    @JsonProperty("xposition")                       // The name in JSON: see the note on the field
    public Integer getXPosition() {
        return xPosition;
    }

    @JsonProperty("xposition")
    public void setXPosition(Integer xPosition) {
        this.xPosition = xPosition;
    }

    @JsonProperty("yposition")
    public Integer getYPosition() {
        return yPosition;
    }

    @JsonProperty("yposition")
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