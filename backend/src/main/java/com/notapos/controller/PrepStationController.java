package com.notapos.controller;

import com.notapos.entity.PrepStation;
import com.notapos.service.PrepStationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * REST API Controller for PrepStation operations.
 * 
 * @author CJ
 */

@RestController
@RequestMapping("/api/prep-stations")
public class PrepStationController {
    
    private final PrepStationService prepStationService;

    @Autowired
    public PrepStationController(PrepStationService prepStationService) {
        this.prepStationService = prepStationService;
    }

    @GetMapping
    public ResponseEntity<List<PrepStation>> getAllPrepStations(                        // Get all prep stations
            @RequestParam(required = false) Boolean active) {

        if (active != null && active) {
            return ResponseEntity.ok(prepStationService.getActivePrepStations());
        }
        return ResponseEntity.ok(prepStationService.getAllPrepStations());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PrepStation> getPrepStationById(@PathVariable Long id) {      // Get prep station by ID
        return prepStationService.getPrepStationById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PrepStation> createPrepStation(@RequestBody PrepStation prepStation) {        // Create prep station
        PrepStation created = prepStationService.createPrepStation(prepStation);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PrepStation> updatePrepStation(                       // Update prep station
            @PathVariable Long id,
            @RequestBody PrepStation prepStation) {
        try {
            PrepStation updated = prepStationService.updatePrepStation(id, prepStation);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePrepStation(@PathVariable Long id) {          // Delete prep station
        prepStationService.deletePrepStation(id);
        return ResponseEntity.noContent().build();
    }
}
