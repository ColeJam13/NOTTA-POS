package com.notapos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The backend's clock, as something that can be handed to a class.
 *
 * WHAT: A Clock answers "what time is it now?". Classes whose rules depend on
 *       time passing (a session unused for 12 hours, a pause of 30 seconds
 *       after wrong PINs) ask this clock instead of calling Instant.now().
 * WHY:  So their tests can hand them a clock that the test moves by hand.
 *       "Five wrong PINs, wait 30 seconds, five more: the second pause is
 *       longer" would otherwise take a test over a minute of real waiting, or
 *       could not be tested at all.
 *
 * The real clock is the system's, read in UTC. Only moments (Instant) are read
 * from it, and a moment is the same in every time zone. (ServerTimeFilter reads
 * a moment from it too, and then writes it in the backend's own time zone.)
 *
 * @author CJ
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
