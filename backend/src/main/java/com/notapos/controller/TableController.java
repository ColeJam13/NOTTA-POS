package com.notapos.controller;

import jakarta.validation.Valid;
import com.notapos.dto.TableLayoutRequest;
import com.notapos.entity.RestaurantTable;
import com.notapos.enums.StaffRole;
import com.notapos.security.StaffSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import com.notapos.service.TableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.notapos.security.Permissions;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * REST API Controller for Table operations.
 * 
 * Exposes HTTP endpoints for managing restaurant tables.
 * 
 * @author CJ
 */

@RestController
@RequestMapping("/api/tables")
public class TableController {

    private final TableService tableService;

    @Autowired
    public TableController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping
    public ResponseEntity<List<RestaurantTable>> getAllTables(
            @RequestParam(required = false) Boolean quickOrders) {
        
        List<RestaurantTable> tables;
        if (quickOrders != null) {
            tables = tableService.getTablesByQuickOrder(quickOrders);
        } else {
            tables = tableService.getAllTables();
        }
        
        return ResponseEntity.ok(tables);
    }

    @GetMapping("/{id}")                                                                // Get table by ID
    public ResponseEntity<RestaurantTable> getTableById(@PathVariable Long id) {
        return tableService.getTableById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }

    // This endpoint does two jobs, with two different rules.
    //   A Quick Order is kept as a row in the tables list, so starting one creates a
    //   "table". Anyone who takes orders may do that.
    //   A table on the FLOOR is part of the floor layout, which is the manager's.
    // @PreAuthorize can only look at the role, so it lets in everyone who takes orders
    // and the second rule is checked below, once the request has been read.
    @PreAuthorize(Permissions.TAKE_ORDERS)
    @PostMapping                                                                                    // Create new table
    public ResponseEntity<RestaurantTable> createTable(
            @Valid @RequestBody RestaurantTable table,
            @AuthenticationPrincipal StaffSession currentStaff) {            // The signed-in person
        boolean isQuickOrder = Boolean.TRUE.equals(table.getIsQuickOrder());
        if (!isQuickOrder && currentStaff.role() != StaffRole.MANAGER) {
            // Answered the same way as a failed @PreAuthorize: 403, naming the role (GlobalExceptionHandler)
            throw new AccessDeniedException("Only a manager may add a table to the floor");
        }

        RestaurantTable created = tableService.createTable(table);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize(Permissions.MANAGER_ONLY)                                                 // Only a manager may rearrange the floor
    @PutMapping("/layout")                                                              // Save the Floor Map layout: position + rotation for many tables at once
    public ResponseEntity<List<RestaurantTable>> updateLayout(@Valid @RequestBody TableLayoutRequest layout) {
        return ResponseEntity.ok(tableService.updateLayout(layout.tables()));
    }

    // Not in the role matrix, and no screen calls it: opening and paying an order set the
    // table's status by themselves. Managers only until a feature (the host view) needs it.
    @PreAuthorize(Permissions.MANAGER_ONLY)
    @PutMapping("/{id}/status")                                                         // Update table status
    public ResponseEntity<RestaurantTable> updateTableStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        RestaurantTable updated = tableService.updateTableStatus(id, status);
        return ResponseEntity.ok(updated);
    }

    // Not in the role matrix, and no screen calls it. Managers only until a feature needs it
    // and opens it on purpose.
    @PreAuthorize(Permissions.MANAGER_ONLY)
    @PutMapping("/{id}")
    public ResponseEntity<RestaurantTable> updateTable(
            @PathVariable Long id,
            @RequestBody RestaurantTable tableUpdate) {
        RestaurantTable updated = tableService.updateTable(id, tableUpdate);
        return ResponseEntity.ok(updated);
            }
    
}