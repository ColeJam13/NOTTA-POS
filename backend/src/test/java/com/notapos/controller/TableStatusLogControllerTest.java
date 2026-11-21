package com.notapos.controller;

import com.notapos.entity.TableStatusLog;
import com.notapos.service.TableStatusLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.notapos.testsupport.ControllerTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for TableStatusLogController.
 * 
 * Tests REST API endpoints for table status change audit trail.
 * Tracks history of table status changes for accountability and analytics.
 * Uses MockMvc to simulate HTTP requests without starting full server.
 * 
 * @author CJ
 */

@ControllerTest(TableStatusLogController.class)
class TableStatusLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TableStatusLogService tableStatusLogService;

    private TableStatusLog testLog;

    @BeforeEach
    void setUp() {
        testLog = new TableStatusLog();
        testLog.setLogId(1L);
        testLog.setTableId(1L);
        testLog.setOldStatus("available");
        testLog.setNewStatus("occupied");
        testLog.setChangedBy("Server Alice");
    }

    @Test
    void testGetAllLogs_ShouldReturnList() throws Exception {
        // WHAT: Test GET /api/table-status-logs
        // WHY: Retrieve all status change logs
        
        List<TableStatusLog> logs = Arrays.asList(testLog);
        when(tableStatusLogService.getAllLogs()).thenReturn(logs);

        mockMvc.perform(get("/api/table-status-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].oldStatus").value("available"))
                .andExpect(jsonPath("$[0].newStatus").value("occupied"));

        verify(tableStatusLogService).getAllLogs();
    }

    @Test
    void testGetLogById_WhenExists_ShouldReturnLog() throws Exception {
        // WHAT: Test GET /api/table-status-logs/{id}
        // WHY: Retrieve specific log entry
        
        when(tableStatusLogService.getLogById(1L)).thenReturn(Optional.of(testLog));

        mockMvc.perform(get("/api/table-status-logs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logId").value(1))
                .andExpect(jsonPath("$.changedBy").value("Server Alice"));

        verify(tableStatusLogService).getLogById(1L);
    }

    @Test
    void testGetLogById_WhenNotExists_ShouldReturn404() throws Exception {
        // WHAT: Test GET /api/table-status-logs/{id} when not found
        // WHY: Handle missing logs
        
        when(tableStatusLogService.getLogById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/table-status-logs/999"))
                .andExpect(status().isNotFound());

        verify(tableStatusLogService).getLogById(999L);
    }

    @Test
    void testGetLogsByTable_ShouldReturnTableLogs() throws Exception {
        // WHAT: Test GET /api/table-status-logs/table/{tableId}
        // WHY: Get status change history for specific table
        
        List<TableStatusLog> logs = Arrays.asList(testLog);
        when(tableStatusLogService.getLogsByTable(1L)).thenReturn(logs);

        mockMvc.perform(get("/api/table-status-logs/table/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tableId").value(1));

        verify(tableStatusLogService).getLogsByTable(1L);
    }

    @Test
    void testCreateLog_ShouldReturnCreated() throws Exception {
        // WHAT: Test POST /api/table-status-logs
        // WHY: Record new status change
        
        when(tableStatusLogService.createLog(any(TableStatusLog.class))).thenReturn(testLog);

        mockMvc.perform(post("/api/table-status-logs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":1,\"oldStatus\":\"available\",\"newStatus\":\"occupied\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.logId").value(1));

        verify(tableStatusLogService).createLog(any(TableStatusLog.class));
    }

    @Test
    void testDeleteLog_ShouldReturn204() throws Exception {
        // WHAT: Test DELETE /api/table-status-logs/{id}
        // WHY: Remove log entries (rare in production)
        
        doNothing().when(tableStatusLogService).deleteLog(1L);

        mockMvc.perform(delete("/api/table-status-logs/1"))
                .andExpect(status().isNoContent());

        verify(tableStatusLogService).deleteLog(1L);
    }
}