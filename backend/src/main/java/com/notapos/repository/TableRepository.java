package com.notapos.repository;

import com.notapos.entity.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}