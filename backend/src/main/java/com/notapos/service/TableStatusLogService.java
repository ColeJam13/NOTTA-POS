package com.notapos.service;

import org.springframework.transaction.annotation.Transactional;
import com.notapos.entity.TableStatusLog;
import com.notapos.repository.TableStatusLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for TableStatusLog operations.
 * 
 * Tracks all table status changes for audit trail.
 * 
 * @author CJ
 */

@Service
@Transactional(readOnly = true)      // Every method runs in a database transaction; read-only unless a method says otherwise
public class TableStatusLogService {
    
    private final TableStatusLogRepository tableStatusLogRepository;

    @Autowired
    public TableStatusLogService(TableStatusLogRepository tableStatusLogRepository) {
        this.tableStatusLogRepository = tableStatusLogRepository;
    }

    public List<TableStatusLog> getAllLogs() {                                          // Get all Logs
        return tableStatusLogRepository.findAll();
    }

    public Optional<TableStatusLog> getLogById(Long id) {                               // Get log by ID
        return tableStatusLogRepository.findById(id);
    }

    public List<TableStatusLog> getLogsByTable(Long tableId) {                          // Get logs by table
        return tableStatusLogRepository.findByTableId(tableId);
    }

    public List<TableStatusLog> getLogsByChangedBy(String changedBy) {                  // get all logs by changed by
        return tableStatusLogRepository.findByChangedBy(changedBy);
    }

    public List<TableStatusLog> getLogsBetween(LocalDateTime start, LocalDateTime end) {                        // get logs in a certain time frame
        return tableStatusLogRepository.findLogsBetween(start, end);
    }

    public List<TableStatusLog> getTableLogsBetween(Long tableId, LocalDateTime start, LocalDateTime end) {     // get logs in a certain time frame for a certain table
        return tableStatusLogRepository.findTableLogsBetween(tableId, start, end);
    }

    @Transactional
    public TableStatusLog createLog(TableStatusLog log) {                           // create new log
        return tableStatusLogRepository.save(log);
    }

    @Transactional
    public void deleteLog(Long id) {                                                // delete log
        tableStatusLogRepository.deleteById(id);
    }
}
