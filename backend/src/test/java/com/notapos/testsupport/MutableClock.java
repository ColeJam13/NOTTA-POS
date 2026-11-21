package com.notapos.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock a test moves by hand.
 *
 *     MutableClock clock = new MutableClock();
 *     SignInGuard guard = new SignInGuard(clock);
 *     ...five wrong PINs...
 *     clock.advance(Duration.ofSeconds(31));      // "31 seconds later", instantly
 *
 * WHY: Rules about time passing (a 30-second pause, a session unused for 12
 *      hours) cannot be tested by really waiting. Classes with such rules ask
 *      a Clock what time it is (see TimeConfig); a test hands them this one.
 *      Time stands still until the test moves it, so nothing depends on how
 *      fast the test happens to run.
 *
 * @author CJ
 */
public class MutableClock extends Clock {

    private Instant now = Instant.parse("2026-11-20T18:00:00Z");       // A Friday dinner service; any moment would do

    public void advance(Duration amount) {                          // Move time forward
        now = now.plus(amount);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;                                                // Only moments are read from this clock, and a moment has no zone
    }
}
