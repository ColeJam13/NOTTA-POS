package com.notapos.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
        return tableRepository.findByIsQuickOrder(isQuickOrder);
    }

    public RestaurantTable createTable(RestaurantTable table) {             // Create new table
        return tableRepository.save(table);
    }

    public RestaurantTable updateTableStatus(Long tableId, String newStatus) {          // Log status changes for tables
        RestaurantTable table = tableRepository.findById(tableId)                       // Find table, update the status, save to database
                .orElseThrow(() -> new RuntimeException("Table not found with id: " + tableId));

        table.setStatus(newStatus);
        return tableRepository.save(table);
    }

    public void deleteTable(Long id) {                  // Delete table (not recommended, use is_active flag instead)
        tableRepository.deleteById(id);
    }

    public RestaurantTable updateTable(Long id, RestaurantTable tableUpdate) {
        RestaurantTable table = tableRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Table not found with id: " + id));
        
        if (tableUpdate.getStatus() != null) {
            table.setStatus(tableUpdate.getStatus());
        }
        return tableRepository.save(table);
    }
}