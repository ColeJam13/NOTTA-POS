package com.notapos.dto;

import com.notapos.entity.Notification;

import com.notapos.config.RestaurantTime;
import java.time.LocalDateTime;

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
 * @param createdAt      When it happened, on the restaurant's own clock, with no zone: the way
 *                       every time is written in JSON, so the screen can compare it with
 *                       the backend's time and show "5m ago" (see RestaurantTime)
 */
public record NotificationView(
        Long notificationId,
        String kind,
        String text,
        String fromName,
        LocalDateTime createdAt) {

    // The notification is stored with a moment (timestamptz); the clock turns it into the
    // local date and time a screen is sent
    public static NotificationView of(Notification notification, RestaurantTime clock) {
        return new NotificationView(
                notification.getNotificationId(),
                notification.getKind(),
                notification.getBody(),
                notification.getCreatedByName(),
                clock.local(notification.getCreatedAt()));
    }
}
