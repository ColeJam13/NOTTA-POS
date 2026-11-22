package com.notapos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One challenge as the Challenges screen shows it, with how far everyone has got.
 *
 * Everyone signed in is sent every participant's progress (the owner's decision
 * D6). Progress is sales and counts of items, never tips: there is no tip in here.
 *
 * Nothing in "participants" is stored. It is worked out from the checks each
 * time this is asked for (ChallengeService), except whether a prize has been
 * handed over, which is a stored fact.
 *
 * @param challengeId   Challenge ID
 * @param name          What it is called
 * @param description   A line about it, or null
 * @param measure       "items_sold", "total_sales", "check_average", or "biggest_check"
 * @param target        What a person has to reach: a number of items, or dollars
 * @param menuCategory  items_sold only: the category whose items count, or null
 * @param menuItemId    items_sold only: the one item that counts, or null
 * @param menuItemName  That item's name (null if it is a category, or the item is gone)
 * @param minChecks     check_average only: how many checks before an average counts
 * @param startsAt      When it starts: the restaurant's own clock, no zone, like every time in JSON
 * @param endsAt        When it ends, written the same way
 * @param prize         A manager's words, or null
 * @param createdByName Who made it
 * @param state         "upcoming" (not started), "active" (running now), or "finished"
 * @param participants  Everyone who opened a check closed in the period, furthest along
 *                      first. Empty for a challenge that has not started.
 */
public record ChallengeView(
        Long challengeId,
        String name,
        String description,
        String measure,
        BigDecimal target,
        String menuCategory,
        Long menuItemId,
        String menuItemName,
        Integer minChecks,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String prize,
        String createdByName,
        String state,
        List<Participant> participants) {

    public static final String UPCOMING = "upcoming";
    public static final String ACTIVE = "active";
    public static final String FINISHED = "finished";

    /**
     * One person in a challenge.
     *
     * @param staffId       Who. The screen finds the signed-in person's own progress by this
     * @param name          Their name as it is today
     * @param progress      How far they have got, in the measure's own unit: items, or dollars
     * @param checksClosed  How many of their checks were closed in the period
     * @param meetsTarget   Whether progress is at or past the target right now (for a check
     *                      average: with enough checks to count)
     * @param completed     Whether they have COMPLETED it. The same as meetsTarget, except for
     *                      a check average, which can still fall while the challenge runs:
     *                      that one is only completed once the challenge has ended.
     * @param awardedAt     When a manager marked the prize as handed over, or null
     * @param awardedByName Which manager, or null
     */
    public record Participant(
            Long staffId,
            String name,
            BigDecimal progress,
            int checksClosed,
            boolean meetsTarget,
            boolean completed,
            LocalDateTime awardedAt,
            String awardedByName) {
    }
}
