package com.notapos.service;

import com.notapos.entity.RestaurantTable;
import com.notapos.repository.TableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TableService.
 * 
 * Tests table management functionality.
 * 
 * @author CJ
 */

@ExtendWith(MockitoExtension.class)
class TableServiceTest {

    @Mock
    private TableRepository tableRepository;

    @InjectMocks
    private TableService tableService;

    private RestaurantTable testTable;

    @BeforeEach
    void setUp() {
        // Create a test table (Table F1 in Front section)
        testTable = new RestaurantTable();
        testTable.setTableId(1L);
        testTable.setTableNumber("F1");
        testTable.setSection("Front");
        testTable.setSeatCount(2);
        testTable.setStatus("available");
    }

    @Test
    void testCreateTable_ShouldSaveTable() {
        // WHAT: Test creating a new table
        // WHY: Need to add tables to restaurant layout
        
        // Given - Mock returns saved table
        when(tableRepository.save(any(RestaurantTable.class))).thenReturn(testTable);

        // When - Create table
        RestaurantTable created = tableService.createTable(testTable);

        // Then - Should save and return the table
        assertNotNull(created);
        assertEquals("F1", created.getTableNumber());
        assertEquals("Front", created.getSection());
        assertEquals(2, created.getSeatCount());
        verify(tableRepository, times(1)).save(testTable);
    }

    @Test
    void testGetAllTables_ShouldReturnAllTables() {
        // WHAT: Test retrieving all tables in restaurant
        // WHY: Floor manager needs to see all tables
        
        // Given - Mock returns 2 tables
        RestaurantTable table2 = new RestaurantTable();
        table2.setTableNumber("F2");
        List<RestaurantTable> tables = Arrays.asList(testTable, table2);
        when(tableRepository.findAll()).thenReturn(tables);

        // When - Get all tables
        List<RestaurantTable> result = tableService.getAllTables();

        // Then - Should get both tables
        assertEquals(2, result.size());
        verify(tableRepository, times(1)).findAll();
    }

    @Test
    void testGetTableById_WhenExists_ShouldReturnTable() {
        // WHAT: Test finding a specific table by ID
        // WHY: Need to load table details
        
        // Given - Mock returns the table
        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));

        // When - Get table by ID
        Optional<RestaurantTable> result = tableService.getTableById(1L);

        // Then - Should find the table
        assertTrue(result.isPresent());
        assertEquals("F1", result.get().getTableNumber());
        verify(tableRepository, times(1)).findById(1L);
    }

    @Test
    void testGetTableByNumber_ShouldReturnTable() {
        // WHAT: Test finding table by table number
        // WHY: Servers reference tables by number (F1, BAR-2, etc.)
        
        // Given - Mock returns table by number
        when(tableRepository.findByTableNumber("F1")).thenReturn(Optional.of(testTable));

        // When - Find table F1
        Optional<RestaurantTable> result = tableService.getTableByNumber("F1");

        // Then - Should find the table
        assertTrue(result.isPresent());
        assertEquals("F1", result.get().getTableNumber());
        verify(tableRepository, times(1)).findByTableNumber("F1");
    }

    @Test
    void testGetTablesBySection_ShouldReturnSectionTables() {
        // WHAT: Test getting all tables in a section
        // WHY: Filter by section (Front, Bar, Patio)
        
        // Given - Mock returns front section tables
        List<RestaurantTable> frontTables = Arrays.asList(testTable);
        when(tableRepository.findBySection("Front")).thenReturn(frontTables);

        // When - Get front section tables
        List<RestaurantTable> result = tableService.getTablesBySection("Front");

        // Then - Should get tables in that section
        assertEquals(1, result.size());
        assertEquals("Front", result.get(0).getSection());
        verify(tableRepository, times(1)).findBySection("Front");
    }

    @Test
    void testGetTablesByStatus_ShouldFilterByStatus() {
        // WHAT: Test getting tables by status
        // WHY: See which tables are available, occupied, or need cleaning
        
        // Given - Mock returns available tables
        List<RestaurantTable> availableTables = Arrays.asList(testTable);
        when(tableRepository.findByStatus("available")).thenReturn(availableTables);

        // When - Get available tables
        List<RestaurantTable> result = tableService.getTablesByStatus("available");

        // Then - Should get available tables
        assertEquals(1, result.size());
        assertEquals("available", result.get(0).getStatus());
        verify(tableRepository, times(1)).findByStatus("available");
    }

    @Test
    void testGetTablesByQuickOrder_ShouldFilterByQuickOrderFlag() {
        // WHAT: Test getting tables filtered by Quick Order flag
        // WHY: Separate Quick Orders (QO1, QO2) from physical tables on floor map
        
        // Given - Mock returns regular tables (not Quick Orders)
        List<RestaurantTable> regularTables = Arrays.asList(testTable);
        when(tableRepository.findByIsQuickOrder(false)).thenReturn(regularTables);

        // When - Get regular tables (exclude Quick Orders)
        List<RestaurantTable> result = tableService.getTablesByQuickOrder(false);

        // Then - Should get only regular tables
        assertEquals(1, result.size());
        verify(tableRepository, times(1)).findByIsQuickOrder(false);
    }

    @Test
    void testGetTablesByQuickOrder_ShouldReturnQuickOrders() {
        // WHAT: Test getting only Quick Order tables
        // WHY: Filter for Quick Orders when needed
        
        // Given - Create Quick Order tables
        RestaurantTable quickOrder1 = new RestaurantTable();
        quickOrder1.setTableId(100L);
        quickOrder1.setTableNumber("QO1");
        quickOrder1.setSection("Quick Orders");
        quickOrder1.setIsQuickOrder(true);
        
        List<RestaurantTable> quickOrders = Arrays.asList(quickOrder1);
        when(tableRepository.findByIsQuickOrder(true)).thenReturn(quickOrders);

        // When - Get Quick Order tables
        List<RestaurantTable> result = tableService.getTablesByQuickOrder(true);

        // Then - Should get only Quick Orders
        assertEquals(1, result.size());
        assertEquals("QO1", result.get(0).getTableNumber());
        verify(tableRepository, times(1)).findByIsQuickOrder(true);
    }

    @Test
    void testUpdateTableStatus_ShouldChangeStatus() {
        // WHAT: Test updating table status
        // WHY: Mark table as occupied when guests sit, available when they leave
        
        // Given - Table exists
        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));
        when(tableRepository.save(any(RestaurantTable.class))).thenReturn(testTable);

        // When - Change status to occupied
        RestaurantTable result = tableService.updateTableStatus(1L, "occupied");

        // Then - Status should be updated
        assertEquals("occupied", result.getStatus());
        verify(tableRepository, times(1)).save(testTable);
    }

    @Test
    void testUpdateTableStatus_WhenNotFound_ShouldThrowException() {
        // WHAT: Test error handling when table doesn't exist
        // WHY: Can't update status of non-existent table
        
        // Given - Table doesn't exist
        when(tableRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then - Should throw exception
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            tableService.updateTableStatus(999L, "occupied");
        });
        
        assertTrue(exception.getMessage().contains("Table not found"));
    }

    @Test
    void testDeleteTable_ShouldCallRepository() {
        // WHAT: Test deleting a table
        // WHY: Remove tables from layout (rare - usually just deactivate)
        
        // Given - Mock repository
        doNothing().when(tableRepository).deleteById(1L);

        // When - Delete table
        tableService.deleteTable(1L);

        // Then - Repository delete should be called
        verify(tableRepository, times(1)).deleteById(1L);
    }

    @Test
    void testUpdateTable_ShouldUpdateTableFields() {
        // WHAT: Test updating table details
        // WHY: Need to modify table properties (status, etc.)
        
        // Given - Table exists
        RestaurantTable updateData = new RestaurantTable();
        updateData.setStatus("occupied");
        
        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));
        when(tableRepository.save(any(RestaurantTable.class))).thenReturn(testTable);

        // When - Update table
        RestaurantTable result = tableService.updateTable(1L, updateData);

        // Then - Status should be updated
        assertEquals("occupied", result.getStatus());
        verify(tableRepository, times(1)).findById(1L);
        verify(tableRepository, times(1)).save(testTable);
    }

    @Test
    void testUpdateTable_WhenNotFound_ShouldThrowException() {
        // WHAT: Test updating non-existent table
        // WHY: Handle missing tables gracefully
        
        // Given - Table doesn't exist
        RestaurantTable updateData = new RestaurantTable();
        updateData.setStatus("occupied");
        
        when(tableRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then - Should throw exception
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            tableService.updateTable(999L, updateData);
        });
        
        assertTrue(exception.getMessage().contains("Table not found"));
        verify(tableRepository, never()).save(any());
    }

    @Test
    void testUpdateTable_WhenStatusIsNull_ShouldNotUpdateStatus() {
        // WHAT: Test updating table when status is null
        // WHY: Only update fields that are provided
        
        // Given - Table exists, update has null status
        RestaurantTable updateData = new RestaurantTable();
        updateData.setStatus(null);
        
        String originalStatus = testTable.getStatus();
        when(tableRepository.findById(1L)).thenReturn(Optional.of(testTable));
        when(tableRepository.save(any(RestaurantTable.class))).thenReturn(testTable);

        // When - Update table
        RestaurantTable result = tableService.updateTable(1L, updateData);

        // Then - Status should remain unchanged
        assertEquals(originalStatus, result.getStatus());
        verify(tableRepository, times(1)).save(testTable);
    }
}