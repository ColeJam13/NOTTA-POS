package com.notapos.service;

import com.notapos.config.RestaurantTime;
import com.notapos.dto.ChallengeRequest;
import com.notapos.dto.ChallengeView;
import com.notapos.dto.ChallengeView.Participant;
import com.notapos.dto.StaffSalesView;
import com.notapos.entity.Challenge;
import com.notapos.entity.ChallengeAward;
import com.notapos.entity.MenuItem;
import com.notapos.enums.ChallengeMeasure;
import com.notapos.exception.ChallengeRuleException;
import com.notapos.exception.InvalidChallengeException;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.repository.ChallengeAwardRepository;
import com.notapos.repository.ChallengeRepository;
import com.notapos.repository.MenuItemRepository;
import com.notapos.security.StaffSession;
import com.notapos.service.StaffSalesReportService.ItemsCounted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Challenges: what a manager sets, and how far each person has got.
 *
 * WHAT A CHALLENGE IS: a name, one of four measures, a target, a period, and
 * optionally a prize in the manager's own words (the owner's decision D6).
 *
 * THREE RULES HOLD THIS CLASS TOGETHER
 *
 *   1. PROGRESS IS WORKED OUT, NEVER STORED. Each time the challenges are asked
 *      for, each person's progress is read from the checks, through the same
 *      report the leaderboard is (StaffSalesReportService). So a challenge counts
 *      a check exactly as the leaderboard and the Financials screen do: it belongs
 *      to the period it was CLOSED in and to the person who OPENED it. A person's
 *      bar therefore moves when a check is paid, not when an item is rung in.
 *
 *   2. NOBODY JOINS. Everyone who opens a check that is closed in a challenge's
 *      period is in it.
 *
 *   3. AN AWARD IS A FACT, AND IT IS STORED. That a manager handed over a prize
 *      is not in any check. Once a challenge has an award it is a record: it can
 *      no longer be changed or deleted.
 *
 * WHEN IS A CHALLENGE "COMPLETED"? For items sold, total sales, and biggest
 * check: as soon as progress reaches the target, because those only ever go up.
 * A check average can go down again with the next small check, so that one is
 * completed only when the challenge has ENDED with the average at or above the
 * target (and with the minimum number of checks). While it runs, a person at or
 * above the target "meets the target", which the screen shows as ON TRACK.
 *
 * Who may do what is decided at the controller (Permissions.SEE_CHALLENGES for
 * reading, MANAGE_CHALLENGES for everything else).
 *
 * @author CJ
 */
@Service
@Transactional(readOnly = true)      // Every method runs in a database transaction; read-only unless a method says otherwise
public class ChallengeService {

    private static final Logger log = LoggerFactory.getLogger(ChallengeService.class);

    // How many ended challenges the screen is sent, the most recently ended first.
    // Each one listed has its progress worked out from the checks, and a restaurant's
    // list of old challenges only grows.
    public static final int FINISHED_LISTED = 20;

    private final ChallengeRepository challengeRepository;
    private final ChallengeAwardRepository challengeAwardRepository;
    private final MenuItemRepository menuItemRepository;
    private final StaffSalesReportService staffSalesReportService;
    private final RestaurantTime restaurantTime;

    @Autowired
    public ChallengeService(ChallengeRepository challengeRepository,
                            ChallengeAwardRepository challengeAwardRepository,
                            MenuItemRepository menuItemRepository,
                            StaffSalesReportService staffSalesReportService,
                            RestaurantTime restaurantTime) {
        this.challengeRepository = challengeRepository;
        this.challengeAwardRepository = challengeAwardRepository;
        this.menuItemRepository = menuItemRepository;
        this.staffSalesReportService = staffSalesReportService;
        this.restaurantTime = restaurantTime;
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    /**
     * The challenges the screen shows: the ones running now (ending soonest first),
     * then the ones still to start (starting soonest first), then the last
     * FINISHED_LISTED that ended (most recently ended first).
     */
    public List<ChallengeView> getChallenges() {
        Instant now = restaurantTime.now();

        List<Challenge> notEnded = challengeRepository.findByEndsAtAfterOrderByEndsAtAscChallengeIdAsc(now);
        List<Challenge> running = notEnded.stream().filter(challenge -> !challenge.getStartsAt().isAfter(now)).toList();
        List<Challenge> upcoming = notEnded.stream().filter(challenge -> challenge.getStartsAt().isAfter(now))
                .sorted(Comparator.comparing(Challenge::getStartsAt).thenComparing(Challenge::getChallengeId))
                .toList();
        List<Challenge> finished = challengeRepository
                .findByEndsAtLessThanEqualOrderByEndsAtDescChallengeIdDesc(now, Limit.of(FINISHED_LISTED));

        List<Challenge> listed = new ArrayList<>(running);
        listed.addAll(upcoming);
        listed.addAll(finished);
        return viewsOf(listed, now);
    }

    // ------------------------------------------------------------------
    // What a manager does
    // ------------------------------------------------------------------

    /** Make a challenge. Who made it is the signed-in manager. */
    @Transactional
    public ChallengeView create(ChallengeRequest request, StaffSession by) {
        // A new row built here from the request's fields: the request carries no ID,
        // so it can never mean "replace that challenge"
        Challenge challenge = new Challenge();
        fillIn(challenge, request);
        challenge.setCreatedByStaffId(by.staffId());
        challenge.setCreatedByName(by.name());
        challenge.setCreatedAt(restaurantTime.now());

        Challenge saved = challengeRepository.save(challenge);
        log.info("{} (staff {}) made challenge {} \"{}\"", by.name(), by.staffId(), saved.getChallengeId(), saved.getName());
        return viewOf(saved);
    }

    /**
     * Change a challenge: any of its fields, including its period.
     *
     * @throws ChallengeRuleException if a prize has been marked awarded on it (409)
     */
    @Transactional
    public ChallengeView change(Long challengeId, ChallengeRequest request, StaffSession by) {
        Challenge challenge = lockedChallenge(challengeId);
        if (challengeAwardRepository.existsByChallengeId(challengeId)) {
            // Changing the target or the period now could leave a prize given to someone
            // who, by the new rules, never completed it
            throw new ChallengeRuleException("A prize has been marked awarded on this challenge, so it is a record now and cannot be changed.");
        }
        fillIn(challenge, request);

        Challenge saved = challengeRepository.save(challenge);
        log.info("{} (staff {}) changed challenge {} \"{}\"", by.name(), by.staffId(), saved.getChallengeId(), saved.getName());
        return viewOf(saved);
    }

    /**
     * End a running challenge now. It moves to FINISHED with everyone's progress
     * as it stands; checks closed after this moment no longer count toward it.
     *
     * @throws ChallengeRuleException if it has not started, or has already ended (409)
     */
    @Transactional
    public ChallengeView endNow(Long challengeId, StaffSession by) {
        Challenge challenge = lockedChallenge(challengeId);
        Instant now = restaurantTime.now();
        if (challenge.getStartsAt().isAfter(now)) {
            throw new ChallengeRuleException("This challenge has not started yet. Delete it, or change when it starts.");
        }
        if (!challenge.getEndsAt().isAfter(now)) {
            throw new ChallengeRuleException("This challenge has already ended.");
        }
        challenge.setEndsAt(now);

        Challenge saved = challengeRepository.save(challenge);
        log.info("{} (staff {}) ended challenge {} \"{}\" early", by.name(), by.staffId(), saved.getChallengeId(), saved.getName());
        return viewOf(saved);
    }

    /**
     * Delete a challenge.
     *
     * @throws ChallengeRuleException if a prize has been marked awarded on it (409)
     */
    @Transactional
    public void delete(Long challengeId, StaffSession by) {
        Challenge challenge = lockedChallenge(challengeId);
        if (challengeAwardRepository.existsByChallengeId(challengeId)) {
            throw new ChallengeRuleException("A prize has been marked awarded on this challenge. It stays as a record and cannot be deleted.");
        }
        challengeRepository.delete(challenge);
        log.info("{} (staff {}) deleted challenge {} \"{}\"", by.name(), by.staffId(), challengeId, challenge.getName());
    }

    /**
     * Record that a person was handed a challenge's prize.
     *
     * Only someone who has completed the challenge can be marked; the app checks
     * that against the checks, now, and does not take the screen's word for it.
     * Marking the same person again changes nothing: the award keeps the manager
     * and the time of the first marking.
     *
     * @throws ChallengeRuleException if that person has not completed the challenge (409)
     */
    @Transactional
    public ChallengeView markAwarded(Long challengeId, Long staffId, StaffSession by) {
        Challenge challenge = lockedChallenge(challengeId);

        Participant winner = viewOf(challenge).participants().stream()
                .filter(participant -> participant.staffId().equals(staffId))
                .findFirst()
                .orElseThrow(() -> new ChallengeRuleException("That person has not completed this challenge."));
        if (!winner.completed()) {
            throw new ChallengeRuleException(winner.name() + " has not completed this challenge.");
        }

        int recorded = challengeAwardRepository.awardOnce(
                challengeId, staffId, winner.name(), by.staffId(), by.name(), restaurantTime.now());
        if (recorded == 1) {
            log.info("{} (staff {}) marked the prize of challenge {} \"{}\" as awarded to {} (staff {})",
                    by.name(), by.staffId(), challengeId, challenge.getName(), winner.name(), staffId);
        }
        return viewOf(challenge);
    }

    // ------------------------------------------------------------------
    // A request's fields, checked and written onto a challenge
    // ------------------------------------------------------------------

    private void fillIn(Challenge challenge, ChallengeRequest request) {
        ChallengeMeasure measure = ChallengeMeasure.fromValue(request.measure())
                .orElseThrow(() -> new InvalidChallengeException("Pick what the challenge measures."));
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new InvalidChallengeException("The end has to be after the start.");
        }

        // The fields that belong to one measure only. They are written for that measure
        // and cleared for every other, whatever the request carries.
        String menuCategory = null;
        Long menuItemId = null;
        Integer minChecks = null;

        if (measure == ChallengeMeasure.ITEMS_SOLD) {
            boolean namesACategory = request.menuCategory() != null && !request.menuCategory().isBlank();
            boolean namesAnItem = request.menuItemId() != null;
            if (namesACategory && namesAnItem) {
                throw new InvalidChallengeException("Pick a menu category or one item, not both.");
            }
            if (!namesACategory && !namesAnItem) {
                throw new InvalidChallengeException("Pick the menu category or the item this challenge counts.");
            }
            // 15.00 is fine; 15.5 items is not a thing a person can sell
            if (request.target().stripTrailingZeros().scale() > 0) {
                throw new InvalidChallengeException("A number of items has to be a whole number.");
            }
            if (namesAnItem) {
                if (!menuItemRepository.existsById(request.menuItemId())) {
                    throw new InvalidChallengeException("That menu item does not exist.");
                }
                menuItemId = request.menuItemId();
            } else {
                // The categories on offer come from the menu itself, so one that no item
                // has was typed by hand or belongs to an item that has since moved
                menuCategory = request.menuCategory().strip();
                if (!menuItemRepository.existsByCategory(menuCategory)) {
                    throw new InvalidChallengeException("No menu item is in the category \"" + menuCategory + "\".");
                }
            }
        } else if (measure == ChallengeMeasure.CHECK_AVERAGE) {
            if (request.minChecks() == null) {
                throw new InvalidChallengeException("Say how many checks a person needs before their average counts.");
            }
            minChecks = request.minChecks();
        }

        challenge.setName(request.name().strip());
        challenge.setDescription(textOrNull(request.description()));
        challenge.setMeasure(measure.getValue());
        challenge.setTarget(request.target());
        challenge.setMenuCategory(menuCategory);
        challenge.setMenuItemId(menuItemId);
        challenge.setMinChecks(minChecks);
        // Typed as times on the restaurant's clock; stored as moments
        challenge.setStartsAt(restaurantTime.moment(request.startsAt()));
        challenge.setEndsAt(restaurantTime.moment(request.endsAt()));
        challenge.setPrize(textOrNull(request.prize()));
    }

    private String textOrNull(String text) {                // "  " and "" are "nothing was written"
        return text == null || text.isBlank() ? null : text.strip();
    }

    private Challenge lockedChallenge(Long challengeId) {
        return challengeRepository.findByIdLockedForUpdate(challengeId)
                .orElseThrow(() -> new ResourceNotFoundException("Challenge not found with id: " + challengeId));
    }

    // ------------------------------------------------------------------
    // A challenge as the screen is sent it
    // ------------------------------------------------------------------

    private ChallengeView viewOf(Challenge challenge) {
        return viewsOf(List.of(challenge), restaurantTime.now()).get(0);
    }

    // Two queries for all of them together (their awards, and the names of the menu
    // items they count), then each challenge's progress.
    private List<ChallengeView> viewsOf(List<Challenge> challenges, Instant now) {
        if (challenges.isEmpty()) {
            return List.of();
        }

        Map<Long, List<ChallengeAward>> awardsByChallengeId = challengeAwardRepository
                .findByChallengeIdIn(challenges.stream().map(Challenge::getChallengeId).toList()).stream()
                .collect(Collectors.groupingBy(ChallengeAward::getChallengeId));

        Set<Long> menuItemIds = challenges.stream().map(Challenge::getMenuItemId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> menuItemNamesById = menuItemIds.isEmpty() ? Map.of()
                : menuItemRepository.findAllById(menuItemIds).stream()
                        .collect(Collectors.toMap(MenuItem::getMenuItemId, MenuItem::getName));

        return challenges.stream()
                .map(challenge -> viewOf(challenge,
                        awardsByChallengeId.getOrDefault(challenge.getChallengeId(), List.of()),
                        // (asked only when there is an item: an empty Map.of() throws on a null key)
                        challenge.getMenuItemId() == null ? null : menuItemNamesById.get(challenge.getMenuItemId()),
                        now))
                .toList();
    }

    private ChallengeView viewOf(Challenge challenge, List<ChallengeAward> awards, String menuItemName, Instant now) {
        String state = challenge.getStartsAt().isAfter(now) ? ChallengeView.UPCOMING
                : challenge.getEndsAt().isAfter(now) ? ChallengeView.ACTIVE
                : ChallengeView.FINISHED;

        // Nothing can have been sold toward a challenge that has not started
        List<Participant> participants = ChallengeView.UPCOMING.equals(state) ? List.of()
                : participantsOf(challenge, awards, ChallengeView.FINISHED.equals(state));

        return new ChallengeView(
                challenge.getChallengeId(),
                challenge.getName(),
                challenge.getDescription(),
                challenge.getMeasure(),
                challenge.getTarget(),
                challenge.getMenuCategory(),
                challenge.getMenuItemId(),
                menuItemName,
                challenge.getMinChecks(),
                restaurantTime.local(challenge.getStartsAt()),
                restaurantTime.local(challenge.getEndsAt()),
                challenge.getPrize(),
                challenge.getCreatedByName(),
                state,
                participants);
    }

    /**
     * Everyone in a challenge with how far they have got, furthest along first.
     *
     * The numbers come from the per-person sales report for the challenge's period:
     * the checks closed in it, each given to whoever opened it. A challenge's moments
     * are turned into the restaurant's local times first, because that is how a
     * check's closing time is stamped.
     */
    private List<Participant> participantsOf(Challenge challenge, List<ChallengeAward> awards, boolean ended) {
        ChallengeMeasure measure = ChallengeMeasure.fromValue(challenge.getMeasure()).orElse(null);
        Map<Long, ChallengeAward> awardsByStaffId = awards.stream()
                .collect(Collectors.toMap(ChallengeAward::getStaffId, Function.identity(), (first, second) -> first));

        StaffSalesView sales = staffSalesReportService.getReport(
                restaurantTime.local(challenge.getStartsAt()),
                restaurantTime.local(challenge.getEndsAt()),
                itemsCountedBy(challenge));

        List<Participant> participants = new ArrayList<>();
        Set<Long> listed = new HashSet<>();
        for (StaffSalesView.Row row : sales.staff()) {
            BigDecimal progress = progressOf(row, measure);
            boolean meetsTarget = measure != null
                    && progress.compareTo(challenge.getTarget()) >= 0
                    && (measure != ChallengeMeasure.CHECK_AVERAGE || row.checksClosed() >= challenge.getMinChecks());
            // A check average can still fall while the challenge runs (see the class comment)
            boolean completed = meetsTarget && (measure != ChallengeMeasure.CHECK_AVERAGE || ended);

            ChallengeAward award = awardsByStaffId.get(row.staffId());
            participants.add(new Participant(row.staffId(), row.name(), progress, row.checksClosed(), meetsTarget, completed,
                    award != null ? restaurantTime.local(award.getAwardedAt()) : null,
                    award != null ? award.getAwardedByName() : null));
            listed.add(row.staffId());
        }

        // An award is a fact, so its holder is always listed, even if the checks no longer
        // show how they earned it. (Nothing removes a paid check today; this is for the
        // day something can, such as a voided item.)
        for (ChallengeAward award : awards) {
            if (listed.add(award.getStaffId())) {
                participants.add(new Participant(award.getStaffId(), award.getStaffName(), BigDecimal.ZERO, 0, false, false,
                        restaurantTime.local(award.getAwardedAt()), award.getAwardedByName()));
            }
        }

        // Furthest along first; people level with each other by name, then ID, so the
        // list is in the same order every time it is asked for
        participants.sort(Comparator.comparing(Participant::progress).reversed()
                .thenComparing(Participant::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Participant::staffId));
        return participants;
    }

    // Which column of the sales report is this challenge's progress
    private BigDecimal progressOf(StaffSalesView.Row row, ChallengeMeasure measure) {
        if (measure == null) {
            return BigDecimal.ZERO;                         // A measure this version does not know: nobody has progress
        }
        return switch (measure) {
            case ITEMS_SOLD -> BigDecimal.valueOf(row.itemsSold());
            case TOTAL_SALES -> row.sales();
            case CHECK_AVERAGE -> row.checkAverage();
            case BIGGEST_CHECK -> row.biggestCheck();
        };
    }

    // For a challenge that counts items: which items. The other measures do not look at
    // the items column at all.
    private ItemsCounted itemsCountedBy(Challenge challenge) {
        if (challenge.getMenuItemId() != null) {
            return ItemsCounted.ofMenuItem(challenge.getMenuItemId());
        }
        if (challenge.getMenuCategory() != null) {
            return ItemsCounted.inCategory(challenge.getMenuCategory());
        }
        return ItemsCounted.EVERYTHING;
    }
}
