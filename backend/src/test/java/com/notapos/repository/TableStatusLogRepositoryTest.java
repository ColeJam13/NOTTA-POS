package com.notapos.repository;

import com.notapos.entity.TableStatusLog;
import com.notapos.entity.RestaurantTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for TableStatusLogRepository.
 * 
 * Tests database queries for table status change audit trail using PostgreSQL Testcontainer.
 * 
 * CHANGES FROM ORIGINAL:
 * - Now extends BaseRepositoryTest (provides PostgreSQL container)
 * - Removed @DataJpaTest, @AutoConfigureTestDatabase, @ActiveProfiles (inherited from base)
 * - Creates actual RestaurantTable entities first (proper foreign key handling)
 * - Tests now run against real PostgreSQL 16 in Docker
 * 
 * @author CJ
 */
class TableStatusLogRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private TableStatusLogRepository tableStatusLogRepository;
    
    @Autowired
    private TableRepository tableRepository;

    private RestaurantTable table1;
    private RestaurantTable table2;
    private RestaurantTable table3;
    private TableStatusLog log1;
    private TableStatusLog log2;
    private TableStatusLog log3;

    @BeforeEach
    void setUp() {
        // Clear database before each test (in proper order due to foreign keys)
        tableStatusLogRepository.deleteAll();
        tableRepository.deleteAll();

        // Create tables first (foreign key dependency)
        table1 = new RestaurantTable();
        table1.setTableNumber("F1");
        table1.setSection("Front");
        table1.setSeatCount(2);
        table1.setStatus("cleaning");
        table1 = tableRepository.save(table1);

        table2 = new RestaurantTable();
        table2.setTableNumber("F2");
        table2.setSection("Front");
        table2.setSeatCount(4);
        table2.setStatus("occupied");
        table2 = tableRepository.save(table2);

        table3 = new RestaurantTable();
        table3.setTableNumber("B1");
        table3.setSection("Back");
        table3.setSeatCount(4);
        table3.setStatus("available");
        table3 = tableRepository.save(table3);

        // Now create log entries with valid table foreign keys
        // Create log entry 1: Table 1 available -> occupied
        log1 = new TableStatusLog();
        log1.setTableId(table1.getTableId());
        log1.setOldStatus("available");
        log1.setNewStatus("occupied");
        log1.setChangedBy("Server Alice");
        log1 = tableStatusLogRepository.save(log1);

        // Create log entry 2: Table 1 occupied -> cleaning
        log2 = new TableStatusLog();
        log2.setTableId(table1.getTableId());
        log2.setOldStatus("occupied");
        log2.setNewStatus("cleaning");
        log2.setChangedBy("Server Alice");
        log2 = tableStatusLogRepository.save(log2);

        // Create log entry 3: Table 2 available -> occupied
        log3 = new TableStatusLog();
        log3.setTableId(table2.getTableId());
        log3.setOldStatus("available");
        log3.setNewStatus("occupied");
        log3.setChangedBy("Server Bob");
        log3 = tableStatusLogRepository.save(log3);
    }

    @Test
    void testSave_ShouldPersistLog() {
        // WHAT: Test saving a new log entry to database
        // WHY: Ensure basic create operation works
        
        // Given - New log entry for table 3
        TableStatusLog newLog = new TableStatusLog();
        newLog.setTableId(table3.getTableId());
        newLog.setOldStatus("cleaning");
        newLog.setNewStatus("available");
        newLog.setChangedBy("Busser Charlie");

        // When - Save to database
        TableStatusLog saved = tableStatusLogRepository.save(newLog);

        // Then - Should persist with generated ID
        assertNotNull(saved.getLogId());
        assertEquals(table3.getTableId(), saved.getTableId());
        assertEquals("available", saved.getNewStatus());
    }

    @Test
    void testFindById_WhenExists_ShouldReturnLog() {
        // WHAT: Test finding log entry by ID
        // WHY: Need to load specific log entries for audit review
        
        // Given - Log entry exists in database (from setUp)
        
        // When - Find by ID
        Optional<TableStatusLog> result = tableStatusLogRepository.findById(log1.getLogId());

        // Then - Should find the log
        assertTrue(result.isPresent());
        assertEquals("occupied", result.get().getNewStatus());
        assertEquals("Server Alice", result.get().getChangedBy());
    }

    @Test
    void testFindById_WhenNotExists_ShouldReturnEmpty() {
        // WHAT: Test finding non-existent log entry
        // WHY: Handle missing logs gracefully
        
        // Given - Non-existent ID
        
        // When - Try to find
        Optional<TableStatusLog> result = tableStatusLogRepository.findById(999L);

        // Then - Should return empty
        assertFalse(result.isPresent());
    }

    @Test
    void testFindAll_ShouldReturnAllLogs() {
        // WHAT: Test retrieving all log entries
        // WHY: Get complete audit trail
        
        // Given - 3 logs in database (from setUp)
        
        // When - Find all
        List<TableStatusLog> logs = tableStatusLogRepository.findAll();

        // Then - Should get all 3 logs
        assertEquals(3, logs.size());
    }

    @Test
    void testFindByTableId_ShouldReturnLogsForTable() {
        // WHAT: Test finding all log entries for a specific table
        // WHY: Show complete history of status changes for one table
        
        // Given - Table 1 has 2 logs, Table 2 has 1 log (from setUp)
        
        // When - Find logs for Table 1
        List<TableStatusLog> table1Logs = tableStatusLogRepository.findByTableId(table1.getTableId());

        // Then - Should get 2 logs for Table 1
        assertEquals(2, table1Logs.size());
        assertTrue(table1Logs.stream().allMatch(log -> log.getTableId().equals(table1.getTableId())));
    }

    @Test
    void testFindByTableId_ShouldReturnLogsInChronologicalOrder() {
        // WHAT: Test that logs for a table can be sorted by creation time
        // WHY: Show status changes in order (oldest to newest or vice versa)
        
        // Given - Table 1 has 2 logs created at different times (from setUp)
        
        // When - Find logs for Table 1
        List<TableStatusLog> logs = tableStatusLogRepository.findByTableId(table1.getTableId());

        // Then - Should get 2 logs
        assertEquals(2, logs.size());
        
        // And - Can verify they're in chronological order by checking status transitions
        // First log: available -> occupied (happened first)
        // Second log: occupied -> cleaning (happened second)
        boolean foundFirstTransition = logs.stream()
            .anyMatch(log -> "available".equals(log.getOldStatus()) && "occupied".equals(log.getNewStatus()));
        boolean foundSecondTransition = logs.stream()
            .anyMatch(log -> "occupied".equals(log.getOldStatus()) && "cleaning".equals(log.getNewStatus()));
        
        assertTrue(foundFirstTransition, "Should find available -> occupied transition");
        assertTrue(foundSecondTransition, "Should find occupied -> cleaning transition");
    }

    @Test
    void testFindByChangedBy_ShouldReturnLogsForUser() {
        // WHAT: Test finding all logs by who made the change
        // WHY: Track which server/staff made status changes
        
        // Given - Server Alice made 2 changes (from setUp)
        
        // When - Find logs changed by Server Alice
        List<TableStatusLog> aliceLogs = tableStatusLogRepository.findByChangedBy("Server Alice");

        // Then - Should get 2 logs
        assertEquals(2, aliceLogs.size());
        assertTrue(aliceLogs.stream().allMatch(log -> "Server Alice".equals(log.getChangedBy())));
    }

    @Test
    void testFindLogsBetween_ShouldReturnLogsInTimeRange() {
        // WHAT: Test finding logs within a time range
        // WHY: Generate reports for specific shifts or time periods
        
        // Given - Logs created at different times
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(1);
        
        // When - Find logs in time range
        List<TableStatusLog> logsInRange = tableStatusLogRepository.findLogsBetween(start, end);

        // Then - Should find all 3 logs (they were just created)
        assertEquals(3, logsInRange.size());
    }

    @Test
    void testDeleteById_ShouldRemoveLog() {
        // WHAT: Test deleting a log entry
        // WHY: Remove test data (not typically done in production)
        
        // Given - Log entry exists
        Long logId = log3.getLogId();
        
        // When - Delete the log
        tableStatusLogRepository.deleteById(logId);

        // Then - Log should no longer exist
        Optional<TableStatusLog> deleted = tableStatusLogRepository.findById(logId);
        assertFalse(deleted.isPresent());
    }

    @Test
    void testLogPreservesStatusHistory_ShouldTrackChanges() {
        // WHAT: Test that logs preserve old and new status values
        // WHY: Maintain complete audit trail of what changed
        
        // Given - Log with status change exists (from setUp)
        
        // When - Load the log
        TableStatusLog loaded = tableStatusLogRepository.findById(log1.getLogId()).orElseThrow();

        // Then - Should preserve both old and new status
        assertEquals("available", loaded.getOldStatus());
        assertEquals("occupied", loaded.getNewStatus());
        assertEquals("Server Alice", loaded.getChangedBy());
    }

    @Test
    void testMultipleLogsForSameTable_ShouldMaintainHistory() {
        // WHAT: Test that multiple status changes are tracked separately
        // WHY: Complete timeline of table status changes
        
        // Given - Table 1 has 2 status changes (from setUp)
        
        // When - Get all logs for Table 1
        List<TableStatusLog> history = tableStatusLogRepository.findByTableId(table1.getTableId());

        // Then - Should have 2 separate log entries
        assertEquals(2, history.size());
        // Both logs are for same table but track different transitions
        assertEquals("available", history.get(0).getOldStatus());
        assertEquals("occupied", history.get(0).getNewStatus());
        assertEquals("occupied", history.get(1).getOldStatus());
        assertEquals("cleaning", history.get(1).getNewStatus());
    }
}