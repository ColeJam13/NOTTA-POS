package com.notapos.controller;

import com.notapos.entity.TableStatusLog;
import com.notapos.service.TableStatusLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import com.notapos.security.Permissions;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * REST API Controller for TableStatusLog operations.
 * 
 * @author CJ
 */

@RestController
@RequestMapping("/api/table-status-logs")
public class TableStatusLogController {

    private final TableStatusLogService tableStatusLogService;

    @Autowired
    public TableStatusLogController(TableStatusLogService tableStatusLogService) {
        this.tableStatusLogService = tableStatusLogService;
    }

    @GetMapping
    public ResponseEntity<List<TableStatusLog>> getAllLogs() {                                      // Get all logs
        return ResponseEntity.ok(tableStatusLogService.getAllLogs());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TableStatusLog> getLogById(@PathVariable Long id) {                       // Get log by ID
        return tableStatusLogService.getLogById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/table/{tableId}")
    public ResponseEntity<List<TableStatusLog>> getLogsByTable(@PathVariable Long tableId) {        // Get logs by table
        return ResponseEntity.ok(tableStatusLogService.getLogsByTable(tableId));
    }

    // Not in the role matrix, and no screen calls it. Managers only until a feature needs it
    // and opens it on purpose.
    @PreAuthorize(Permissions.MANAGER_ONLY)
    @PostMapping
    public ResponseEntity<TableStatusLog> createLog(@RequestBody TableStatusLog log) {              // create new log
        TableStatusLog created = tableStatusLogService.createLog(log);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // Not in the role matrix, and no screen calls it. Managers only until a feature needs it
    // and opens it on purpose.
    @PreAuthorize(Permissions.MANAGER_ONLY)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLog(@PathVariable Long id) {                                  // Delete existing log
        tableStatusLogService.deleteLog(id);
        return ResponseEntity.noContent().build();
    }
    
}
