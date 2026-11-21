package com.notapos.security;

import com.notapos.exception.TooManyAttemptsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts wrong PINs, and pauses sign-in where too many are coming from.
 *
 * WHY: A 4-digit PIN has only 10,000 possibilities, and sign-in asks for no
 *      name, so a wrong guess is a guess at EVERYONE'S PIN at once. Without a
 *      limit a script finds somebody's PIN in minutes. The limit is the real
 *      protection; the PIN's hash is not.
 *
 * WHAT WAS WRONG BEFORE (step A4 of the Phase 2 plan): there was one count for
 * the whole restaurant. Five wrong PINs anywhere paused sign-in everywhere, so
 * a busy shift could trip it and anything on the network could hold it shut.
 * And after each 30-second pause the count started again, which allowed about
 * ten guesses a minute for ever.
 *
 * THE RULES NOW. "A device" is the address a request comes from.
 *
 *   1. FIVE WRONG IN A ROW on one device pauses that device. A right PIN on
 *      that device starts the row again, so ordinary mistakes on a shared
 *      terminal never add up: a mistype is followed by the same person typing
 *      it right.
 *
 *   2. TEN WRONG WITHIN TEN MINUTES on one device pauses it too, whatever was
 *      typed in between. This closes the way around rule 1 for someone who has
 *      a PIN of their own: guess four times, sign in properly to clear the row,
 *      guess four more.
 *
 *   3. EACH PAUSE ON A DEVICE IS LONGER THAN ITS LAST, UP TO FIVE MINUTES:
 *      30 seconds, 1 minute, then 5 minutes every time. A device is given a
 *      clean slate after a day with no wrong PIN. Guessing from one device
 *      ends up at five tries every five minutes.
 *
 *      The pauses used to carry on to 15 minutes and then an hour. The owner
 *      capped them (decision D11, step A6 of the Phase 2 plan): a terminal
 *      dead for an hour on a Friday night is worse than the guessing it guards
 *      against. The price is that one device now gets about 60 guesses an
 *      hour where it got 5; CLAUDE.md says what that means under known gaps.
 *
 *   4. THE WHOLE RESTAURANT, as a backstop: 40 wrong PINs within ten minutes,
 *      from all devices together, pauses sign-in everywhere for one minute.
 *      This is for a guesser who keeps changing address so that rules 1 to 3
 *      never see the same device twice. It is set far above what a full floor
 *      of people mistyping could reach.
 *
 * A pause stops sign-in only. People who are already signed in carry on.
 * While a device is paused its PINs are not looked at, so guessing during a
 * pause tells the guesser nothing.
 *
 * A MANAGER CAN END A DEVICE'S PAUSE EARLY (also D11): pausedDevices() lists
 * what is paused and release() lets one go, with a clean slate. Both are
 * reached through StaffService and are manager-only endpoints. The pause on
 * the whole restaurant (rule 4) cannot be released: it lasts a minute.
 *
 * AN ATTEMPT IS COUNTED BEFORE ITS PIN IS CHECKED. Checking takes a moment. If
 * the count only went up afterwards, a script could send a hundred guesses in
 * the same instant and have all of them checked before the first was counted.
 * So begin() holds a place for the attempt, and only as many may be under way
 * at once as the device has guesses left.
 *
 * WHAT THIS DOES NOT STOP is listed in CLAUDE.md under the known gaps.
 *
 * The counts live in memory and start empty when the backend restarts. That is
 * acceptable: restarting the backend is not something a guesser can do.
 *
 * Every method is "synchronized": only one request at a time may be inside
 * this object, because several devices can be signing in at the same moment
 * and they share the counts.
 *
 * @author CJ
 */
@Component
public class SignInGuard {

    private static final Logger log = LoggerFactory.getLogger(SignInGuard.class);

    // ----- One device -----
    static final int WRONG_IN_A_ROW = 5;                            // Rule 1
    static final int WRONG_WITHIN_WINDOW = 10;                      // Rule 2
    static final Duration WINDOW = Duration.ofMinutes(10);

    // Rule 3: how long each pause on a device lasts. The last one repeats, so no pause
    // is longer than five minutes (the owner's decision D11).
    static final List<Duration> PAUSES = List.of(
            Duration.ofSeconds(30),
            Duration.ofMinutes(1),
            Duration.ofMinutes(5));

    // A device with no wrong PIN for this long is forgotten, and its next pause is 30 seconds again
    static final Duration CLEAN_SLATE_AFTER = Duration.ofHours(24);

    // ----- The whole restaurant (rule 4) -----
    static final int EVERYWHERE_WRONG_WITHIN_WINDOW = 40;
    static final Duration EVERYWHERE_WINDOW = Duration.ofMinutes(10);
    static final Duration EVERYWHERE_PAUSE = Duration.ofMinutes(1);

    // What is remembered about one device
    private static final class Device {
        int wrongInARow;                                            // Since the last right PIN or the last pause
        final Deque<Instant> recentWrong = new ArrayDeque<>();      // When wrong PINs were typed, oldest first
        int beingChecked;                                           // Attempts begun and not yet answered
        int pausesSoFar;                                            // Decides how long the next pause is
        Instant pausedUntil = Instant.MIN;
        Instant lastWrongAt = Instant.MIN;
    }

    /**
     * One device that is paused right now, for the manager's list.
     *
     * @param device      The address its requests come from
     * @param secondsLeft How long until the pause ends by itself, rounded up
     */
    public record PausedDevice(String device, long secondsLeft) {
    }

    private final Clock clock;                                      // "What time is it now?" (a test can hand in its own)
    private final Map<String, Device> devices = new HashMap<>();    // address -> what is remembered about it

    private final Deque<Instant> everywhereRecentWrong = new ArrayDeque<>();
    private int everywhereBeingChecked;
    private Instant everywherePausedUntil = Instant.MIN;

    public SignInGuard(Clock clock) {
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Around one PIN check: begin, then exactly one of the three ends
    // ------------------------------------------------------------------

    /**
     * Call before checking a PIN. Refuses if sign-in is paused for this device
     * or everywhere; otherwise holds a place for the attempt.
     *
     * Every begin() that returns must be followed by exactly one of endRight(),
     * endWrong(), or endWithoutAnswer() for the same device.
     *
     * @param device The address the request came from
     * @throws TooManyAttemptsException (429) if the PIN must not be checked now
     */
    public synchronized void begin(String device) {
        Instant now = clock.instant();

        if (now.isBefore(everywherePausedUntil)) {
            throw paused("Too many wrong PINs across the restaurant. Sign-in is paused for ", everywherePausedUntil, now, ".");
        }

        Device record = devices.computeIfAbsent(key(device), address -> new Device());
        if (now.isBefore(record.pausedUntil)) {
            // The second half tells the person at the terminal there is a way out other than waiting
            throw paused("Too many wrong PINs on this device. Try again in ", record.pausedUntil, now,
                    ", or ask a manager to release this terminal.");
        }

        forgetOlderThan(record.recentWrong, now.minus(WINDOW));
        forgetOlderThan(everywhereRecentWrong, now.minus(EVERYWHERE_WINDOW));

        // Attempts still being checked count as wrong until they are answered (see the class comment)
        boolean deviceHasNoGuessLeft = record.wrongInARow + record.beingChecked >= WRONG_IN_A_ROW
                || record.recentWrong.size() + record.beingChecked >= WRONG_WITHIN_WINDOW;
        boolean restaurantHasNoGuessLeft =
                everywhereRecentWrong.size() + everywhereBeingChecked >= EVERYWHERE_WRONG_WITHIN_WINDOW;
        if (deviceHasNoGuessLeft || restaurantHasNoGuessLeft) {
            throw new TooManyAttemptsException("Too many sign-in attempts at once. Try again in a moment.", 1);
        }

        record.beingChecked++;
        everywhereBeingChecked++;
    }

    public synchronized void endRight(String device) {              // The PIN was right
        Device record = finishCheck(device);
        record.wrongInARow = 0;                                     // Rule 1 starts again. Rule 2's count and the pause ladder do not.
        forgetIfNothingToRemember(device, record, clock.instant());
    }

    public synchronized void endWrong(String device) {              // The PIN was wrong
        Instant now = clock.instant();
        Device record = finishCheck(device);

        record.wrongInARow++;
        record.recentWrong.addLast(now);
        record.lastWrongAt = now;
        everywhereRecentWrong.addLast(now);

        if (record.wrongInARow >= WRONG_IN_A_ROW || record.recentWrong.size() >= WRONG_WITHIN_WINDOW) {
            pauseDevice(device, record, now);
        }
        if (everywhereRecentWrong.size() >= EVERYWHERE_WRONG_WITHIN_WINDOW) {
            pauseEverywhere(now);
        }
    }

    public synchronized void endWithoutAnswer(String device) {      // The PIN was never checked (the database failed, say): not a wrong PIN
        Device record = finishCheck(device);
        forgetIfNothingToRemember(device, record, clock.instant());
    }

    // ------------------------------------------------------------------
    // For a manager: what is paused, and letting one go (decision D11)
    // ------------------------------------------------------------------

    /**
     * The devices that are paused right now, in address order so the list does
     * not jump around on the screen. A device with wrong PINs on its count but
     * no pause running is not in it: people can still sign in there.
     */
    public synchronized List<PausedDevice> pausedDevices() {
        Instant now = clock.instant();
        return devices.entrySet().stream()
                .filter(entry -> now.isBefore(entry.getValue().pausedUntil))
                .map(entry -> new PausedDevice(entry.getKey(), secondsLeft(entry.getValue().pausedUntil, now)))
                .sorted(Comparator.comparing(PausedDevice::device))
                .toList();
    }

    /**
     * End a device's pause now and give it a clean slate: its wrong PINs are
     * forgotten, and if it is paused again the pause is the shortest one.
     *
     * A manager does this for a terminal they can vouch for ("that was Sam
     * fumbling, not somebody guessing"). It signs nobody in: the next person
     * at that terminal still has to type a right PIN, and a wrong one counts
     * from zero like on any other terminal.
     *
     * The count for the whole restaurant (rule 4) keeps the wrong PINs this
     * device added to it. That rule is about all devices together, and one
     * manager's word about one terminal does not change it.
     *
     * @param device The address to release
     * @return True if that device was paused; false if there was nothing to end
     *         (the pause had already run out, or the address was never paused)
     */
    public synchronized boolean release(String device) {
        Device record = devices.get(key(device));
        if (record == null) {
            return false;
        }
        boolean wasPaused = clock.instant().isBefore(record.pausedUntil);

        record.pausedUntil = Instant.MIN;
        record.wrongInARow = 0;
        record.recentWrong.clear();
        record.pausesSoFar = 0;
        record.lastWrongAt = Instant.MIN;
        if (record.beingChecked == 0) {
            devices.remove(key(device));                            // Nothing left to remember about it
        }
        // An attempt still being checked keeps its record, so that finishing it gives its
        // place back to the right device. (A paused device has none: begin() refuses them.)

        return wasPaused;
    }

    // ------------------------------------------------------------------
    // Housekeeping
    // ------------------------------------------------------------------

    /**
     * Forget devices that have earned a clean slate. Run every few minutes by
     * SessionSweepTask, so the list cannot grow for ever.
     *
     * @return How many were forgotten
     */
    public synchronized int forgetQuietDevices() {
        Instant now = clock.instant();
        int before = devices.size();
        devices.entrySet().removeIf(entry -> nothingToRemember(entry.getValue(), now));
        return before - devices.size();
    }

    /**
     * Forget everything: every count and every pause.
     *
     * Used by the integration tests, which share one running backend and must
     * not inherit each other's wrong PINs. Nothing in the app calls it.
     */
    public synchronized void forgetEverything() {
        devices.clear();
        everywhereRecentWrong.clear();
        everywhereBeingChecked = 0;
        everywherePausedUntil = Instant.MIN;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Device finishCheck(String device) {                     // An attempt was answered: give its place back
        Device record = devices.computeIfAbsent(key(device), address -> new Device());
        if (record.beingChecked > 0) {
            record.beingChecked--;
        }
        if (everywhereBeingChecked > 0) {
            everywhereBeingChecked--;
        }
        return record;
    }

    private void pauseDevice(String device, Device record, Instant now) {
        Duration pause = PAUSES.get(Math.min(record.pausesSoFar, PAUSES.size() - 1));
        record.pausedUntil = now.plus(pause);
        record.pausesSoFar++;
        record.wrongInARow = 0;                                     // The pause is the price of those guesses: both counts start again
        record.recentWrong.clear();
        log.warn("Too many wrong PINs from {} - sign-in paused there for {} (pause number {} for this device)",
                key(device), inWords(pause.toSeconds()), record.pausesSoFar);
    }

    private void pauseEverywhere(Instant now) {
        everywherePausedUntil = now.plus(EVERYWHERE_PAUSE);
        everywhereRecentWrong.clear();
        log.warn("{} wrong PINs within {} minutes across all devices - sign-in paused everywhere for {}",
                EVERYWHERE_WRONG_WITHIN_WINDOW, EVERYWHERE_WINDOW.toMinutes(), inWords(EVERYWHERE_PAUSE.toSeconds()));
    }

    private void forgetIfNothingToRemember(String device, Device record, Instant now) {
        if (nothingToRemember(record, now)) {
            devices.remove(key(device));                            // Most devices, most of the time: no record is kept at all
        }
    }

    // True when a device has no pause running, no attempt under way, no wrong PIN in the
    // current row, and has gone long enough without one to be given a clean slate
    private static boolean nothingToRemember(Device record, Instant now) {
        return record.beingChecked == 0
                && record.wrongInARow == 0
                && !now.isBefore(record.pausedUntil)
                && !record.lastWrongAt.isAfter(now.minus(CLEAN_SLATE_AFTER));
    }

    // Drop the times that have left the window. A wrong PIN typed exactly one window ago is
    // out: "within ten minutes" means less than ten minutes ago.
    private static void forgetOlderThan(Deque<Instant> times, Instant cutoff) {
        while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
            times.removeFirst();
        }
    }

    private static String key(String device) {                      // A request with no address still needs a name to be counted under
        return device == null ? "unknown" : device;
    }

    private static TooManyAttemptsException paused(String messageStart, Instant until, Instant now, String messageEnd) {
        long secondsLeft = secondsLeft(until, now);
        return new TooManyAttemptsException(messageStart + inWords(secondsLeft) + messageEnd, secondsLeft);
    }

    // How long until a pause ends, in whole seconds. Rounded up: "30 seconds", never "29"
    // a moment after the pause starts, and never 0 while it is still running.
    private static long secondsLeft(Instant until, Instant now) {
        long millisLeft = Duration.between(now, until).toMillis();
        return Math.max(1, (millisLeft + 999) / 1000);
    }

    // 30 -> "30 seconds", 60 -> "1 minute", 290 -> "5 minutes", 3600 -> "1 hour"
    static String inWords(long seconds) {
        if (seconds < 60) {
            return seconds + (seconds == 1 ? " second" : " seconds");
        }
        long minutes = (seconds + 59) / 60;
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        long hours = (minutes + 59) / 60;
        return hours + (hours == 1 ? " hour" : " hours");
    }
}
