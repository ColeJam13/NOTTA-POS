package com.notapos.service;

import com.notapos.config.RestaurantTime;
import com.notapos.dto.ChallengeRequest;
import com.notapos.dto.ChallengeView;
import com.notapos.dto.ChallengeView.Participant;
import com.notapos.dto.StaffSalesView;
import com.notapos.entity.Challenge;
import com.notapos.entity.ChallengeAward;
import com.notapos.entity.MenuItem;
import com.notapos.enums.StaffRole;
import com.notapos.exception.ChallengeRuleException;
import com.notapos.exception.InvalidChallengeException;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.repository.ChallengeAwardRepository;
import com.notapos.repository.ChallengeRepository;
import com.notapos.repository.MenuItemRepository;
import com.notapos.security.StaffSession;
import com.notapos.service.StaffSalesReportService.ItemsCounted;
import com.notapos.testsupport.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChallengeService.
 *
 * Tests what a challenge is, how far each person has got, and what a manager may
 * do with one. The sales report a challenge reads its progress from is a Mockito
 * stand-in here: each test says what the checks "hold". That the progress really
 * comes from real checks is ChallengesIntegrationTest's job.
 *
 * The clock is one the test moves, read in UTC, so nothing here depends on the
 * day or the time zone of the machine running it.
 *
 * @author CJ
 */

@ExtendWith(MockitoExtension.class)
class ChallengeServiceTest {

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeAwardRepository challengeAwardRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private StaffSalesReportService staffSalesReportService;

    private final MutableClock clock = new MutableClock();

    private ChallengeService challengeService;

    private static final StaffSession MORGAN = new StaffSession(2L, "Morgan", StaffRole.MANAGER, Instant.EPOCH, false);

    private Instant now;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        challengeService = new ChallengeService(challengeRepository, challengeAwardRepository, menuItemRepository,
                staffSalesReportService, new RestaurantTime(clock, ZoneOffset.UTC));
        now = clock.instant();
        // Unless a test says what the checks hold, they hold nothing: nobody has sold anything
        lenient().when(staffSalesReportService.getReport(any(), any(), any())).thenReturn(new StaffSalesView(List.of()));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private LocalDateTime local(Instant moment) {
        return LocalDateTime.ofInstant(moment, ZoneOffset.UTC);
    }

    // A challenge that started an hour ago and ends in an hour
    private Challenge running(String measure, String target) {
        return challenge(measure, target, now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(1)));
    }

    private Challenge challenge(String measure, String target, Instant startsAt, Instant endsAt) {
        Challenge challenge = new Challenge();
        challenge.setChallengeId(nextId++);
        challenge.setName("A challenge");
        challenge.setMeasure(measure);
        challenge.setTarget(new BigDecimal(target));
        challenge.setStartsAt(startsAt);
        challenge.setEndsAt(endsAt);
        challenge.setCreatedByStaffId(2L);
        challenge.setCreatedByName("Morgan");
        challenge.setCreatedAt(startsAt);
        return challenge;
    }

    // One person's line of the sales report: checks, sales, average, biggest check, items
    private StaffSalesView.Row row(long staffId, String name, int checks, String sales, String average, String biggest, long items) {
        return new StaffSalesView.Row(staffId, name, 1, checks, new BigDecimal(sales), new BigDecimal(average), new BigDecimal(biggest), items);
    }

    // What the checks "hold" for any period and any items
    private void theChecksHold(StaffSalesView.Row... rows) {
        when(staffSalesReportService.getReport(any(), any(), any())).thenReturn(new StaffSalesView(List.of(rows)));
    }

    private void listed(List<Challenge> notEnded, List<Challenge> finished) {
        when(challengeRepository.findByEndsAtAfterOrderByEndsAtAscChallengeIdAsc(now)).thenReturn(notEnded);
        when(challengeRepository.findByEndsAtLessThanEqualOrderByEndsAtDescChallengeIdDesc(eq(now), any(Limit.class))).thenReturn(finished);
    }

    // The one challenge on the list, as the screen is sent it
    private ChallengeView theOnly(Challenge challenge) {
        boolean ended = !challenge.getEndsAt().isAfter(now);
        listed(ended ? List.of() : List.of(challenge), ended ? List.of(challenge) : List.of());
        return challengeService.getChallenges().get(0);
    }

    private Participant participant(ChallengeView view, long staffId) {
        return view.participants().stream().filter(p -> p.staffId() == staffId).findFirst().orElseThrow();
    }

    // An award row. The entity has no setters (rows are only ever written by one query), so a test fills it in directly.
    private ChallengeAward award(Challenge challenge, long staffId, String name, String byName, Instant at) {
        ChallengeAward award = new ChallengeAward();
        ReflectionTestUtils.setField(award, "challengeId", challenge.getChallengeId());
        ReflectionTestUtils.setField(award, "staffId", staffId);
        ReflectionTestUtils.setField(award, "staffName", name);
        ReflectionTestUtils.setField(award, "awardedByStaffId", 2L);
        ReflectionTestUtils.setField(award, "awardedByName", byName);
        ReflectionTestUtils.setField(award, "awardedAt", at);
        return award;
    }

    private void awardsOn(ChallengeAward... awards) {
        when(challengeAwardRepository.findByChallengeIdIn(anyCollection())).thenReturn(List.of(awards));
    }

    // A request for a total-sales challenge, which needs no extra fields
    private ChallengeRequest salesRequest() {
        return new ChallengeRequest("Big night", null, "total_sales", new BigDecimal("500"), null, null, null,
                LocalDateTime.of(2026, 11, 18, 17, 0), LocalDateTime.of(2026, 11, 18, 23, 0), null);
    }

    private ChallengeRequest request(String measure, String target, String category, Long menuItemId, Integer minChecks) {
        return new ChallengeRequest("A challenge", null, measure, new BigDecimal(target), category, menuItemId, minChecks,
                LocalDateTime.of(2026, 11, 18, 17, 0), LocalDateTime.of(2026, 11, 18, 23, 0), null);
    }

    // What the service saved
    private Challenge saved() {
        ArgumentCaptor<Challenge> captor = ArgumentCaptor.forClass(Challenge.class);
        verify(challengeRepository).save(captor.capture());
        return captor.getValue();
    }

    private void savingWorks() {
        when(challengeRepository.save(any(Challenge.class))).thenAnswer(call -> {
            Challenge challenge = call.getArgument(0);
            if (challenge.getChallengeId() == null) challenge.setChallengeId(nextId++);
            return challenge;
        });
    }

    // ------------------------------------------------------------------
    // The list
    // ------------------------------------------------------------------

    @Test
    void testGetChallenges_ShouldListRunningThenComingThenFinished_EachWithItsState() {
        // WHAT: Test the order of the list and the state each challenge is given
        // WHY: The screen draws ACTIVE, then what is coming, then FINISHED, from "state"

        // Given - one of each, and a second one coming that starts sooner than the first
        Challenge runningNow = running("total_sales", "100");
        Challenge startsTomorrow = challenge("total_sales", "100", now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(2)));
        Challenge startsInAnHour = challenge("total_sales", "100", now.plus(Duration.ofHours(1)), now.plus(Duration.ofDays(3)));
        Challenge ended = challenge("total_sales", "100", now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1)));
        // (the repository hands back "not ended" by when each ENDS; the service orders the coming ones by when they START)
        listed(List.of(runningNow, startsTomorrow, startsInAnHour), List.of(ended));
        theChecksHold();

        // When
        List<ChallengeView> list = challengeService.getChallenges();

        // Then
        assertEquals(List.of(runningNow.getChallengeId(), startsInAnHour.getChallengeId(), startsTomorrow.getChallengeId(), ended.getChallengeId()),
                list.stream().map(ChallengeView::challengeId).toList());
        assertEquals(List.of("active", "upcoming", "upcoming", "finished"), list.stream().map(ChallengeView::state).toList());
    }

    @Test
    void testGetChallenges_ShouldAskForOnlyTheLastTwentyThatEnded() {
        // WHAT: Test how many ended challenges are asked for
        // WHY: Each one listed has its progress worked out from the checks, and old
        //      challenges only pile up

        listed(List.of(), List.of());

        challengeService.getChallenges();

        verify(challengeRepository).findByEndsAtLessThanEqualOrderByEndsAtDescChallengeIdDesc(now, Limit.of(ChallengeService.FINISHED_LISTED));
        assertEquals(20, ChallengeService.FINISHED_LISTED);
    }

    @Test
    void testGetChallenges_WhenThereAreNone_ShouldBeEmpty_AndAskNothingElse() {
        // WHAT: Test a restaurant with no challenges
        // WHY: "No challenges yet." comes from an empty list

        listed(List.of(), List.of());

        assertEquals(List.of(), challengeService.getChallenges());
        verifyNoInteractions(staffSalesReportService, challengeAwardRepository, menuItemRepository);
    }

    @Test
    void testAChallengeThatHasNotStarted_ShouldHaveNobodyInIt_AndNotLookAtTheChecks() {
        // WHAT: Test a challenge that starts tomorrow
        // WHY: Nothing can have been sold toward it yet

        Challenge tomorrow = challenge("total_sales", "100", now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(2)));

        ChallengeView view = theOnly(tomorrow);

        assertEquals("upcoming", view.state());
        assertEquals(List.of(), view.participants());
        verifyNoInteractions(staffSalesReportService);
    }

    @Test
    void testAChallengesTimes_ShouldBeSentOnTheRestaurantsClock() {
        // WHAT: Test how the start and end are written in the answer
        // WHY: Stored as moments, sent as local times with no zone: the form the screen
        //      compares with the backend's time to show "2h left" (see RestaurantTime)

        Challenge challenge = running("total_sales", "100");
        theChecksHold();

        ChallengeView view = theOnly(challenge);

        assertEquals(local(challenge.getStartsAt()), view.startsAt());
        assertEquals(local(challenge.getEndsAt()), view.endsAt());
    }

    // ------------------------------------------------------------------
    // Progress, by measure
    // ------------------------------------------------------------------

    @Test
    void testProgress_ShouldBeReadFromTheChecksClosedInTheChallengesPeriod() {
        // WHAT: Test which period the sales report is asked for
        // WHY: Progress is never stored. It is the per-person sales report for exactly
        //      the challenge's period, so a challenge counts a check as the leaderboard does.

        Challenge challenge = running("total_sales", "100");
        theChecksHold();

        theOnly(challenge);

        verify(staffSalesReportService).getReport(local(challenge.getStartsAt()), local(challenge.getEndsAt()), ItemsCounted.EVERYTHING);
    }

    @Test
    void testTotalSales_ProgressIsSales_AndReachingTheTargetCompletesIt() {
        // WHAT: Test a "reach $100 in sales" challenge with one person past it and one short
        // WHY: Sales only go up, so it is completed the moment the target is reached,
        //      while the challenge is still running

        Challenge challenge = running("total_sales", "100");
        theChecksHold(row(7, "Sarah", 3, "120.50", "40.17", "60.00", 9), row(8, "Dan", 1, "99.99", "99.99", "99.99", 2));

        ChallengeView view = theOnly(challenge);

        Participant sarah = participant(view, 7);
        assertEquals(new BigDecimal("120.50"), sarah.progress());
        assertEquals(3, sarah.checksClosed());
        assertTrue(sarah.meetsTarget());
        assertTrue(sarah.completed());

        Participant dan = participant(view, 8);
        assertEquals(new BigDecimal("99.99"), dan.progress());         // One cent short is short
        assertFalse(dan.meetsTarget());
        assertFalse(dan.completed());
    }

    @Test
    void testTotalSales_ExactlyTheTarget_IsReached() {
        // WHAT: Test progress equal to the target, written with different decimals
        // WHY: "At least". And 100 and 100.00 are the same money (compareTo, not equals).

        Challenge challenge = running("total_sales", "100");
        theChecksHold(row(7, "Sarah", 1, "100.00", "100.00", "100.00", 1));

        assertTrue(participant(theOnly(challenge), 7).completed());
    }

    @Test
    void testBiggestCheck_ProgressIsThePersonsLargestCheck() {
        // WHAT: Test a "close one check of at least $150" challenge
        // WHY: One check has to do it; many small ones adding up to more do not

        Challenge challenge = running("biggest_check", "150");
        theChecksHold(row(7, "Sarah", 10, "900.00", "90.00", "149.00", 30), row(8, "Dan", 1, "150.00", "150.00", "150.00", 4));

        ChallengeView view = theOnly(challenge);

        assertEquals(new BigDecimal("149.00"), participant(view, 7).progress());
        assertFalse(participant(view, 7).completed());
        assertTrue(participant(view, 8).completed());
    }

    @Test
    void testItemsSold_InACategory_CountsOnlyThatCategorysItems() {
        // WHAT: Test a "sell 15 from Sweet" challenge
        // WHY: The sales report is asked to count only that category's items, and the
        //      progress is that count

        Challenge challenge = running("items_sold", "15");
        challenge.setMenuCategory("Sweet");
        theChecksHold(row(7, "Sarah", 6, "200.00", "33.33", "50.00", 15), row(8, "Dan", 4, "300.00", "75.00", "90.00", 14));

        ChallengeView view = theOnly(challenge);

        verify(staffSalesReportService).getReport(any(), any(), eq(ItemsCounted.inCategory("Sweet")));
        assertEquals("Sweet", view.menuCategory());
        assertEquals(new BigDecimal("15"), participant(view, 7).progress());
        assertTrue(participant(view, 7).completed());
        assertFalse(participant(view, 8).completed());
    }

    @Test
    void testItemsSold_OfOneItem_CountsOnlyThatItem_AndSaysItsName() {
        // WHAT: Test a "sell 20 Daily Pastries" challenge
        // WHY: One item counts, and the screen prints its name, which the challenge
        //      does not store (it stores the ID, so a renamed item keeps counting)

        Challenge challenge = running("items_sold", "20");
        challenge.setMenuItemId(41L);
        MenuItem pastry = new MenuItem();
        pastry.setMenuItemId(41L);
        pastry.setName("Daily Pastry");
        when(menuItemRepository.findAllById(java.util.Set.of(41L))).thenReturn(List.of(pastry));
        theChecksHold(row(7, "Sarah", 6, "200.00", "33.33", "50.00", 3));

        ChallengeView view = theOnly(challenge);

        verify(staffSalesReportService).getReport(any(), any(), eq(ItemsCounted.ofMenuItem(41L)));
        assertEquals(41L, view.menuItemId());
        assertEquals("Daily Pastry", view.menuItemName());
        assertEquals(new BigDecimal("3"), participant(view, 7).progress());
    }

    @Test
    void testCheckAverage_WhileItRuns_MeetingTheTargetIsNotYetCompleting() {
        // WHAT: Test a running "hold a $40 average over 3 checks" challenge
        // WHY: An average can fall again with the next small check. Someone at $45 now
        //      meets the target (the screen says ON TRACK) but has not completed it,
        //      so no prize can be marked yet.

        Challenge challenge = running("check_average", "40");
        challenge.setMinChecks(3);
        theChecksHold(row(7, "Sarah", 3, "135.00", "45.00", "60.00", 9));

        Participant sarah = participant(theOnly(challenge), 7);

        assertEquals(new BigDecimal("45.00"), sarah.progress());
        assertTrue(sarah.meetsTarget());
        assertFalse(sarah.completed());
    }

    @Test
    void testCheckAverage_OnceItHasEnded_MeetingTheTargetCompletesIt() {
        // WHAT: Test the same challenge after its end
        // WHY: Nothing can change the average now

        Challenge challenge = challenge("check_average", "40", now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1)));
        challenge.setMinChecks(3);
        theChecksHold(row(7, "Sarah", 3, "135.00", "45.00", "60.00", 9), row(8, "Dan", 5, "150.00", "30.00", "70.00", 9));

        ChallengeView view = theOnly(challenge);

        assertEquals("finished", view.state());
        assertTrue(participant(view, 7).completed());
        assertFalse(participant(view, 8).completed());                 // An average of $30 is below $40
    }

    @Test
    void testCheckAverage_WithTooFewChecks_DoesNotCount() {
        // WHAT: Test someone with a high average over fewer checks than the minimum
        // WHY: The minimum is there so that one large check cannot win

        Challenge challenge = challenge("check_average", "40", now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1)));
        challenge.setMinChecks(3);
        theChecksHold(row(7, "Sarah", 2, "400.00", "200.00", "300.00", 9));

        Participant sarah = participant(theOnly(challenge), 7);

        assertEquals(new BigDecimal("200.00"), sarah.progress());      // The average is still shown
        assertFalse(sarah.meetsTarget());
        assertFalse(sarah.completed());
    }

    @Test
    void testParticipants_ShouldBeListedFurthestAlongFirst_ThenByName() {
        // WHAT: Test the order of the people in a challenge
        // WHY: A manager reads the list top down; people level with each other must not
        //      swap places from one refresh to the next

        Challenge challenge = running("total_sales", "100");
        theChecksHold(row(3, "Casey", 1, "50.00", "50.00", "50.00", 1), row(1, "avery", 1, "80.00", "80.00", "80.00", 1),
                row(2, "Blake", 1, "80.00", "80.00", "80.00", 1), row(4, "Drew", 1, "120.00", "120.00", "120.00", 1));

        assertEquals(List.of("Drew", "avery", "Blake", "Casey"),
                theOnly(challenge).participants().stream().map(Participant::name).toList());
    }

    // ------------------------------------------------------------------
    // Awards
    // ------------------------------------------------------------------

    @Test
    void testAnAward_ShouldBeOnThatPersonsLine_WithWhoMarkedItAndWhen() {
        // WHAT: Test a finished challenge with one prize handed over and one waiting
        // WHY: The screen shows "prize awarded" or "prize waiting" from these two fields

        Challenge challenge = challenge("total_sales", "100", now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1)));
        Instant handedOver = now.minus(Duration.ofHours(3));
        awardsOn(award(challenge, 7, "Sarah", "Morgan", handedOver));
        theChecksHold(row(7, "Sarah", 3, "120.00", "40.00", "60.00", 9), row(8, "Dan", 3, "110.00", "36.67", "60.00", 9));

        ChallengeView view = theOnly(challenge);

        assertEquals(local(handedOver), participant(view, 7).awardedAt());
        assertEquals("Morgan", participant(view, 7).awardedByName());
        assertTrue(participant(view, 8).completed());
        assertNull(participant(view, 8).awardedAt());                   // Completed, prize still waiting
        assertNull(participant(view, 8).awardedByName());
    }

    @Test
    void testAnAwardsHolder_ShouldStillBeListed_EvenIfTheChecksNoLongerShowThem() {
        // WHAT: Test an award whose holder is not in the sales report
        // WHY: An award is a fact. Nothing removes a paid check today, but the day
        //      something can (a voided item), a prize that was handed over still was.

        Challenge challenge = challenge("total_sales", "100", now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1)));
        awardsOn(award(challenge, 7, "Sarah", "Morgan", now.minus(Duration.ofHours(3))));
        theChecksHold();

        ChallengeView view = theOnly(challenge);

        Participant sarah = participant(view, 7);
        assertEquals("Sarah", sarah.name());
        assertEquals(0, BigDecimal.ZERO.compareTo(sarah.progress()));
        assertFalse(sarah.completed());
        assertNotNull(sarah.awardedAt());
    }

    @Test
    void testMarkAwarded_ForSomeoneWhoCompletedIt_ShouldRecordWhoAndByWhom() {
        // WHAT: Test a manager marking a prize as handed over
        // WHY: The record says which challenge, which person, which manager, and when

        Challenge challenge = running("total_sales", "100");
        when(challengeRepository.findByIdLockedForUpdate(challenge.getChallengeId())).thenReturn(Optional.of(challenge));
        theChecksHold(row(7, "Sarah", 3, "120.00", "40.00", "60.00", 9));
        when(challengeAwardRepository.awardOnce(anyLong(), anyLong(), anyString(), anyLong(), anyString(), any())).thenReturn(1);

        challengeService.markAwarded(challenge.getChallengeId(), 7L, MORGAN);

        verify(challengeAwardRepository).awardOnce(challenge.getChallengeId(), 7L, "Sarah", 2L, "Morgan", now);
    }

    @Test
    void testMarkAwarded_ForSomeoneWhoHasNotCompletedIt_ShouldBeRefused_AndRecordNothing() {
        // WHAT: Test marking a prize for someone short of the target
        // WHY: "Only a person who completed the challenge can be marked". The app checks
        //      the checks itself; it does not take the screen's word for it.

        Challenge challenge = running("total_sales", "100");
        when(challengeRepository.findByIdLockedForUpdate(challenge.getChallengeId())).thenReturn(Optional.of(challenge));
        theChecksHold(row(8, "Dan", 1, "99.99", "99.99", "99.99", 2));

        ChallengeRuleException refused = assertThrows(ChallengeRuleException.class,
                () -> challengeService.markAwarded(challenge.getChallengeId(), 8L, MORGAN));

        assertEquals("Dan has not completed this challenge.", refused.getMessage());
        verify(challengeAwardRepository, never()).awardOnce(anyLong(), anyLong(), anyString(), anyLong(), anyString(), any());
    }

    @Test
    void testMarkAwarded_ForSomeoneNotInTheChallenge_ShouldBeRefused() {
        // WHAT: Test marking a prize for a staff ID with no checks in the period
        // WHY: A request can name anybody

        Challenge challenge = running("total_sales", "100");
        when(challengeRepository.findByIdLockedForUpdate(challenge.getChallengeId())).thenReturn(Optional.of(challenge));
        theChecksHold(row(7, "Sarah", 3, "120.00", "40.00", "60.00", 9));

        assertThrows(ChallengeRuleException.class, () -> challengeService.markAwarded(challenge.getChallengeId(), 999L, MORGAN));
        verify(challengeAwardRepository, never()).awardOnce(anyLong(), anyLong(), anyString(), anyLong(), anyString(), any());
    }

    @Test
    void testMarkAwarded_ForARunningCheckAverage_ShouldBeRefused_EvenWhenOnTrack() {
        // WHAT: Test marking a check-average prize before the challenge has ended
        // WHY: The average can still fall; the prize would have gone to someone who
        //      then finished below the target

        Challenge challenge = running("check_average", "40");
        challenge.setMinChecks(1);
        when(challengeRepository.findByIdLockedForUpdate(challenge.getChallengeId())).thenReturn(Optional.of(challenge));
        theChecksHold(row(7, "Sarah", 3, "135.00", "45.00", "60.00", 9));

        assertThrows(ChallengeRuleException.class, () -> challengeService.markAwarded(challenge.getChallengeId(), 7L, MORGAN));
    }

    @Test
    void testMarkAwarded_Twice_ShouldAnswerTheSame_WithoutAnError() {
        // WHAT: Test a second press of AWARDED (the insert reports "already there")
        // WHY: Two managers, or one double press, must not be an error. The first
        //      marking's manager and time are kept by the query (see awardOnce).

        Challenge challenge = running("total_sales", "100");
        when(challengeRepository.findByIdLockedForUpdate(challenge.getChallengeId())).thenReturn(Optional.of(challenge));
        theChecksHold(row(7, "Sarah", 3, "120.00", "40.00", "60.00", 9));
        when(challengeAwardRepository.awardOnce(anyLong(), anyLong(), anyString(), anyLong(), anyString(), any())).thenReturn(0);

        ChallengeView view = challengeService.markAwarded(challenge.getChallengeId(), 7L, MORGAN);

        assertEquals(challenge.getChallengeId(), view.challengeId());
    }

    // ------------------------------------------------------------------
    // Making a challenge
    // ------------------------------------------------------------------

    @Test
    void testCreate_ShouldBuildANewChallenge_MadeByTheSignedInManager() {
        // WHAT: Test what is saved for a new challenge
        // WHY: Who made it comes from the sign-in. The times typed on the restaurant's
        //      clock are stored as moments. Text is tidied; an empty box is "nothing".

        savingWorks();
        ChallengeRequest request = new ChallengeRequest("  Big night  ", "   ", "total_sales", new BigDecimal("500"),
                "Sweet", 41L, 3,
                LocalDateTime.of(2026, 11, 18, 17, 0), LocalDateTime.of(2026, 11, 18, 23, 0), " $20 ");

        ChallengeView view = challengeService.create(request, MORGAN);

        Challenge saved = saved();
        assertEquals("Big night", saved.getName());
        assertNull(saved.getDescription());
        assertEquals("total_sales", saved.getMeasure());
        assertEquals(new BigDecimal("500"), saved.getTarget());
        assertEquals(Instant.parse("2026-11-18T17:00:00Z"), saved.getStartsAt());
        assertEquals(Instant.parse("2026-11-18T23:00:00Z"), saved.getEndsAt());
        assertEquals("$20", saved.getPrize());
        assertEquals(2L, saved.getCreatedByStaffId());
        assertEquals("Morgan", saved.getCreatedByName());
        assertEquals(now, saved.getCreatedAt());
        // The fields that belong to other measures are not kept, whatever the request carried
        assertNull(saved.getMenuCategory());
        assertNull(saved.getMenuItemId());
        assertNull(saved.getMinChecks());

        assertEquals("Big night", view.name());
        assertEquals("Morgan", view.createdByName());
    }

    @Test
    void testCreate_ItemsInACategory_ShouldKeepTheCategory_WhenTheMenuHasIt() {
        // WHAT: Test an items challenge on a category
        // WHY: The category has to be one the menu really has

        savingWorks();
        when(menuItemRepository.existsByCategory("Sweet")).thenReturn(true);

        challengeService.create(request("items_sold", "15", " Sweet ", null, null), MORGAN);

        Challenge saved = saved();
        assertEquals("Sweet", saved.getMenuCategory());
        assertNull(saved.getMenuItemId());
    }

    @Test
    void testCreate_ItemsOfOneItem_ShouldKeepTheItem_WhenItExists() {
        // WHAT: Test an items challenge on one menu item

        savingWorks();
        when(menuItemRepository.existsById(41L)).thenReturn(true);

        challengeService.create(request("items_sold", "20.00", null, 41L, null), MORGAN);

        Challenge saved = saved();
        assertEquals(41L, saved.getMenuItemId());
        assertNull(saved.getMenuCategory());
    }

    @Test
    void testCreate_CheckAverage_ShouldKeepTheMinimumNumberOfChecks() {
        // WHAT: Test a check-average challenge

        savingWorks();

        challengeService.create(request("check_average", "40", null, null, 5), MORGAN);

        assertEquals(5, saved().getMinChecks());
    }

    @Test
    void testCreate_ThatDoesNotAddUp_ShouldBeRefusedWithWordsForTheManager() {
        // WHAT: Test each way a challenge's fields can fail to add up
        // WHY: These are rules across fields, which the annotations on the request
        //      cannot check. The message is shown on the form as-is.

        when(menuItemRepository.existsById(41L)).thenReturn(false);
        when(menuItemRepository.existsByCategory("Dinosaurs")).thenReturn(false);

        assertRefused("Pick what the challenge measures.", request("fewest_mistakes", "5", null, null, null));
        assertRefused("Pick the menu category or the item this challenge counts.", request("items_sold", "15", null, null, null));
        assertRefused("Pick the menu category or the item this challenge counts.", request("items_sold", "15", "  ", null, null));
        assertRefused("Pick a menu category or one item, not both.", request("items_sold", "15", "Sweet", 41L, null));
        assertRefused("A number of items has to be a whole number.", request("items_sold", "15.5", "Sweet", null, null));
        assertRefused("That menu item does not exist.", request("items_sold", "15", null, 41L, null));
        assertRefused("No menu item is in the category \"Dinosaurs\".", request("items_sold", "15", "Dinosaurs", null, null));
        assertRefused("Say how many checks a person needs before their average counts.", request("check_average", "40", null, null, null));

        ChallengeRequest endsBeforeItStarts = new ChallengeRequest("Backwards", null, "total_sales", new BigDecimal("100"), null, null, null,
                LocalDateTime.of(2026, 11, 18, 23, 0), LocalDateTime.of(2026, 11, 18, 17, 0), null);
        assertRefused("The end has to be after the start.", endsBeforeItStarts);
        ChallengeRequest noTimeAtAll = new ChallengeRequest("Instant", null, "total_sales", new BigDecimal("100"), null, null, null,
                LocalDateTime.of(2026, 11, 18, 17, 0), LocalDateTime.of(2026, 11, 18, 17, 0), null);
        assertRefused("The end has to be after the start.", noTimeAtAll);

        verify(challengeRepository, never()).save(any());
    }

    private void assertRefused(String message, ChallengeRequest request) {
        InvalidChallengeException refused = assertThrows(InvalidChallengeException.class, () -> challengeService.create(request, MORGAN));
        assertEquals(message, refused.getMessage());
    }

    // ------------------------------------------------------------------
    // Changing, ending, deleting
    // ------------------------------------------------------------------

    @Test
    void testChange_ShouldRewriteTheChallenge_AndKeepWhoMadeIt() {
        // WHAT: Test changing a challenge's fields
        // WHY: A manager fixes a typo or moves the end. Who made it does not change
        //      to whoever edited it.

        Challenge challenge = running("biggest_check", "150");
        challenge.setCreatedByStaffId(5L);
        challenge.setCreatedByName("Alex");
        when(challengeRepository.findByIdLockedForUpdate(challenge.getChallengeId())).thenReturn(Optional.of(challenge));
        savingWorks();

        challengeService.change(challenge.getChallengeId(), salesRequest(), MORGAN);

        Challenge saved = saved();
        assertEquals("Big night", saved.getName());
        assertEquals("total_sales", saved.getMeasure());
        assertEquals(new BigDecimal("500"), saved.getTarget());
        assertEquals(Instant.parse("2026-11-18T23:00:00Z"), saved.getEndsAt());
        assertEquals(5L, saved.getCreatedByStaffId());
        assertEquals("Alex", saved.getCreatedByName());
    }

    @Test
    void testChange_OnceAPrizeHasBeenAwarded_ShouldBeRefused() {
        // WHAT: Test changing a challenge that has an award
        // WHY: Raising the target now would leave a prize with someone who, by the
        //      new rules, never completed it. A challenge with an award is a record.

        Challenge challenge = running("total_sales", "100");
        when(challengeRepository.findByIdLockedForUpdate(challenge.getChallengeId())).thenReturn(Optional.of(challenge));
        when(challengeAwardRepository.existsByChallengeId(challenge.getChallengeId())).thenReturn(true);

        assertThrows(ChallengeRuleException.class, () -> challengeService.change(challenge.getChallengeId(), salesRequest(), MORGAN));
        verify(challengeRepository, never()).save(any());
    }

    @Test
    void testEndNow_ShouldMoveTheEndToThisMoment() {
        // WHAT: Test ending a running challenge early
        // WHY: It becomes FINISHED with everyone's progress as it stands, and checks
        //      closed after this moment no longer count

        Challenge challenge = running("total_sales", "100");
        when(challengeRepository.findByIdLockedForUpdate(challenge.getChallengeId())).thenReturn(Optional.of(challenge));
        savingWorks();
        theChecksHold();

        ChallengeView view = challengeService.endNow(challenge.getChallengeId(), MORGAN);

        assertEquals(now, saved().getEndsAt());
        assertEquals("finished", view.state());
    }

    @Test
    void testEndNow_OnOneThatHasNotStartedOrHasEnded_ShouldBeRefused() {
        // WHAT: Test END on a challenge that is not running
        // WHY: Ending one that has not started would put its end before its start;
        //      one that has ended has nothing left to end

        Challenge tomorrow = challenge("total_sales", "100", now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(2)));
        Challenge yesterday = challenge("total_sales", "100", now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1)));
        when(challengeRepository.findByIdLockedForUpdate(tomorrow.getChallengeId())).thenReturn(Optional.of(tomorrow));
        when(challengeRepository.findByIdLockedForUpdate(yesterday.getChallengeId())).thenReturn(Optional.of(yesterday));

        assertEquals("This challenge has not started yet. Delete it, or change when it starts.",
                assertThrows(ChallengeRuleException.class, () -> challengeService.endNow(tomorrow.getChallengeId(), MORGAN)).getMessage());
        assertEquals("This challenge has already ended.",
                assertThrows(ChallengeRuleException.class, () -> challengeService.endNow(yesterday.getChallengeId(), MORGAN)).getMessage());
        verify(challengeRepository, never()).save(any());
    }

    @Test
    void testDelete_ShouldRemoveAChallengeNobodyHasBeenAwardedFor() {
        // WHAT: Test deleting a challenge with no awards

        Challenge challenge = running("total_sales", "100");
        when(challengeRepository.findByIdLockedForUpdate(challenge.getChallengeId())).thenReturn(Optional.of(challenge));

        challengeService.delete(challenge.getChallengeId(), MORGAN);

        verify(challengeRepository).delete(challenge);
    }

    @Test
    void testDelete_OnceAPrizeHasBeenAwarded_ShouldBeRefused() {
        // WHAT: Test deleting a challenge that has an award
        // WHY: The award is the only record that the prize was handed over

        Challenge challenge = running("total_sales", "100");
        when(challengeRepository.findByIdLockedForUpdate(challenge.getChallengeId())).thenReturn(Optional.of(challenge));
        when(challengeAwardRepository.existsByChallengeId(challenge.getChallengeId())).thenReturn(true);

        assertThrows(ChallengeRuleException.class, () -> challengeService.delete(challenge.getChallengeId(), MORGAN));
        verify(challengeRepository, never()).delete(any());
    }

    @Test
    void testAChallengeThatDoesNotExist_ShouldBeNotFound_WhateverIsAskedOfIt() {
        // WHAT: Test every change on an ID with no challenge behind it
        // WHY: 404, not a new challenge made by accident or a silent success

        when(challengeRepository.findByIdLockedForUpdate(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> challengeService.change(404L, salesRequest(), MORGAN));
        assertThrows(ResourceNotFoundException.class, () -> challengeService.endNow(404L, MORGAN));
        assertThrows(ResourceNotFoundException.class, () -> challengeService.delete(404L, MORGAN));
        assertThrows(ResourceNotFoundException.class, () -> challengeService.markAwarded(404L, 7L, MORGAN));
        verify(challengeRepository, never()).save(any());
    }

    @Test
    void testEveryChange_ShouldHoldTheChallengeWhileItWorks() {
        // WHAT: Test how a challenge is found before it is changed
        // WHY: "No award on it, so it may be deleted" is read-then-act. The locked
        //      finder keeps another manager's AWARDED from landing in between.
        //      A plain findById here would bring the race back.

        Challenge challenge = running("total_sales", "100");
        when(challengeRepository.findByIdLockedForUpdate(challenge.getChallengeId())).thenReturn(Optional.of(challenge));
        savingWorks();
        theChecksHold();

        challengeService.change(challenge.getChallengeId(), salesRequest(), MORGAN);
        challengeService.delete(challenge.getChallengeId(), MORGAN);

        verify(challengeRepository, times(2)).findByIdLockedForUpdate(challenge.getChallengeId());
        verify(challengeRepository, never()).findById(anyLong());
    }
}
