package com.notapos.service;

import com.notapos.entity.TableStatusLog;
import com.notapos.repository.TableStatusLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TableStatusLogService.
 * 
 * Tests table status change tracking for audit trail.
 * 
 * @author CJ
 */

@ExtendWith(MockitoExtension.class)
class TableStatusLogServiceTest {

    @Mock
    private TableStatusLogRepository tableStatusLogRepository;

    @InjectMocks
    private TableStatusLogService tableStatusLogService;

    private TableStatusLog testLog;

    @BeforeEach
    void setUp() {
        // Create a test log entry (table F1 changed from available to occupied)
        testLog = new TableStatusLog();
        testLog.setLogId(1L);
        testLog.setTableId(1L);
        testLog.setOldStatus("available");
        testLog.setNewStatus("occupied");
        testLog.setChangedBy("Server Jane");
    }

    @Test
    void testCreateLog_ShouldSaveLogEntry() {
        // WHAT: Test creating a new status change log
        // WHY: Track all table status changes for accountability
        
        // Given - Mock returns saved log
        when(tableStatusLogRepository.save(any(TableStatusLog.class))).thenReturn(testLog);

        // When - Create log entry
        TableStatusLog created = tableStatusLogService.createLog(testLog);

        // Then - Should save and return log
        assertNotNull(created);
        assertEquals(1L, created.getTableId());
        assertEquals("available", created.getOldStatus());
        assertEquals("occupied", created.getNewStatus());
        assertEquals("Server Jane", created.getChangedBy());
        verify(tableStatusLogRepository, times(1)).save(testLog);
    }

    @Test
    void testGetAllLogs_ShouldReturnAllLogs() {
        // WHAT: Test retrieving all log entries
        // WHY: Manager needs full audit trail
        
        // Given - Mock returns 2 logs
        TableStatusLog log2 = new TableStatusLog();
        log2.setTableId(2L);
        List<TableStatusLog> logs = Arrays.asList(testLog, log2);
        when(tableStatusLogRepository.findAll()).thenReturn(logs);

        // When - Get all logs
        List<TableStatusLog> result = tableStatusLogService.getAllLogs();

        // Then - Should get both logs
        assertEquals(2, result.size());
        verify(tableStatusLogRepository, times(1)).findAll();
    }

    @Test
    void testGetLogById_WhenExists_ShouldReturnLog() {
        // WHAT: Test finding a specific log entry by ID
        // WHY: Need to view specific status change details
        
        // Given - Mock returns the log
        when(tableStatusLogRepository.findById(1L)).thenReturn(Optional.of(testLog));

        // When - Get log by ID
        Optional<TableStatusLog> result = tableStatusLogService.getLogById(1L);

        // Then - Should find the log
        assertTrue(result.isPresent());
        assertEquals("occupied", result.get().getNewStatus());
        verify(tableStatusLogRepository, times(1)).findById(1L);
    }

    @Test
    void testGetLogsByTable_ShouldReturnTableLogs() {
        // WHAT: Test getting all logs for a specific table
        // WHY: See history of status changes for table F1
        
        // Given - Mock returns logs for table 1
        List<TableStatusLog> tableLogs = Arrays.asList(testLog);
        when(tableStatusLogRepository.findByTableId(1L)).thenReturn(tableLogs);

        // When - Get logs for table 1
        List<TableStatusLog> result = tableStatusLogService.getLogsByTable(1L);

        // Then - Should get that table's logs
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getTableId());
        verify(tableStatusLogRepository, times(1)).findByTableId(1L);
    }

    @Test
    void testGetLogsByChangedBy_ShouldReturnServerLogs() {
        // WHAT: Test getting all logs by a specific server
        // WHY: Track which server made which changes
        
        // Given - Mock returns logs for Server Jane
        List<TableStatusLog> serverLogs = Arrays.asList(testLog);
        when(tableStatusLogRepository.findByChangedBy("Server Jane")).thenReturn(serverLogs);

        // When - Get logs by Server Jane
        List<TableStatusLog> result = tableStatusLogService.getLogsByChangedBy("Server Jane");

        // Then - Should get that server's logs
        assertEquals(1, result.size());
        assertEquals("Server Jane", result.get(0).getChangedBy());
        verify(tableStatusLogRepository, times(1)).findByChangedBy("Server Jane");
    }

    @Test
    void testGetLogsBetween_ShouldReturnLogsInRange() {
        // WHAT: Test getting logs within a date range
        // WHY: Shift reports, daily audit
        
        // Given - Mock returns logs in range
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 1, 23, 59);
        List<TableStatusLog> logs = Arrays.asList(testLog);
        when(tableStatusLogRepository.findLogsBetween(start, end)).thenReturn(logs);

        // When - Get logs for Jan 1, 2025
        List<TableStatusLog> result = tableStatusLogService.getLogsBetween(start, end);

        // Then - Should get logs in that range
        assertEquals(1, result.size());
        verify(tableStatusLogRepository, times(1)).findLogsBetween(start, end);
    }

    @Test
    void testGetTableLogsBetween_ShouldReturnTableLogsInRange() {
        // WHAT: Test getting logs for a specific table within a date range
        // WHY: See table F1's history for a specific shift
        
        // Given - Mock returns logs for table in range
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 1, 23, 59);
        List<TableStatusLog> logs = Arrays.asList(testLog);
        when(tableStatusLogRepository.findTableLogsBetween(1L, start, end)).thenReturn(logs);

        // When - Get table 1's logs for Jan 1, 2025
        List<TableStatusLog> result = tableStatusLogService.getTableLogsBetween(1L, start, end);

        // Then - Should get that table's logs in range
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getTableId());
        verify(tableStatusLogRepository, times(1)).findTableLogsBetween(1L, start, end);
    }

    @Test
    void testDeleteLog_ShouldCallRepository() {
        // WHAT: Test deleting a log entry
        // WHY: Remove test data (not recommended for production - audit trail!)
        
        // Given - Mock repository
        doNothing().when(tableStatusLogRepository).deleteById(1L);

        // When - Delete log
        tableStatusLogService.deleteLog(1L);

        // Then - Repository delete should be called
        verify(tableStatusLogRepository, times(1)).deleteById(1L);
    }
}