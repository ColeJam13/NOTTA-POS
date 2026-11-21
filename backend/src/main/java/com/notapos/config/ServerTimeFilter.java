package com.notapos.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Puts the backend's current time on every answer, in the X-Server-Time header.
 *
 * WHY: The backend stamps things with its own clock: "this edit window closes
 *      at 19:15:31", "sent at 19:02". A screen then has to compare those stamps
 *      with "now", and the only "now" a tablet has by itself is its own clock.
 *      A tablet running two minutes slow showed an edit window with two extra
 *      minutes on it, long after the kitchen had the items. A tablet set to
 *      another time zone showed every ticket as hours late, or as brand new.
 *      With this header a screen knows what time the BACKEND thinks it is, and
 *      measures every stamp against that (see services/serverClock.js in the
 *      frontend). The tablet's own clock is then only used to count seconds
 *      passing, which any clock does correctly.
 *
 * WHAT IS SENT: the time in the same form as the stamps themselves: a local
 *      date and time with no zone, read in the backend's own time zone, like
 *      2026-11-21T19:15:31.250. That is what LocalDateTime.now() gives, which
 *      is what every stamp on an order is written with. The two must match:
 *      a header in UTC beside stamps in local time would be hours apart.
 *
 * HOW: A "filter" is a check every request passes through on its way to a
 *      controller (StaffTokenFilter is another). This one runs first of all
 *      (@Order, highest precedence), before sign-in is looked at, so even a
 *      refusal carries the time. Unlike StaffTokenFilter it IS a Spring bean
 *      (@Component): Spring Boot adds every filter bean to the web server by
 *      itself, which is exactly what is wanted here.
 *
 * The browser only lets the frontend read this header because WebConfig names
 * it in exposedHeaders.
 *
 * @author CJ
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ServerTimeFilter extends OncePerRequestFilter {

    // The header the time is sent in. The frontend reads the same name (services/api.js).
    public static final String SERVER_TIME_HEADER = "X-Server-Time";

    // Always three digits of milliseconds: 2026-11-21T19:15:31.250. JavaScript's date
    // format is defined with exactly three, so every browser reads this the same way.
    private static final DateTimeFormatter HEADER_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private final Clock clock;

    public ServerTimeFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Set before the request is handled: once an answer has started going out,
        // headers can no longer be added to it
        response.setHeader(SERVER_TIME_HEADER, HEADER_FORMAT.format(now()));

        filterChain.doFilter(request, response);
    }

    // The moment comes from the app's Clock (so a test can set it). It is then read in
    // the backend's own time zone, the same one LocalDateTime.now() uses for the stamps.
    LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
    }
}
