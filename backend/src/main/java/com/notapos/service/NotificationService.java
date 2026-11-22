package com.notapos.service;

import com.notapos.config.RestaurantTime;
import com.notapos.dto.NotificationView;
import com.notapos.entity.MenuItem;
import com.notapos.entity.Notification;
import com.notapos.entity.Order;
import com.notapos.entity.RestaurantTable;
import com.notapos.enums.NotificationKind;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.repository.NotificationRepository;
import com.notapos.repository.TableRepository;
import com.notapos.security.StaffSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Notifications: the bell in the top bar.
 *
 * THREE KINDS (the owner's decision D5):
 *   - an item was 86'd, or is back on    -> everyone except the person who changed it
 *   - order up (a check has nothing sent  -> the person who opened that check, unless
 *     left to make)                          they completed it themselves
 *   - a message, written by the kitchen,  -> everyone except the writer
 *     a bartender, or a manager
 *
 * A NOTIFICATION IS WRITTEN IN THE SAME TRANSACTION AS WHAT CAUSED IT. The three
 * methods that the app itself calls (itemEightySixChanged, orderUp) refuse to run
 * outside a transaction (Propagation.MANDATORY), so the 86 and its notice are
 * saved together or not at all: there is never an 86 without its notice, or a
 * notice for an 86 that was rolled back.
 *
 * WHOSE IS A NOTIFICATION, and for how long, is NotificationRepository's three
 * conditions. A notification stays on a person's list until THEY dismiss it,
 * and is no longer listed after 24 hours either way. Dismissing is kept in the
 * database, per person, because a server moves between terminals.
 *
 * A PERSON IS ONLY EVER SENT, AND CAN ONLY DISMISS, THEIR OWN. Every method
 * here takes the signed-in person and asks the database for that person's rows;
 * there is no way to ask for someone else's.
 *
 * @author CJ
 */
@Service
@Transactional(readOnly = true)      // Every method runs in a database transaction; read-only unless a method says otherwise
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    // How long a notification is listed. After this it is old news whether or not anyone dismissed it.
    public static final Duration LISTED_FOR = Duration.ofHours(24);

    // The most a person's list holds. A busy day's 86 board can make a long list; the newest matter.
    public static final int LIST_LIMIT = 50;

    // The longest message a person may write
    public static final int MESSAGE_MAX_LENGTH = 200;

    private final NotificationRepository notificationRepository;
    private final TableRepository tableRepository;
    private final Clock clock;
    private final RestaurantTime restaurantTime;            // Turns a stored moment into the local time a screen is sent

    @Autowired
    public NotificationService(NotificationRepository notificationRepository,
                               TableRepository tableRepository,
                               Clock clock,
                               RestaurantTime restaurantTime) {
        this.notificationRepository = notificationRepository;
        this.tableRepository = tableRepository;
        this.clock = clock;
        this.restaurantTime = restaurantTime;
    }

    // ------------------------------------------------------------------
    // Reading and dismissing: always the signed-in person's own
    // ------------------------------------------------------------------

    /** The signed-in person's notifications that they have not dismissed, newest first. */
    public List<NotificationView> getMine(StaffSession viewer) {
        return notificationRepository.findOnTheListOf(viewer.staffId(), listedSince(), LIST_LIMIT).stream()
                .map(notification -> NotificationView.of(notification, restaurantTime))
                .toList();
    }

    /**
     * Dismiss one notification, for the signed-in person only.
     *
     * Dismissing one that is already dismissed is fine (the same answer as the
     * first time). One that is not theirs, or does not exist, is "not found":
     * the answer does not say which, so it tells nobody what other people have.
     *
     * @throws ResourceNotFoundException if it is not one of this person's (404)
     */
    @Transactional
    public void dismiss(Long notificationId, StaffSession viewer) {
        if (notificationRepository.countBelongingTo(notificationId, viewer.staffId(), listedSince()) == 0) {
            throw new ResourceNotFoundException("Notification not found with id: " + notificationId);
        }
        notificationRepository.dismiss(notificationId, viewer.staffId(), clock.instant());
    }

    /** Dismiss everything on the signed-in person's list. Nobody else's list changes. */
    @Transactional
    public void dismissAll(StaffSession viewer) {
        notificationRepository.dismissAllOf(viewer.staffId(), listedSince(), clock.instant());
    }

    // ------------------------------------------------------------------
    // A message, written by a person
    // ------------------------------------------------------------------

    /**
     * Send a message to everyone else.
     *
     * Who may is decided at the controller (Permissions.SEND_MESSAGES); how long
     * it may be, on the request (MessageRequest). The writer is the signed-in
     * person. They are not shown their own message.
     */
    @Transactional
    public NotificationView sendMessage(String text, StaffSession writer) {
        Notification saved = notificationRepository.save(
                notification(NotificationKind.MESSAGE, text.strip(), null, writer));
        log.info("{} (staff {}) sent a message to the floor", writer.name(), writer.staffId());
        return NotificationView.of(saved, restaurantTime);
    }

    // ------------------------------------------------------------------
    // Sent by the app itself, inside the transaction of what caused them
    // ------------------------------------------------------------------

    /**
     * An item was just 86'd, or put back on. Tells everyone except whoever did it.
     *
     * Called by MenuItemService only when the item's state really changed (86'ing
     * something that is already out says nothing new).
     *
     * @param item The menu item, as it now is
     * @param by   The signed-in person who changed it
     */
    @Transactional(propagation = Propagation.MANDATORY)        // Only inside the transaction that changes the item
    public void itemEightySixChanged(MenuItem item, StaffSession by) {
        boolean out = Boolean.TRUE.equals(item.getIs86d());
        notificationRepository.save(notification(
                out ? NotificationKind.ITEM_86 : NotificationKind.ITEM_BACK,
                item.getName() + (out ? " is 86'd" : " is back on"),
                null,                                           // For everyone
                by));
    }

    /**
     * Everything sent on a check has been made. Tells the person who opened it.
     *
     * Nobody is told when nobody is on record as having opened the check (one from
     * before staff profiles), or when the person who completed the last item IS
     * the person who opened it: they are holding the plate.
     *
     * @param order The check whose last unfinished item was just completed
     * @param by    The signed-in person who completed it
     */
    @Transactional(propagation = Propagation.MANDATORY)        // Only inside the transaction that completes the item
    public void orderUp(Order order, StaffSession by) {
        Long openedBy = order.getStaffId();
        if (openedBy == null || openedBy.equals(by.staffId())) {
            return;
        }
        notificationRepository.save(notification(
                NotificationKind.ORDER_UP,
                nameOf(order) + " is up",
                openedBy,
                by));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Notification notification(NotificationKind kind, String body, Long recipientStaffId, StaffSession by) {
        Notification notification = new Notification();
        notification.setKind(kind.getValue());
        notification.setBody(body);
        notification.setRecipientStaffId(recipientStaffId);
        notification.setCreatedByStaffId(by.staffId());
        notification.setCreatedByName(by.name());
        notification.setCreatedAt(clock.instant());
        return notification;
    }

    // The first moment a notification can have been created and still be listed
    private Instant listedSince() {
        return clock.instant().minus(LISTED_FOR);
    }

    // "Table W3" or "Quick Order QO1", the way the screens name a check's table
    private String nameOf(Order order) {
        RestaurantTable table = tableRepository.findById(order.getTableId()).orElse(null);
        if (table == null) {
            return "Check #" + order.getOrderId();
        }
        return (Boolean.TRUE.equals(table.getIsQuickOrder()) ? "Quick Order " : "Table ") + table.getTableNumber();
    }
}
