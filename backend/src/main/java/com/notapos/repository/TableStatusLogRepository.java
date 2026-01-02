package com.notapos.repository;

import com.notapos.entity.TableStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for TableStatusLog entity.
 * 
 * @author CJ
 */

@Repository
public interface TableStatusLogRepository extends JpaRepository<TableStatusLog, Long> {

    List<TableStatusLog> findByTableId(Long tableId);                                                                       // Find all logs for a specific table

    List<TableStatusLog> findByChangedBy(String changedBy);                                                                 // Find log entries by who made changes

    @Query("SELECT tsl FROM TableStatusLog tsl WHERE tsl.createdAt BETWEEN :start AND :end")                                // Find log entries within a time range
    List<TableStatusLog> findLogsBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT tsl FROM TableStatusLog tsl WHERE tsl.tableId = :tableId AND tsl.createdAt BETWEEN :start AND :end")     // Find log entries for a specific table within a time range
    List<TableStatusLog> findTableLogsBetween(Long tableId, LocalDateTime start, LocalDateTime end);
}