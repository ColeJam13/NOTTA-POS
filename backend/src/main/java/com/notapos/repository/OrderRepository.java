package com.notapos.repository;

import com.notapos.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Order entity.
 * 
 * @author CJ
 */

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByTableId(Long tableId);            // Find all orders for specific table

    List<Order> findByStatus(String status);            // Find all orders with specific status

    List<Order> findByTableIdAndStatus(Long tableId, String status);                        // Find orders by status and table

    List<Order> findByTableIdInAndStatus(Collection<Long> tableIds, String status);         // Find orders with a status for MANY tables in one query (avoids one query per table)

    List<Order> findTop50ByTableIdInAndStatusOrderByClosedAtDesc(Collection<Long> tableIds, String status);    // The 50 most recently closed orders for those tables, newest first


    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :start AND :end")               // Find orders created within a time range
    List<Order> findOrdersBetween(LocalDateTime start, LocalDateTime end);

    List<Order> findByStatusAndClosedAtBetween(String status, LocalDateTime start, LocalDateTime end);     // Find orders with a status that were closed within a time range (start and end included)

    // ------------------------------------------------------------------
    // The search box
    // ------------------------------------------------------------------
    //
    // A check is found by its table's number, by the name of the person who opened it,
    // or by its own number. Orders and tables are not linked by a JPA relationship in
    // this codebase, so the query names both and matches them up itself (t.tableId =
    // o.tableId): one query, not one per order.
    //
    // "pattern" and "orderNumber" are PARAMETERS. The database receives them as values,
    // separately from the query's text, so nothing a person types can change what the
    // query does. (Building the query by gluing the typed text into it is how "SQL
    // injection" happens. Never do that.)

    /**
     * Open checks matching what was typed, newest first.
     *
     * @param status      OrderStatus.OPEN's value
     * @param pattern     See TableRepository.searchRegularTables
     * @param orderNumber The typed text as a number, or null when it is not one
     * @param page        How many to return at most
     */
    @Query("""
            SELECT o FROM Order o, RestaurantTable t
            WHERE t.tableId = o.tableId
              AND o.status = :status
              AND (LOWER(t.tableNumber) LIKE :pattern ESCAPE '\\'
                   OR LOWER(o.serverName) LIKE :pattern ESCAPE '\\'
                   OR o.orderId = :orderNumber)
            ORDER BY o.createdAt DESC, o.orderId DESC
            """)
    List<Order> searchWithStatus(String status, String pattern, Long orderNumber, Pageable page);

    /**
     * Closed checks matching what was typed and closed at or after a moment, most
     * recently closed first.
     */
    @Query("""
            SELECT o FROM Order o, RestaurantTable t
            WHERE t.tableId = o.tableId
              AND o.status = :status
              AND o.closedAt >= :since
              AND (LOWER(t.tableNumber) LIKE :pattern ESCAPE '\\'
                   OR LOWER(o.serverName) LIKE :pattern ESCAPE '\\'
                   OR o.orderId = :orderNumber)
            ORDER BY o.closedAt DESC, o.orderId DESC
            """)
    List<Order> searchWithStatusClosedSince(String status, LocalDateTime since, String pattern, Long orderNumber, Pageable page);

    // ------------------------------------------------------------------
    // Row locks: how two terminals are kept from acting on one check at the same instant
    // ------------------------------------------------------------------
    //
    // A plain read ("is this order open?") tells you what was true when you read it.
    // Another terminal can close the order a millisecond later, before you act on the answer.
    // A row lock makes the answer stay true until your transaction ends.
    //
    // There are two kinds, and they work like a meeting room:
    //   - "for share": many requests can hold it at once (several items being rung in
    //     together). While anyone holds it, nobody can take the "for update" lock.
    //   - "for update": one holder at a time, and only when nobody holds "for share".
    //     Taken by a request that is about to close or remove the order.
    // A request that cannot get its lock yet simply waits (a few milliseconds) for the
    // other transaction to finish, and then reads the row as it is NOW.
    //
    // Both must be called inside a transaction that is allowed to write (@Transactional,
    // not the read-only default): the lock lasts until that transaction commits.

    /**
     * Read an order and keep it from being closed or removed until this transaction ends.
     * Used by everything that adds to, sends, or changes the order's items.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)                                                    // SQL: SELECT ... FOR SHARE
    @Query("SELECT o FROM Order o WHERE o.orderId = :orderId")
    Optional<Order> findByIdLockedForShare(Long orderId);

    /**
     * Read an order and hold it alone until this transaction ends: no other request can
     * add to it, send it, pay it, or remove it in the meantime.
     * Used by paying a check and by clearing an empty order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)                                                   // SQL: SELECT ... FOR UPDATE
    @Query("SELECT o FROM Order o WHERE o.orderId = :orderId")
    Optional<Order> findByIdLockedForUpdate(Long orderId);
}
