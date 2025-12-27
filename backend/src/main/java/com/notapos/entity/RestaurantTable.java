package com.notapos.entity;

import jakarta.persistence.*;                   // JPA annotations
import lombok.AllArgsConstructor;               // Lombok generates boilerplate code
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

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
@Data                                       // Lombok autogenerates (Getters, Setters, toString(), equals(), and hashCode() methods)
@NoArgsConstructor                          // Lombok Generates empty constructor (new RestaurantTable() )
@AllArgsConstructor                         // Lombok Generates constructor with all fields (new RestaurantTable(id, number, etc...) )
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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist                                     // PrePersist means runs BEFORE saving to database
    protected void onCreate() {
        createdAt = LocalDateTime.now();            // automatically sets createdAt timestamp
    }
}