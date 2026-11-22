package com.notapos.service;

import com.notapos.dto.NotificationView;
import com.notapos.entity.MenuItem;
import com.notapos.entity.Notification;
import com.notapos.entity.Order;
import com.notapos.entity.RestaurantTable;
import com.notapos.enums.StaffRole;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.repository.NotificationRepository;
import com.notapos.repository.TableRepository;
import com.notapos.security.StaffSession;
import com.notapos.testsupport.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationService.
 *
 * Tests what each kind of notification says and who it is addressed to, that a
 * person is only ever asked about their own, and the 24-hour window, with the
 * repository replaced by a Mockito stand-in and the clock by one a test can move.
 *
 * WHICH ROWS are "one person's" is the repository's SQL, tested against a real
 * database in NotificationRepositoryTest. That the whole thing reaches the right
 * people through real requests is NotificationsIntegrationTest.
 *
 * @author CJ
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private TableRepository tableRepository;

    private final MutableClock clock = new MutableClock();
    private NotificationService notificationService;

    private static final StaffSession KIT = new StaffSession(9L, "Kit", StaffRole.KITCHEN, Instant.now(), false);
    private static final StaffSession SARAH = new StaffSession(7L, "Sarah", StaffRole.SERVER, Instant.now(), false);

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, tableRepository, clock);
    }

    // The notification the service handed to the repository to be saved
    private Notification saved() {
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        return saved.getValue();
    }

    private MenuItem gardenToast(boolean is86d) {
        MenuItem item = new MenuItem();
        item.setMenuItemId(3L);
        item.setName("Garden Toast");
        item.setIs86d(is86d);
        return item;
    }

    private Order checkOpenedBy(Long staffId) {
        Order order = new Order();
        order.setOrderId(31L);
        order.setTableId(5L);
        order.setStaffId(staffId);
        return order;
    }

    private void tableFiveIs(String number, boolean quickOrder) {
        RestaurantTable table = new RestaurantTable();
        table.setTableId(5L);
        table.setTableNumber(number);
        table.setIsQuickOrder(quickOrder);
        when(tableRepository.findById(5L)).thenReturn(Optional.of(table));
    }

    // ------------------------------------------------------------------
    // 86
    // ------------------------------------------------------------------

    @Test
    void testItemEightySixChanged_WhenNowOut_ShouldTellEveryoneItIs86d() {
        // WHY: Addressed to nobody in particular (everyone), from the person who did it,
        //      who is the one person it will not be shown to
        notificationService.itemEightySixChanged(gardenToast(true), KIT);

        Notification notice = saved();
        assertEquals("item_86", notice.getKind());
        assertEquals("Garden Toast is 86'd", notice.getBody());
        assertNull(notice.getRecipientStaffId());
        assertEquals(9L, notice.getCreatedByStaffId());
        assertEquals("Kit", notice.getCreatedByName());
        assertEquals(clock.instant(), notice.getCreatedAt());
    }

    @Test
    void testItemEightySixChanged_WhenBackOn_ShouldSaySo() {
        notificationService.itemEightySixChanged(gardenToast(false), KIT);

        Notification notice = saved();
        assertEquals("item_back", notice.getKind());
        assertEquals("Garden Toast is back on", notice.getBody());
        assertNull(notice.getRecipientStaffId());
    }

    // ------------------------------------------------------------------
    // Order up
    // ------------------------------------------------------------------

    @Test
    void testOrderUp_ShouldTellThePersonWhoOpenedTheCheck_ByItsTable() {
        tableFiveIs("W5", false);

        notificationService.orderUp(checkOpenedBy(7L), KIT);

        Notification notice = saved();
        assertEquals("order_up", notice.getKind());
        assertEquals("Table W5 is up", notice.getBody());
        assertEquals(7L, notice.getRecipientStaffId());              // Sarah, and nobody else
        assertEquals(9L, notice.getCreatedByStaffId());
    }

    @Test
    void testOrderUp_ForAQuickOrder_ShouldNameItAsOne() {
        tableFiveIs("QO12", true);

        notificationService.orderUp(checkOpenedBy(7L), KIT);

        assertEquals("Quick Order QO12 is up", saved().getBody());
    }

    @Test
    void testOrderUp_WhenThePersonWhoMadeItOpenedIt_ShouldTellNobody() {
        // WHAT: A bartender completes the drinks on their own check
        // WHY: They are holding the glass
        notificationService.orderUp(checkOpenedBy(9L), KIT);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void testOrderUp_WhenNobodyIsOnRecordAsHavingOpenedIt_ShouldTellNobody() {
        // WHY: A check from before staff profiles has no opener to tell
        notificationService.orderUp(checkOpenedBy(null), KIT);

        verify(notificationRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // A message
    // ------------------------------------------------------------------

    @Test
    void testSendMessage_ShouldSaveItFromTheWriterForEveryone_AndHandItBack() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(call -> {
            Notification notice = call.getArgument(0);
            notice.setNotificationId(44L);
            return notice;
        });

        NotificationView sent = notificationService.sendMessage("  Fryer is down for 10 minutes  ", KIT);

        Notification notice = saved();
        assertEquals("message", notice.getKind());
        assertEquals("Fryer is down for 10 minutes", notice.getBody());      // Without the spaces around it
        assertNull(notice.getRecipientStaffId());
        assertEquals(9L, notice.getCreatedByStaffId());
        assertEquals(44L, sent.notificationId());
        assertEquals("Kit", sent.fromName());
        assertEquals("message", sent.kind());
    }

    // ------------------------------------------------------------------
    // Reading and dismissing: only ever the signed-in person's own
    // ------------------------------------------------------------------

    @Test
    void testGetMine_ShouldAskForThatPersonsListFromTheLast24Hours() {
        // WHY: The staff ID handed to the repository is the signed-in person's, and the
        //      window is 24 hours back from now by the clock
        Notification notice = new Notification();
        notice.setNotificationId(1L);
        notice.setKind("item_86");
        notice.setBody("Garden Toast is 86'd");
        notice.setCreatedByName("Kit");
        notice.setCreatedAt(clock.instant());
        when(notificationRepository.findOnTheListOf(7L, clock.instant().minus(Duration.ofHours(24)), NotificationService.LIST_LIMIT))
                .thenReturn(List.of(notice));

        List<NotificationView> mine = notificationService.getMine(SARAH);

        assertEquals(1, mine.size());
        assertEquals("Garden Toast is 86'd", mine.get(0).text());
        assertEquals("Kit", mine.get(0).fromName());
        verify(notificationRepository, never()).findOnTheListOf(eq(9L), any(), anyInt());
    }

    @Test
    void testGetMine_AfterTimeHasPassed_ShouldMoveTheWindowWithIt() {
        clock.advance(Duration.ofHours(5));

        notificationService.getMine(SARAH);

        verify(notificationRepository).findOnTheListOf(7L, clock.instant().minus(Duration.ofHours(24)), NotificationService.LIST_LIMIT);
    }

    @Test
    void testDismiss_ShouldRecordItForThatPerson() {
        when(notificationRepository.countBelongingTo(1L, 7L, clock.instant().minus(Duration.ofHours(24)))).thenReturn(1L);

        notificationService.dismiss(1L, SARAH);

        verify(notificationRepository).dismiss(1L, 7L, clock.instant());
    }

    @Test
    void testDismiss_WhenItIsNotTheirs_ShouldBeNotFoundAndRecordNothing() {
        // WHAT: A notification that is someone else's, their own doing, too old, or made up
        // WHY: All four look the same from outside: "not found". The answer must not say
        //      which, or it would tell a person what others have been sent.
        when(notificationRepository.countBelongingTo(anyLong(), eq(7L), any())).thenReturn(0L);

        assertThrows(ResourceNotFoundException.class, () -> notificationService.dismiss(99L, SARAH));

        verify(notificationRepository, never()).dismiss(anyLong(), anyLong(), any());
    }

    @Test
    void testDismissAll_ShouldDismissThatPersonsListAndNobodyElses() {
        notificationService.dismissAll(SARAH);

        verify(notificationRepository).dismissAllOf(7L, clock.instant().minus(Duration.ofHours(24)), clock.instant());
        verify(notificationRepository, never()).dismissAllOf(eq(9L), any(), any());
    }
}
