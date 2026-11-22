package com.notapos.config;

import com.notapos.testsupport.MutableClock;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RestaurantTime: a stored moment as the restaurant's clock showed it,
 * and back.
 *
 * @author CJ
 */
class RestaurantTimeTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private final MutableClock clock = new MutableClock();

    @Test
    void testLocal_ShouldBeWhatTheRestaurantsClockShowedAtThatMoment() {
        // WHAT: Test a moment in a restaurant five hours behind UTC
        // WHY: This is the time a screen is sent. It must be the wall clock's, because
        //      that is how the backend's own time is sent (X-Server-Time) and the two
        //      are compared.

        RestaurantTime time = new RestaurantTime(clock, NEW_YORK);

        // 17:00 UTC on a November day is noon in New York
        assertEquals(LocalDateTime.of(2026, 11, 20, 12, 0), time.local(Instant.parse("2026-11-20T17:00:00Z")));
        // ...and 13:00 on a July day, when New York is four hours behind
        assertEquals(LocalDateTime.of(2026, 7, 20, 13, 0), time.local(Instant.parse("2026-07-20T17:00:00Z")));
    }

    @Test
    void testMoment_ShouldBeTheMomentTheRestaurantsClockShowedThatTime() {
        // WHAT: Test the other direction: a time picked on a screen, as a moment to store
        // WHY: A challenge's start and end are typed as local times and stored as moments

        RestaurantTime time = new RestaurantTime(clock, NEW_YORK);

        assertEquals(Instant.parse("2026-11-20T17:00:00Z"), time.moment(LocalDateTime.of(2026, 11, 20, 12, 0)));
    }

    @Test
    void testThereAndBack_ShouldBeTheSameMoment() {
        // WHAT: Test converting a moment to local time and back
        // WHY: A challenge is stored, sent to a screen, edited, and stored again

        RestaurantTime time = new RestaurantTime(clock, NEW_YORK);
        Instant moment = Instant.parse("2026-03-15T23:30:00Z");

        assertEquals(moment, time.moment(time.local(moment)));
    }

    @Test
    void testNow_ShouldComeFromTheClockItWasGiven() {
        // WHAT: Test "now" as a moment and as a local time
        // WHY: So a rule that depends on the time can be tested with a clock moved by hand

        RestaurantTime time = new RestaurantTime(clock, ZoneOffset.UTC);

        assertEquals(clock.instant(), time.now());
        assertEquals(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), time.localNow());
    }

    @Test
    void testNothing_StaysNothing() {
        // WHAT: Test a missing time
        // WHY: An award that has not been made has no time; the answer leaves it empty

        RestaurantTime time = new RestaurantTime(clock, NEW_YORK);

        assertNull(time.local(null));
        assertNull(time.moment(null));
    }
}
