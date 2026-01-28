package com.notapos.repository;

import com.notapos.entity.RestaurantTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for TableRepository.
 * 
 * Tests database queries for table management using PostgreSQL Testcontainer.
 * 
 * CHANGES FROM ORIGINAL:
 * - Now extends BaseRepositoryTest (provides PostgreSQL container)
 * - Removed @DataJpaTest, @AutoConfigureTestDatabase, @ActiveProfiles (inherited from base)
 * - Tests now run against real PostgreSQL 16 in Docker
 * 
 * @author CJ
 */
class TableRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private TableRepository tableRepository;

    private RestaurantTable frontTable;
    private RestaurantTable backTable;
    private RestaurantTable occupiedTable;

    @BeforeEach
    void setUp() {
        // Clear database before each test
        tableRepository.deleteAll();

        // Create available table in Front section
        frontTable = new RestaurantTable();
        frontTable.setTableNumber("F1");
        frontTable.setSection("Front");
        frontTable.setSeatCount(2);
        frontTable.setStatus("available");
        frontTable = tableRepository.save(frontTable);

        // Create available table in Back section
        backTable = new RestaurantTable();
        backTable.setTableNumber("B1");
        backTable.setSection("Back");
        backTable.setSeatCount(4);
        backTable.setStatus("available");
        backTable = tableRepository.save(backTable);

        // Create occupied table in Front section
        occupiedTable = new RestaurantTable();
        occupiedTable.setTableNumber("F2");
        occupiedTable.setSection("Front");
        occupiedTable.setSeatCount(4);
        occupiedTable.setStatus("occupied");
        occupiedTable = tableRepository.save(occupiedTable);
    }

    @Test
    void testSave_ShouldPersistTable() {
        // WHAT: Test saving a new table to database
        // WHY: Ensure basic create operation works
        
        // Given - New table
        RestaurantTable newTable = new RestaurantTable();
        newTable.setTableNumber("BAR-1");
        newTable.setSection("Bar");
        newTable.setSeatCount(6);
        newTable.setStatus("available");

        // When - Save to database
        RestaurantTable saved = tableRepository.save(newTable);

        // Then - Should persist with generated ID
        assertNotNull(saved.getTableId());
        assertEquals("BAR-1", saved.getTableNumber());
        assertEquals("Bar", saved.getSection());
    }

    @Test
    void testFindById_WhenExists_ShouldReturnTable() {
        // WHAT: Test finding table by ID
        // WHY: Need to load specific tables for status updates
        
        // Given - Front table exists in database (from setUp)
        
        // When - Find by ID
        Optional<RestaurantTable> result = tableRepository.findById(frontTable.getTableId());

        // Then - Should find the table
        assertTrue(result.isPresent());
        assertEquals("F1", result.get().getTableNumber());
        assertEquals("Front", result.get().getSection());
    }

    @Test
    void testFindById_WhenNotExists_ShouldReturnEmpty() {
        // WHAT: Test finding non-existent table
        // WHY: Handle missing tables gracefully
        
        // Given - Non-existent ID
        
        // When - Try to find
        Optional<RestaurantTable> result = tableRepository.findById(999L);

        // Then - Should return empty
        assertFalse(result.isPresent());
    }

    @Test
    void testFindAll_ShouldReturnAllTables() {
        // WHAT: Test retrieving all tables
        // WHY: Get complete restaurant floor plan
        
        // Given - 3 tables in database (from setUp)
        
        // When - Find all
        List<RestaurantTable> tables = tableRepository.findAll();

        // Then - Should get all 3 tables
        assertEquals(3, tables.size());
    }

    @Test
    void testFindByTableNumber_ShouldReturnTable() {
        // WHAT: Test finding table by table number
        // WHY: Servers reference tables by number (F1, B1, etc.)
        
        // Given - F1 exists (from setUp)
        
        // When - Find by table number
        Optional<RestaurantTable> result = tableRepository.findByTableNumber("F1");

        // Then - Should find the table
        assertTrue(result.isPresent());
        assertEquals("F1", result.get().getTableNumber());
        assertEquals(2, result.get().getSeatCount());
    }

    @Test
    void testFindBySection_ShouldReturnTablesInSection() {
        // WHAT: Test finding all tables in a section
        // WHY: Show server all tables in their assigned section
        
        // Given - 2 Front section tables exist (from setUp)
        
        // When - Find tables in Front section
        List<RestaurantTable> frontTables = tableRepository.findBySection("Front");

        // Then - Should get 2 Front tables
        assertEquals(2, frontTables.size());
        assertTrue(frontTables.stream().allMatch(t -> "Front".equals(t.getSection())));
    }

    @Test
    void testFindByStatus_Available_ShouldReturnAvailableTables() {
        // WHAT: Test finding all available tables
        // WHY: Show host which tables can seat new guests
        
        // Given - 2 available tables exist (from setUp)
        
        // When - Find available tables
        List<RestaurantTable> available = tableRepository.findByStatus("available");

        // Then - Should get 2 available tables
        assertEquals(2, available.size());
        assertTrue(available.stream().allMatch(t -> "available".equals(t.getStatus())));
    }

    @Test
    void testFindByStatus_Occupied_ShouldReturnOccupiedTables() {
        // WHAT: Test finding all occupied tables
        // WHY: Show servers which tables have active guests
        
        // Given - 1 occupied table exists (from setUp)
        
        // When - Find occupied tables
        List<RestaurantTable> occupied = tableRepository.findByStatus("occupied");

        // Then - Should get 1 occupied table
        assertEquals(1, occupied.size());
        assertEquals("occupied", occupied.get(0).getStatus());
        assertEquals("F2", occupied.get(0).getTableNumber());
    }

    @Test
    void testFindBySectionAndStatus_ShouldReturnMatchingTables() {
        // WHAT: Test finding tables by both section and status
        // WHY: Show available tables in specific section
        
        // Given - 1 available Front table (F1 from setUp)
        
        // When - Find available tables in Front section
        List<RestaurantTable> availableFront = tableRepository.findBySectionAndStatus("Front", "available");

        // Then - Should get only F1
        assertEquals(1, availableFront.size());
        assertEquals("F1", availableFront.get(0).getTableNumber());
        assertEquals("available", availableFront.get(0).getStatus());
    }

    @Test
    void testFindByIsQuickOrder_False_ShouldReturnRegularTables() {
        // WHAT: Test finding regular tables (not Quick Orders)
        // WHY: Filter out Quick Orders from floor map display
        
        // Given - All existing tables are regular tables (isQuickOrder defaults to false)
        
        // When - Find regular tables
        List<RestaurantTable> regularTables = tableRepository.findByIsQuickOrder(false);

        // Then - Should get all 3 regular tables (F1, B1, F2)
        assertEquals(3, regularTables.size());
        assertTrue(regularTables.stream().allMatch(t -> !t.getIsQuickOrder()));
    }

    @Test
    void testFindByIsQuickOrder_True_ShouldReturnQuickOrders() {
        // WHAT: Test finding Quick Order tables
        // WHY: Get only Quick Orders (QO1, QO2, etc.)
        
        // Given - Create Quick Order tables
        RestaurantTable quickOrder1 = new RestaurantTable();
        quickOrder1.setTableNumber("QO1");
        quickOrder1.setSection("Quick Orders");
        quickOrder1.setSeatCount(1);
        quickOrder1.setStatus("available");
        quickOrder1.setIsQuickOrder(true);
        tableRepository.save(quickOrder1);

        RestaurantTable quickOrder2 = new RestaurantTable();
        quickOrder2.setTableNumber("QO2");
        quickOrder2.setSection("Quick Orders");
        quickOrder2.setSeatCount(1);
        quickOrder2.setStatus("available");
        quickOrder2.setIsQuickOrder(true);
        tableRepository.save(quickOrder2);

        // When - Find Quick Order tables
        List<RestaurantTable> quickOrders = tableRepository.findByIsQuickOrder(true);

        // Then - Should get only the 2 Quick Orders
        assertEquals(2, quickOrders.size());
        assertTrue(quickOrders.stream().allMatch(RestaurantTable::getIsQuickOrder));
        assertTrue(quickOrders.stream().anyMatch(t -> "QO1".equals(t.getTableNumber())));
        assertTrue(quickOrders.stream().anyMatch(t -> "QO2".equals(t.getTableNumber())));
    }

    @Test
    void testFindByIsQuickOrder_Mixed_ShouldSeparateCorrectly() {
        // WHAT: Test that Quick Orders and regular tables are properly separated
        // WHY: Ensure filtering works correctly with mixed table types
        
        // Given - Add Quick Orders alongside existing regular tables
        RestaurantTable quickOrder = new RestaurantTable();
        quickOrder.setTableNumber("QO1");
        quickOrder.setSection("Quick Orders");
        quickOrder.setSeatCount(1);
        quickOrder.setStatus("available");
        quickOrder.setIsQuickOrder(true);
        tableRepository.save(quickOrder);

        // When - Query both types
        List<RestaurantTable> regularTables = tableRepository.findByIsQuickOrder(false);
        List<RestaurantTable> quickOrders = tableRepository.findByIsQuickOrder(true);

        // Then - Should separate correctly
        assertEquals(3, regularTables.size()); // F1, B1, F2
        assertEquals(1, quickOrders.size());   // QO1
        
        // Verify no overlap
        assertTrue(regularTables.stream().noneMatch(RestaurantTable::getIsQuickOrder));
        assertTrue(quickOrders.stream().allMatch(RestaurantTable::getIsQuickOrder));
    }

    @Test
    void testDeleteById_ShouldRemoveTable() {
        // WHAT: Test deleting a table
        // WHY: Remove tables during restaurant layout changes
        
        // Given - Back table exists
        Long tableId = backTable.getTableId();
        
        // When - Delete the table
        tableRepository.deleteById(tableId);

        // Then - Table should no longer exist
        Optional<RestaurantTable> deleted = tableRepository.findById(tableId);
        assertFalse(deleted.isPresent());
    }

    @Test
    void testUpdate_ShouldModifyExistingTable() {
        // WHAT: Test updating a table's fields
        // WHY: Change table status or configuration
        
        // Given - Front table exists
        Long tableId = frontTable.getTableId();
        
        // When - Update status and seat count
        frontTable.setStatus("occupied");
        frontTable.setSeatCount(3);
        RestaurantTable updated = tableRepository.save(frontTable);

        // Then - Changes should persist
        RestaurantTable reloaded = tableRepository.findById(tableId).orElseThrow();
        assertEquals("occupied", reloaded.getStatus());
        assertEquals(3, reloaded.getSeatCount());
    }

    @Test
    void testFindByTableNumber_Unique_ShouldNotAllowDuplicates() {
        // WHAT: Test that table numbers are unique
        // WHY: Can't have two tables with same number (database constraint)
        
        // Given - F1 already exists (from setUp)
        RestaurantTable duplicate = new RestaurantTable();
        duplicate.setTableNumber("F1"); // Same as existing
        duplicate.setSection("Back");
        duplicate.setSeatCount(2);
        duplicate.setStatus("available");

        // When/Then - Attempting to save should fail (unique constraint)
        assertThrows(Exception.class, () -> {
            tableRepository.save(duplicate);
            tableRepository.flush(); // Force database write to trigger constraint
        });
    }
}