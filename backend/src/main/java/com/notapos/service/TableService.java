package com.notapos.service;

import org.springframework.transaction.annotation.Transactional;
import com.notapos.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.notapos.dto.TablePositionRequest;
import com.notapos.entity.RestaurantTable;
import com.notapos.repository.TableRepository;

/**
 * Service layer for Table operations.
 * 
 * Contains business logic for managing restaurant tables.
 * Sits between the Controller (REST API) and Repository (database).
 * 
 * @author CJ
 */

@Service                            // Tells Spring "This is a service bean"
@Transactional(readOnly = true)      // Every method runs in a database transaction; read-only unless a method says otherwise
public class TableService {
    
    private final TableRepository tableRepository;

    @Autowired                                              // Dependency Injection of the TableRepository
    public TableService(TableRepository tableRepository) {
        this.tableRepository = tableRepository;
    }

    public List<RestaurantTable> getAllTables() {                   // Get all tables in the restaurant
        return tableRepository.findAll();
    }

    public Optional<RestaurantTable> getTableById(Long id) {        // Get specific table by ID
        return tableRepository.findById(id);
    }

    public Optional<RestaurantTable> getTableByNumber(String tableNumber) {     // Get table by its number
        return tableRepository.findByTableNumber(tableNumber);
    }

    public List<RestaurantTable> getTablesBySection(String section) {       // Get all tables in specific section (Front, bar, patio)
        return tableRepository.findBySection(section);
    }

    public List<RestaurantTable> getTablesByStatus(String status) {         // Get all tables with specific status (available, occupied, needs cleaning)
        return tableRepository.findByStatus(status);
    }

    public List<RestaurantTable> getTablesByQuickOrder(Boolean isQuickOrder) {      // Get tables filtered by Quick Order flag (true = Quick Orders only, false = regular tables only)
        if (Boolean.TRUE.equals(isQuickOrder)) {
            return tableRepository.findByIsQuickOrder(true);
        }
        return tableRepository.findRegularTables();                 // Includes tables whose flag was never set (NULL)
    }

    @Transactional
    public RestaurantTable createTable(RestaurantTable table) {             // Create new table
        // Creating always creates. save() on an object that carries an ID means "update
        // that row", so a request naming an existing table used to rewrite it: its number,
        // its place on the floor, whether it is a Quick Order. Clearing the ID makes this
        // a new row whatever the request said.
        table.setTableId(null);
        return tableRepository.save(table);
    }

    @Transactional
    public RestaurantTable updateTableStatus(Long tableId, String newStatus) {          // Log status changes for tables
        RestaurantTable table = tableRepository.findById(tableId)                       // Find table, update the status, save to database
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + tableId));

        table.setStatus(newStatus);
        return tableRepository.save(table);
    }

    @Transactional
    public void deleteTable(Long id) {                  // Delete table (not recommended, use is_active flag instead)
        tableRepository.deleteById(id);
    }

    @Transactional
    public RestaurantTable updateTable(Long id, RestaurantTable tableUpdate) {
        RestaurantTable table = tableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + id));
        
        if (tableUpdate.getStatus() != null) {
            table.setStatus(tableUpdate.getStatus());
        }
        return tableRepository.save(table);
    }

    /**
     * Save a new Floor Map layout: where each table sits and how it is turned.
     *
     * All the tables are saved in one transaction. If any table in the list does
     * not exist, nothing is saved - a half-saved layout would leave some tables in
     * their new spots and others in their old ones.
     *
     * Only position and rotation are touched. Status, seats, and server stay as they are.
     *
     * @param positions One entry per table that moved (tables left out are not changed)
     * @return The updated tables
     */
    @Transactional
    public List<RestaurantTable> updateLayout(List<TablePositionRequest> positions) {
        // Load every table named in the request with ONE query (not one query per table)
        List<Long> tableIds = positions.stream().map(TablePositionRequest::tableId).toList();
        Map<Long, RestaurantTable> tablesById = tableRepository.findAllById(tableIds).stream()
                .collect(Collectors.toMap(RestaurantTable::getTableId, Function.identity()));

        List<RestaurantTable> moved = new ArrayList<>();
        for (TablePositionRequest position : positions) {
            RestaurantTable table = tablesById.get(position.tableId());
            if (table == null) {
                throw new ResourceNotFoundException("Table not found with id: " + position.tableId());
            }

            table.setXPosition(position.x());
            table.setYPosition(position.y());
            table.setRotation(position.rotation());
            moved.add(table);
        }

        return tableRepository.saveAll(moved);                      // One batched save for the whole layout
    }
}
