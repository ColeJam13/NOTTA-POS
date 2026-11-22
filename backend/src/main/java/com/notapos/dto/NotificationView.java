package com.notapos.dto;

import com.notapos.entity.Notification;

import java.time.Instant;

/**
 * One notification, as the bell's panel shows it.
 *
 * Who it was for is not in here: the answer is always "you", because a person
 * is only ever sent their own.
 *
 * @param notificationId Notification ID (what dismissing it names)
 * @param kind           "item_86", "item_back", "order_up", or "message" (NotificationKind)
 * @param text           The words shown, e.g. "Garden Toast is 86'd"
 * @param fromName       Who caused it: the writer of a message, the person who 86'd the
 *                       item. Null when nobody is on record.
 * @param createdAt      When it happened, as a moment (the screen shows "5m ago")
 */
public record NotificationView(
        Long notificationId,
        String kind,
        String text,
        String fromName,
        Instant createdAt) {

    public static NotificationView of(Notification notification) {
        return new NotificationView(
                notification.getNotificationId(),
                notification.getKind(),
                notification.getBody(),
                notification.getCreatedByName(),
                notification.getCreatedAt());
    }
}
