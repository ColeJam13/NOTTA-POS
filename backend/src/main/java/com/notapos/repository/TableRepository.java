package com.notapos.repository;

import com.notapos.entity.RestaurantTable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Table entity.
 * 
 * Provides database access methods for managing restaurant tables.
 * Spring Data JPA auto-generates implementation at runtime.
 * 
 * @author CJ
 */

@Repository                                                                                 // Tells spring this is a repository bean
public interface TableRepository extends JpaRepository<RestaurantTable, Long> {

    Optional<RestaurantTable> findByTableNumber(String tableNumber);            // Find table by table Number

    List<RestaurantTable> findBySection(String section);                        // Find table by section

    List<RestaurantTable> findByStatus(String status);                          // Find table by section

    List<RestaurantTable> findBySectionAndStatus(String section, String status);        // Find table by section and status

    List<RestaurantTable> findByIsQuickOrder(Boolean isQuickOrder);             // Find tables by Quick Order flag (true = Quick Orders, false = regular tables)

    @Query("SELECT t FROM RestaurantTable t WHERE t.isQuickOrder = false OR t.isQuickOrder IS NULL")     // Find regular tables. NULL counts as regular: rows inserted by older seed files never set the flag
    List<RestaurantTable> findRegularTables();

    /**
     * Read a table and hold it alone until this transaction ends.
     *
     * Opening an order uses this so that two terminals opening the same table at the
     * same instant go one after the other: the second waits for the first to finish,
     * and then finds the order the first one opened. (See OrderRepository for how
     * row locks work.) Must be called inside a @Transactional method that writes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)                                               // SQL: SELECT ... FOR UPDATE
    @Query("SELECT t FROM RestaurantTable t WHERE t.tableId = :tableId")
    Optional<RestaurantTable> findByIdLockedForUpdate(Long tableId);
}