package com.notapos.repository;

import com.notapos.entity.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for NotificationRepository.
 *
 * Tests, against a real PostgreSQL 16, the SQL that decides whose a
 * notification is: for everyone or for one person, never for the person who
 * caused it, only for 24 hours, and off a person's list once THEY dismiss it.
 *
 * @author CJ
 */
class NotificationRepositoryTest extends BaseRepositoryTest {

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    // The people, by staff ID
    private static final long KIT = 9L;         // The kitchen
    private static final long SARAH = 7L;       // A server
    private static final long DAN = 8L;         // A bartender

    private static final Instant NOW = Instant.parse("2026-11-20T18:00:00Z");
    private static final Instant A_DAY_AGO = NOW.minus(Duration.ofHours(24));

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM notification_dismissals");
        notificationRepository.deleteAll();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Notification notice(String kind, String body, Long recipient, Long createdBy, Instant at) {
        Notification notification = new Notification();
        notification.setKind(kind);
        notification.setBody(body);
        notification.setRecipientStaffId(recipient);
        notification.setCreatedByStaffId(createdBy);
        notification.setCreatedByName(createdBy == null ? null : "Someone");
        notification.setCreatedAt(at);
        return notificationRepository.saveAndFlush(notification);
    }

    private List<String> listOf(long staffId) {
        return notificationRepository.findOnTheListOf(staffId, A_DAY_AGO, 50).stream().map(Notification::getBody).toList();
    }

    // ------------------------------------------------------------------
    // Whose is it
    // ------------------------------------------------------------------

    @Test
    void testFindOnTheListOf_ANoticeForEveryone_ShouldReachEveryoneButWhoeverCausedIt() {
        // WHAT: Kit 86's the Garden Toast
        notice("item_86", "Garden Toast is 86'd", null, KIT, NOW);

        assertEquals(List.of("Garden Toast is 86'd"), listOf(SARAH));
        assertEquals(List.of("Garden Toast is 86'd"), listOf(DAN));
        assertEquals(List.of(), listOf(KIT));                           // Kit knows: Kit did it
    }

    @Test
    void testFindOnTheListOf_ANoticeForOnePerson_ShouldReachOnlyThem() {
        // WHAT: Order up on Sarah's table, completed by Kit
        notice("order_up", "Table W5 is up", SARAH, KIT, NOW);

        assertEquals(List.of("Table W5 is up"), listOf(SARAH));
        assertEquals(List.of(), listOf(DAN));
        assertEquals(List.of(), listOf(KIT));
    }

    @Test
    void testFindOnTheListOf_ANoticeNobodyIsOnRecordAsCausing_ShouldReachEveryone() {
        notice("message", "From nobody in particular", null, null, NOW);

        assertEquals(1, listOf(SARAH).size());
        assertEquals(1, listOf(KIT).size());
    }

    @Test
    void testFindOnTheListOf_ShouldListTheNewestFirst_AndNoMoreThanAsked() {
        notice("message", "first", null, KIT, NOW.minusSeconds(120));
        notice("message", "second", null, KIT, NOW.minusSeconds(60));
        notice("message", "third", null, KIT, NOW);

        assertEquals(List.of("third", "second", "first"), listOf(SARAH));
        assertEquals(List.of("third", "second"), notificationRepository.findOnTheListOf(SARAH, A_DAY_AGO, 2)
                .stream().map(Notification::getBody).toList());
    }

    @Test
    void testFindOnTheListOf_ShouldLeaveOutAnythingOlderThanTheWindow() {
        // WHAT: One notice a second inside the 24 hours, one exactly at the edge, one a second outside
        notice("message", "just inside", null, KIT, A_DAY_AGO.plusSeconds(1));
        notice("message", "at the edge", null, KIT, A_DAY_AGO);
        notice("message", "just outside", null, KIT, A_DAY_AGO.minusSeconds(1));

        assertEquals(List.of("just inside", "at the edge"), listOf(SARAH));
    }

    // ------------------------------------------------------------------
    // Dismissing
    // ------------------------------------------------------------------

    @Test
    void testDismiss_ShouldTakeItOffThatPersonsListAndNobodyElses() {
        Notification toast = notice("item_86", "Garden Toast is 86'd", null, KIT, NOW);

        notificationRepository.dismiss(toast.getNotificationId(), SARAH, NOW);

        assertEquals(List.of(), listOf(SARAH));
        assertEquals(List.of("Garden Toast is 86'd"), listOf(DAN));     // Dan has not dismissed it
    }

    @Test
    void testDismiss_Twice_ShouldBeTheSameAsOnce() {
        // WHY: A double tap, or the same notice dismissed on two terminals at once
        Notification toast = notice("item_86", "Garden Toast is 86'd", null, KIT, NOW);

        notificationRepository.dismiss(toast.getNotificationId(), SARAH, NOW);
        assertDoesNotThrow(() -> notificationRepository.dismiss(toast.getNotificationId(), SARAH, NOW.plusSeconds(5)));

        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification_dismissals", Integer.class));
    }

    @Test
    void testDismissAllOf_ShouldDismissEverythingOnThatPersonsList_AndOnlyThat() {
        // WHAT: Sarah has two notices for everyone and one of her own. Dan has the two.
        notice("item_86", "Garden Toast is 86'd", null, KIT, NOW);
        notice("message", "Fryer is down", null, KIT, NOW);
        notice("order_up", "Table W5 is up", SARAH, KIT, NOW);
        notice("order_up", "Table A1 is up", DAN, KIT, NOW);
        notice("message", "From Sarah herself", null, SARAH, NOW);              // Not on her list to begin with

        int dismissed = notificationRepository.dismissAllOf(SARAH, A_DAY_AGO, NOW);

        assertEquals(3, dismissed);
        assertEquals(List.of(), listOf(SARAH));
        assertEquals(4, listOf(DAN).size());                                    // Dan's list is untouched: the two for everyone, his own table, and Sarah's message
        assertTrue(listOf(DAN).contains("Table A1 is up"));
        assertTrue(listOf(DAN).contains("From Sarah herself"));
    }

    @Test
    void testDismissAllOf_ShouldNotCountWhatWasAlreadyDismissed() {
        Notification toast = notice("item_86", "Garden Toast is 86'd", null, KIT, NOW);
        notice("message", "Fryer is down", null, KIT, NOW);
        notificationRepository.dismiss(toast.getNotificationId(), SARAH, NOW);

        assertEquals(1, notificationRepository.dismissAllOf(SARAH, A_DAY_AGO, NOW));
        assertEquals(0, notificationRepository.dismissAllOf(SARAH, A_DAY_AGO, NOW));     // Nothing left to dismiss
    }

    // ------------------------------------------------------------------
    // Is it theirs at all
    // ------------------------------------------------------------------

    @Test
    void testCountBelongingTo_ShouldSayWhetherANoticeIsThatPersonsDismissedOrNot() {
        Notification forAll = notice("item_86", "Garden Toast is 86'd", null, KIT, NOW);
        Notification forSarah = notice("order_up", "Table W5 is up", SARAH, KIT, NOW);
        Notification old = notice("message", "Yesterday's news", null, KIT, A_DAY_AGO.minusSeconds(1));

        assertEquals(1, notificationRepository.countBelongingTo(forAll.getNotificationId(), SARAH, A_DAY_AGO));
        assertEquals(0, notificationRepository.countBelongingTo(forAll.getNotificationId(), KIT, A_DAY_AGO));       // Kit caused it
        assertEquals(1, notificationRepository.countBelongingTo(forSarah.getNotificationId(), SARAH, A_DAY_AGO));
        assertEquals(0, notificationRepository.countBelongingTo(forSarah.getNotificationId(), DAN, A_DAY_AGO));     // Someone else's
        assertEquals(0, notificationRepository.countBelongingTo(old.getNotificationId(), SARAH, A_DAY_AGO));       // Too old
        assertEquals(0, notificationRepository.countBelongingTo(999_999L, SARAH, A_DAY_AGO));                      // No such notice

        // Still hers after she dismisses it: dismissing again must not turn into "not found"
        notificationRepository.dismiss(forAll.getNotificationId(), SARAH, NOW);
        assertEquals(1, notificationRepository.countBelongingTo(forAll.getNotificationId(), SARAH, A_DAY_AGO));
    }
}
