package com.notapos.controller;

import com.notapos.entity.RestaurantTable;
import com.notapos.service.TableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<List<RestaurantTable>> getAllTables() {
        List<RestaurantTable> tables = tableService.getAllTables();
        return ResponseEntity.ok(tables);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RestaurantTable> getTableById(@PathVariable Long id) {
        return tableService.getTableById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<RestaurantTable> createTable(@RequestBody RestaurantTable table) {
        RestaurantTable created = tableService.createTable(table);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<RestaurantTable> updateTableStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        try {
            RestaurantTable updated = tableService.updateTableStatus(id, status);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
