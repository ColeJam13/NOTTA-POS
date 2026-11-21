package com.notapos.config;

import com.notapos.testsupport.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ServerTimeFilter: the backend's time on every answer.
 *
 * The filter is handed a clock the test sets by hand (MutableClock), so the
 * exact text of the header can be checked. MockHttpServletRequest and
 * MockHttpServletResponse are Spring's stand-ins for a real request and
 * answer; MockFilterChain stands for "the rest of the backend".
 *
 * @author CJ
 */
class ServerTimeFilterTest {

    private final MutableClock clock = new MutableClock();              // Starts at 2026-11-20T18:00:00Z
    private final ServerTimeFilter filter = new ServerTimeFilter(clock);

    // Run one request through the filter and return the header it put on the answer
    private String headerOnAnAnswer() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/tables"), response, new MockFilterChain());
        return response.getHeader(ServerTimeFilter.SERVER_TIME_HEADER);
    }

    // What the clock's moment reads as on the wall of the room the backend is in
    private LocalDateTime backendWallClock() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
    }

    @Test
    void testTheHeader_ShouldBeTheClocksMomentInTheBackendsOwnTimeZone() throws Exception {
        // WHAT: Test what time the header says
        // WHY: Timestamps on orders are written with LocalDateTime.now(): local time, in the
        //      backend's zone, with no zone attached. The header must read the same way, or
        //      a screen comparing the two would be off by the zone's offset.

        // When
        LocalDateTime inTheHeader = LocalDateTime.parse(headerOnAnAnswer());

        // Then
        assertEquals(backendWallClock(), inTheHeader);
    }

    @Test
    void testTheHeader_ShouldAlwaysHaveThreeDigitsOfMilliseconds() throws Exception {
        // WHAT: Test the exact form of the text, at a moment that falls on a whole second
        //       and at one that does not
        // WHY: JavaScript's date format is defined with exactly three digits after the
        //      seconds. Java's default drops them when they are zero and prints as many as
        //      nine otherwise; every browser reads the three-digit form the same way.

        String onTheSecond = headerOnAnAnswer();
        clock.advance(Duration.ofNanos(250_400_000));                   // 250.4 milliseconds later
        String betweenSeconds = headerOnAnAnswer();

        assertTrue(onTheSecond.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.000"), onTheSecond);
        assertTrue(betweenSeconds.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.250"), betweenSeconds);
    }

    @Test
    void testTheHeader_ShouldMoveWithTheClock() throws Exception {
        // WHAT: Test two answers ninety seconds apart
        // WHY: The time is read when each request arrives, not once when the backend starts

        LocalDateTime first = LocalDateTime.parse(headerOnAnAnswer());
        clock.advance(Duration.ofSeconds(90));
        LocalDateTime second = LocalDateTime.parse(headerOnAnAnswer());

        assertEquals(Duration.ofSeconds(90), Duration.between(first, second));
    }

    @Test
    void testTheFilter_ShouldPassTheRequestOnToTheRestOfTheBackend() throws Exception {
        // WHAT: Test that the request continues after the header is set
        // WHY: A filter that forgets to call the rest of the chain answers every request
        //      with an empty page

        MockFilterChain restOfTheBackend = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tables");

        filter.doFilter(request, new MockHttpServletResponse(), restOfTheBackend);

        assertSame(request, restOfTheBackend.getRequest());
    }
}
