package com.notapos.repository;

import com.notapos.entity.Challenge;
import com.notapos.entity.ChallengeAward;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for ChallengeRepository and ChallengeAwardRepository.
 *
 * Tests the queries behind the challenges list, the "award once" insert, and the
 * rules the database itself enforces (migration V9), against real PostgreSQL.
 *
 * @author CJ
 */
class ChallengeRepositoryTest extends BaseRepositoryTest {

    @Autowired private ChallengeRepository challengeRepository;
    @Autowired private ChallengeAwardRepository challengeAwardRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final Instant NOW = Instant.parse("2026-11-20T18:00:00Z");

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM challenge_awards");
        challengeRepository.deleteAll();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    // A challenge running from so many hours before NOW to so many hours after it (negative = before)
    private Challenge challenge(String name, long startsInHours, long endsInHours) {
        Challenge challenge = new Challenge();
        challenge.setName(name);
        challenge.setMeasure("total_sales");
        challenge.setTarget(new BigDecimal("100.00"));
        challenge.setStartsAt(NOW.plus(Duration.ofHours(startsInHours)));
        challenge.setEndsAt(NOW.plus(Duration.ofHours(endsInHours)));
        challenge.setCreatedByStaffId(2L);
        challenge.setCreatedByName("Morgan");
        challenge.setCreatedAt(NOW);
        return challengeRepository.saveAndFlush(challenge);
    }

    private List<String> names(List<Challenge> challenges) {
        return challenges.stream().map(Challenge::getName).toList();
    }

    // ------------------------------------------------------------------
    // The two halves of the list
    // ------------------------------------------------------------------

    @Test
    void testNotEnded_ShouldBeTheRunningAndTheComing_EndingSoonestFirst() {
        // WHAT: Test the challenges that have not ended
        // WHY: ACTIVE lists what ends soonest first. One that ended is not here; one
        //      that ends exactly now has ended.

        challenge("ends in a day", -1, 24);
        challenge("ends in an hour", -5, 1);
        challenge("starts tomorrow", 24, 48);
        challenge("ended an hour ago", -5, -1);
        challenge("ends this instant", -5, 0);

        assertEquals(List.of("ends in an hour", "ends in a day", "starts tomorrow"),
                names(challengeRepository.findByEndsAtAfterOrderByEndsAtAscChallengeIdAsc(NOW)));
    }

    @Test
    void testEnded_ShouldBeMostRecentlyEndedFirst_AndOnlyAsManyAsAskedFor() {
        // WHAT: Test the challenges that have ended, with a limit
        // WHY: FINISHED shows the newest; the limit keeps the screen from working out
        //      the progress of every challenge the restaurant has ever run

        challenge("ended a week ago", -200, -168);
        challenge("ended an hour ago", -5, -1);
        challenge("ended yesterday", -48, -24);
        challenge("ends this instant", -5, 0);
        challenge("still running", -1, 1);

        assertEquals(List.of("ends this instant", "ended an hour ago", "ended yesterday"),
                names(challengeRepository.findByEndsAtLessThanEqualOrderByEndsAtDescChallengeIdDesc(NOW, Limit.of(3))));
        assertEquals(4, challengeRepository.findByEndsAtLessThanEqualOrderByEndsAtDescChallengeIdDesc(NOW, Limit.of(20)).size());
    }

    @Test
    void testFindByIdLockedForUpdate_ShouldFindTheChallenge_OrNothing() {
        // WHAT: Test the finder every change goes through
        // WHY: It must behave like findById for what it returns. (That it HOLDS the row is
        //      PostgreSQL's "SELECT ... FOR UPDATE"; two requests racing is ChallengesIntegrationTest's job.)

        Challenge saved = challenge("a challenge", -1, 1);

        assertEquals("a challenge", challengeRepository.findByIdLockedForUpdate(saved.getChallengeId()).orElseThrow().getName());
        assertTrue(challengeRepository.findByIdLockedForUpdate(saved.getChallengeId() + 1000).isEmpty());
    }

    @Test
    void testSave_ShouldKeepEveryField_IncludingTheOnesOnlySomeMeasuresUse() {
        // WHAT: Test a challenge that counts one menu item, read back
        // WHY: Hibernate checks the entity against the table at start-up; this checks the
        //      values make the round trip, moments included

        Challenge challenge = new Challenge();
        challenge.setName("Pastry push");
        challenge.setDescription("Sell the case");
        challenge.setMeasure("items_sold");
        challenge.setTarget(new BigDecimal("20.00"));
        challenge.setMenuItemId(41L);
        challenge.setMinChecks(null);
        challenge.setStartsAt(NOW);
        challenge.setEndsAt(NOW.plus(Duration.ofHours(6)));
        challenge.setPrize("First cut Friday");
        challenge.setCreatedByStaffId(2L);
        challenge.setCreatedByName("Morgan");
        challenge.setCreatedAt(NOW);
        Long id = challengeRepository.saveAndFlush(challenge).getChallengeId();

        Challenge found = challengeRepository.findById(id).orElseThrow();
        assertEquals("Sell the case", found.getDescription());
        assertEquals("items_sold", found.getMeasure());
        assertEquals(new BigDecimal("20.00"), found.getTarget());
        assertEquals(41L, found.getMenuItemId());
        assertNull(found.getMenuCategory());
        assertNull(found.getMinChecks());
        assertEquals(NOW, found.getStartsAt());
        assertEquals(NOW.plus(Duration.ofHours(6)), found.getEndsAt());
        assertEquals("First cut Friday", found.getPrize());
    }

    // ------------------------------------------------------------------
    // Awards
    // ------------------------------------------------------------------

    @Test
    void testAwardOnce_ShouldRecordTheAward_AndASecondTimeChangeNothing() {
        // WHAT: Test marking the same person's prize twice, by two managers
        // WHY: A double press, or two managers at once, is not an error, and the record
        //      keeps who marked it FIRST and when

        Challenge challenge = challenge("a challenge", -5, -1);
        Long id = challenge.getChallengeId();

        assertEquals(1, challengeAwardRepository.awardOnce(id, 7L, "Sarah", 2L, "Morgan", NOW));
        assertEquals(0, challengeAwardRepository.awardOnce(id, 7L, "Sarah", 5L, "Alex", NOW.plus(Duration.ofHours(1))));

        List<ChallengeAward> awards = challengeAwardRepository.findByChallengeIdIn(List.of(id));
        assertEquals(1, awards.size());
        assertEquals(7L, awards.get(0).getStaffId());
        assertEquals("Sarah", awards.get(0).getStaffName());
        assertEquals(2L, awards.get(0).getAwardedByStaffId());
        assertEquals("Morgan", awards.get(0).getAwardedByName());
        assertEquals(NOW, awards.get(0).getAwardedAt());
    }

    @Test
    void testAwards_AreOnePerPersonPerChallenge_NotOnePerChallenge() {
        // WHAT: Test two people awarded on one challenge, and one person on two
        // WHY: Everyone who completes a challenge can be handed its prize

        Challenge first = challenge("first", -5, -1);
        Challenge second = challenge("second", -5, -1);

        assertEquals(1, challengeAwardRepository.awardOnce(first.getChallengeId(), 7L, "Sarah", 2L, "Morgan", NOW));
        assertEquals(1, challengeAwardRepository.awardOnce(first.getChallengeId(), 8L, "Dan", 2L, "Morgan", NOW));
        assertEquals(1, challengeAwardRepository.awardOnce(second.getChallengeId(), 7L, "Sarah", 2L, "Morgan", NOW));

        assertEquals(2, challengeAwardRepository.findByChallengeIdIn(List.of(first.getChallengeId())).size());
        assertEquals(3, challengeAwardRepository.findByChallengeIdIn(List.of(first.getChallengeId(), second.getChallengeId())).size());
    }

    @Test
    void testExistsByChallengeId_ShouldSayWhetherAnyPrizeHasBeenAwarded() {
        Challenge awarded = challenge("awarded", -5, -1);
        Challenge untouched = challenge("untouched", -5, -1);
        challengeAwardRepository.awardOnce(awarded.getChallengeId(), 7L, "Sarah", 2L, "Morgan", NOW);

        assertTrue(challengeAwardRepository.existsByChallengeId(awarded.getChallengeId()));
        assertFalse(challengeAwardRepository.existsByChallengeId(untouched.getChallengeId()));
    }

    // ------------------------------------------------------------------
    // What the database itself refuses (migration V9)
    // ------------------------------------------------------------------

    @Test
    void testTheDatabase_RefusesToDeleteAChallengeThatHasAnAward() {
        // WHAT: Test deleting an awarded challenge, going around the service
        // WHY: The service refuses this first, with words. The foreign key is the net
        //      under it: even code that forgot the check cannot destroy the record.

        Challenge challenge = challenge("awarded", -5, -1);
        challengeAwardRepository.awardOnce(challenge.getChallengeId(), 7L, "Sarah", 2L, "Morgan", NOW);

        assertThrows(DataIntegrityViolationException.class, () -> {
            challengeRepository.delete(challenge);
            challengeRepository.flush();
        });
    }

    @Test
    void testTheDatabase_RefusesAnAwardOnAChallengeThatDoesNotExist() {
        assertThrows(DataIntegrityViolationException.class,
                () -> challengeAwardRepository.awardOnce(987654L, 7L, "Sarah", 2L, "Morgan", NOW));
    }

    @Test
    void testTheDatabase_RefusesAChallengeThatEndsBeforeItStarts() {
        // WHY: Such a challenge could never be running, and "time left" would be nonsense

        assertThrows(DataIntegrityViolationException.class, () -> challenge("backwards", 5, 1));
    }

    @Test
    void testTheDatabase_RefusesATargetOfZero() {
        // WHY: Everyone would have completed it before selling anything

        Challenge challenge = new Challenge();
        challenge.setName("free prize");
        challenge.setMeasure("total_sales");
        challenge.setTarget(new BigDecimal("0.00"));
        challenge.setStartsAt(NOW);
        challenge.setEndsAt(NOW.plus(Duration.ofHours(1)));
        challenge.setCreatedByStaffId(2L);
        challenge.setCreatedByName("Morgan");
        challenge.setCreatedAt(NOW);

        assertThrows(DataIntegrityViolationException.class, () -> challengeRepository.saveAndFlush(challenge));
    }
}
