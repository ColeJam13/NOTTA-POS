package com.notapos.repository;

import com.notapos.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for notifications, and for who has dismissed them.
 *
 * WHOSE IS A NOTIFICATION? The three conditions below are written out in every
 * query here, so that listing, counting, and dismissing can never disagree
 * about it. A notification is one person's when:
 *
 *   1. it is recent enough       created_at >= :since          (the last 24 hours)
 *   2. it is for them            recipient_staff_id IS NULL    (for everyone)
 *                                OR recipient_staff_id = them
 *   3. they did not cause it     created_by_staff_id IS NULL
 *                                OR created_by_staff_id <> them
 *
 * and it is still ON THEIR LIST when, as well, they have not dismissed it: there
 * is no row for them in notification_dismissals.
 *
 * These are PostgreSQL's own SQL (nativeQuery = true), not JPQL: the dismissals
 * table has no entity (it is two IDs and a time, only ever written and checked
 * here), and "INSERT ... ON CONFLICT DO NOTHING" does not exist in JPQL.
 *
 * @author CJ
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** One person's list: theirs, not dismissed, newest first, at most :limit. */
    @Query(value = """
            SELECT n.* FROM notifications n
            WHERE n.created_at >= :since
              AND (n.recipient_staff_id IS NULL OR n.recipient_staff_id = :staffId)
              AND (n.created_by_staff_id IS NULL OR n.created_by_staff_id <> :staffId)
              AND NOT EXISTS (SELECT 1 FROM notification_dismissals d
                              WHERE d.notification_id = n.notification_id AND d.staff_id = :staffId)
            ORDER BY n.created_at DESC, n.notification_id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Notification> findOnTheListOf(@Param("staffId") Long staffId, @Param("since") Instant since, @Param("limit") int limit);

    /** Is this notification that person's at all (dismissed or not)? 1 or 0. */
    @Query(value = """
            SELECT COUNT(*) FROM notifications n
            WHERE n.notification_id = :notificationId
              AND n.created_at >= :since
              AND (n.recipient_staff_id IS NULL OR n.recipient_staff_id = :staffId)
              AND (n.created_by_staff_id IS NULL OR n.created_by_staff_id <> :staffId)
            """, nativeQuery = true)
    long countBelongingTo(@Param("notificationId") Long notificationId, @Param("staffId") Long staffId, @Param("since") Instant since);

    /**
     * Record that a person dismissed a notification.
     *
     * "ON CONFLICT DO NOTHING": if the row is already there (they dismissed it on
     * another terminal a moment ago, or tapped twice), the database leaves it as it
     * is and does not complain. Dismissing twice is the same as dismissing once.
     */
    @Modifying
    @Query(value = """
            INSERT INTO notification_dismissals (notification_id, staff_id, dismissed_at)
            VALUES (:notificationId, :staffId, :at)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void dismiss(@Param("notificationId") Long notificationId, @Param("staffId") Long staffId, @Param("at") Instant at);

    /**
     * Dismiss everything that is on one person's list, in one statement: the
     * database picks the notifications (the same three conditions) and writes a
     * dismissal for each. Nothing is read into the app and written back, so a
     * notification that arrives in the same instant is simply not included.
     *
     * @return How many were dismissed
     */
    @Modifying
    @Query(value = """
            INSERT INTO notification_dismissals (notification_id, staff_id, dismissed_at)
            SELECT n.notification_id, :staffId, :at FROM notifications n
            WHERE n.created_at >= :since
              AND (n.recipient_staff_id IS NULL OR n.recipient_staff_id = :staffId)
              AND (n.created_by_staff_id IS NULL OR n.created_by_staff_id <> :staffId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int dismissAllOf(@Param("staffId") Long staffId, @Param("since") Instant since, @Param("at") Instant at);
}
