package com.notapos.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One thing that happened and is worth telling people: an item was 86'd, an
 * order is up, someone wrote a message. A row in the notifications table
 * (migration V8, which explains each column).
 *
 * A notification is written once and never changed. Who has DISMISSED it is
 * kept separately, per person, in notification_dismissals (see
 * NotificationRepository): this row is shared by everyone it is for.
 *
 * createdAt is an Instant ("a moment", the same in every time zone), stored in
 * a "timestamptz" column, as every table added since V4 does.
 *
 * @author CJ
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    // A NotificationKind's value: "item_86", "item_back", "order_up", "message"
    @Column(name = "kind", nullable = false, length = 20, updatable = false)
    private String kind;

    // The words shown
    @Column(name = "body", nullable = false, length = 300, updatable = false)
    private String body;

    // Who it is for. Null means everyone.
    @Column(name = "recipient_staff_id", updatable = false)
    private Long recipientStaffId;

    // Who caused it. They are not shown it.
    @Column(name = "created_by_staff_id", updatable = false)
    private Long createdByStaffId;

    // A copy of their name, so the row still reads correctly after they have left
    @Column(name = "created_by_name", length = 50, updatable = false)
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Notification() {}

    public Long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(Long notificationId) {
        this.notificationId = notificationId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Long getRecipientStaffId() {
        return recipientStaffId;
    }

    public void setRecipientStaffId(Long recipientStaffId) {
        this.recipientStaffId = recipientStaffId;
    }

    public Long getCreatedByStaffId() {
        return createdByStaffId;
    }

    public void setCreatedByStaffId(Long createdByStaffId) {
        this.createdByStaffId = createdByStaffId;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
