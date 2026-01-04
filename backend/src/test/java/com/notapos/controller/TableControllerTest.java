package com.notapos.controller;

import com.notapos.entity.RestaurantTable;
import com.notapos.service.TableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for TableController.
 * 
 * Tests REST API endpoints for restaurant table management.
 * Uses MockMvc to simulate HTTP requests without starting full server.
 * 
 * @author CJ
 */

@WebMvcTest(TableController.class)
class TableControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TableService tableService;

    private RestaurantTable testTable;

    @BeforeEach
    void setUp() {
        // Create test table
        testTable = new RestaurantTable();
        testTable.setTableId(1L);
        testTable.setTableNumber("101");
        testTable.setSection("Main Dining");
        testTable.setSeatCount(4);
        testTable.setStatus("available");
    }

    @Test
    void testGetAllTables_ShouldReturnList() throws Exception {
        // WHAT: Test GET /api/tables endpoint
        // WHY: Retrieve all restaurant tables
        
        // Given
        List<RestaurantTable> tables = Arrays.asList(testTable);
        when(tableService.getAllTables()).thenReturn(tables);

        // When/Then
        mockMvc.perform(get("/api/tables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tableNumber").value(101))
                .andExpect(jsonPath("$[0].status").value("available"));

        verify(tableService).getAllTables();
    }

    @Test
    void testGetTableById_WhenExists_ShouldReturnTable() throws Exception {
        // WHAT: Test GET /api/tables/{id} when table exists
        // WHY: Retrieve specific table details
        
        // Given
        when(tableService.getTableById(1L)).thenReturn(Optional.of(testTable));

        // When/Then
        mockMvc.perform(get("/api/tables/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableId").value(1))
                .andExpect(jsonPath("$.tableNumber").value(101));

        verify(tableService).getTableById(1L);
    }

    @Test
    void testGetTableById_WhenNotExists_ShouldReturn404() throws Exception {
        // WHAT: Test GET /api/tables/{id} when table doesn't exist
        // WHY: Handle missing tables gracefully
        
        // Given
        when(tableService.getTableById(999L)).thenReturn(Optional.empty());

        // When/Then
        mockMvc.perform(get("/api/tables/999"))
                .andExpect(status().isNotFound());

        verify(tableService).getTableById(999L);
    }

    @Test
    void testCreateTable_ShouldReturnCreated() throws Exception {
        // WHAT: Test POST /api/tables to create new table
        // WHY: Add tables to restaurant floor plan
        
        // Given
        when(tableService.createTable(any(RestaurantTable.class))).thenReturn(testTable);

        // When/Then
        mockMvc.perform(post("/api/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableNumber\":\"101\",\"section\":\"Main Dining\",\"seatCount\":4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableId").value(1))
                .andExpect(jsonPath("$.tableNumber").value(101));

        verify(tableService).createTable(any(RestaurantTable.class));
    }

    @Test
    void testUpdateTableStatus_ShouldReturnUpdated() throws Exception {
        // WHAT: Test PUT /api/tables/{id}/status to update table status
        // WHY: Mark table as occupied/available/dirty
        
        // Given
        testTable.setStatus("occupied");
        when(tableService.updateTableStatus(eq(1L), eq("occupied"))).thenReturn(testTable);

        // When/Then
        mockMvc.perform(put("/api/tables/1/status")
                .param("status", "occupied"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("occupied"));

        verify(tableService).updateTableStatus(1L, "occupied");
    }

    @Test
    void testUpdateTableStatus_WhenNotFound_ShouldReturn404() throws Exception {
        // WHAT: Test PUT /api/tables/{id}/status when table doesn't exist
        // WHY: Handle updates to missing tables
        
        // Given
        when(tableService.updateTableStatus(eq(999L), eq("occupied")))
                .thenThrow(new RuntimeException("Table not found"));

        // When/Then
        mockMvc.perform(put("/api/tables/999/status")
                .param("status", "occupied"))
                .andExpect(status().isNotFound());

        verify(tableService).updateTableStatus(999L, "occupied");
    }
}