package com.notapos.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Moves a time between the two ways this backend writes one.
 *
 * THE TWO WAYS
 *   A MOMENT (Instant, a "timestamptz" column): one point in time, the same
 *   everywhere. The newer tables store these (sessions, notifications, challenges).
 *
 *   A LOCAL DATE AND TIME (LocalDateTime, a "timestamp" column): what the clock on
 *   the restaurant's wall says, with no time zone written down. Orders, items, and
 *   payments are stamped this way, and it is how EVERY time is written in JSON.
 *
 * WHY ONE WAY IN JSON: a screen works out "5m ago" and "2h left" by comparing a
 *   stamp with the backend's time, which every answer carries in the X-Server-Time
 *   header as a local date and time (ServerTimeFilter; serverClock.js on the
 *   frontend). The comparison is only right when the stamp is written the same way
 *   as the header. A notification's time was once sent as a moment in UTC
 *   ("...Z"): on a tablet set to another time zone than the backend, "5m ago" came
 *   out hours wrong. So a moment is stored as a moment and is converted here, to
 *   the restaurant's own clock, on its way to a screen.
 *
 * It also answers the question the other way round: a challenge is stored as two
 * moments, and the checks it counts are stamped with local times, so "the checks
 * closed during this challenge" needs the challenge's moments as local times.
 *
 * THE ZONE is the one the backend's machine is set to, which is also what
 * LocalDateTime.now() uses to stamp an order. A test can hand in another, so that
 * what it expects does not depend on the machine it runs on.
 *
 * @author CJ
 */
@Component
public class RestaurantTime {

    private final Clock clock;
    private final ZoneId zone;

    @Autowired
    public RestaurantTime(Clock clock) {
        this(clock, ZoneId.systemDefault());
    }

    public RestaurantTime(Clock clock, ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    public Instant now() {                                  // This moment
        return clock.instant();
    }

    public LocalDateTime localNow() {                       // This moment, as the restaurant's clock shows it
        return local(now());
    }

    public LocalDateTime local(Instant moment) {            // A stored moment, as the restaurant's clock showed it (null stays null)
        return moment == null ? null : LocalDateTime.ofInstant(moment, zone);
    }

    // A local date and time, as the moment it was. (In the one hour a year that the
    // clocks repeat, a local time names two moments; this picks the earlier one.)
    public Instant moment(LocalDateTime local) {
        return local == null ? null : local.atZone(zone).toInstant();
    }
}
