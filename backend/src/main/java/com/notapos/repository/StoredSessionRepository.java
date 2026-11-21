package com.notapos.repository;

import com.notapos.entity.StoredSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for StoredSession: the staff_sessions table.
 *
 * Only DatabaseStaffSessionStore uses this. Everything else asks the store.
 *
 * THE @Modifying QUERIES: each changes or removes rows with ONE statement sent
 * straight to the database ("delete every session of staff 7"), instead of
 * loading each row and saving or deleting it one at a time. Two settings go
 * with that:
 *   flushAutomatically - send any changes Hibernate is still holding first, so
 *                        the statement sees them
 *   clearAutomatically - afterwards, make Hibernate forget the rows it had
 *                        loaded, because the statement changed them behind its back
 *
 * @author CJ
 */
@Repository
public interface StoredSessionRepository extends JpaRepository<StoredSession, Long> {

    Optional<StoredSession> findByTokenHash(String tokenHash);                  // The session a token belongs to (uses the unique index)

    @Modifying(flushAutomatically = true, clearAutomatically = true)            // Remember that a session was just used
    @Query("UPDATE StoredSession s SET s.lastUsedAt = :now WHERE s.tokenHash = :tokenHash")
    int touch(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)            // Sign out: end one session
    @Query("DELETE FROM StoredSession s WHERE s.tokenHash = :tokenHash")
    int deleteByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)            // Sign a person out everywhere
    @Query("DELETE FROM StoredSession s WHERE s.staffId = :staffId")
    int deleteByStaffId(@Param("staffId") Long staffId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)            // Sign a person out everywhere except on one device
    @Query("DELETE FROM StoredSession s WHERE s.staffId = :staffId AND s.tokenHash <> :keepTokenHash")
    int deleteOthersByStaffId(@Param("staffId") Long staffId, @Param("keepTokenHash") String keepTokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)            // They chose a new PIN: nothing left to change
    @Query("UPDATE StoredSession s SET s.mustChangePin = false WHERE s.staffId = :staffId")
    int clearMustChangePin(@Param("staffId") Long staffId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)            // A manager corrected someone's name
    @Query("UPDATE StoredSession s SET s.name = :name WHERE s.staffId = :staffId")
    int rename(@Param("staffId") Long staffId, @Param("name") String name);

    @Modifying(flushAutomatically = true, clearAutomatically = true)            // The sweep: drop sessions nobody has used since the cutoff
    @Query("DELETE FROM StoredSession s WHERE s.lastUsedAt < :cutoff")
    int deleteLastUsedBefore(@Param("cutoff") Instant cutoff);
}
