package com.notapos.security;

import com.notapos.exception.TooManyAttemptsException;
import com.notapos.testsupport.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SignInGuard: counting wrong PINs and pausing sign-in.
 *
 * WHAT: The guard is driven directly, the way StaffService drives it: begin()
 *       before a PIN is checked, then endWrong() or endRight().
 * WHY:  These rules are the real protection for a 4-digit PIN. Each test is one
 *       way of guessing, or one way an honest shift could be caught by mistake.
 *
 * Time is a clock the tests move by hand (MutableClock), so a one-hour pause
 * is tested in no time.
 *
 * @author CJ
 */
class SignInGuardTest {

    private static final String BAR = "192.168.1.21";
    private static final String PATIO = "192.168.1.22";

    private MutableClock clock;
    private SignInGuard guard;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        guard = new SignInGuard(clock);
    }

    // ------------------------------------------------------------------
    // Helpers: one whole attempt
    // ------------------------------------------------------------------

    private void wrongPin(String device) {
        guard.begin(device);
        guard.endWrong(device);
    }

    private void rightPin(String device) {
        guard.begin(device);
        guard.endRight(device);
    }

    private void wrongPins(int howMany, String device) {
        for (int attempt = 0; attempt < howMany; attempt++) {
            wrongPin(device);
        }
    }

    private boolean isPaused(String device) {                       // Would the next attempt there be refused?
        try {
            guard.begin(device);
        } catch (TooManyAttemptsException paused) {
            return true;
        }
        guard.endWithoutAnswer(device);                              // It was let in: give the place back without counting anything
        return false;
    }

    private TooManyAttemptsException refusal(String device) {
        return assertThrows(TooManyAttemptsException.class, () -> guard.begin(device));
    }

    // ------------------------------------------------------------------
    // Rule 1: five wrong in a row on one device
    // ------------------------------------------------------------------

    @Test
    void testFiveWrongInARow_ShouldPauseThatDeviceFor30Seconds() {
        // WHAT: Test the basic limit
        // WHY: Without it a script tries all 10,000 PINs in a minute

        // When
        wrongPins(4, BAR);
        assertFalse(isPaused(BAR));                                  // Four is still fine
        wrongPin(BAR);

        // Then
        TooManyAttemptsException refused = refusal(BAR);
        assertEquals("Too many wrong PINs on this device. Try again in 30 seconds.", refused.getMessage());
        assertEquals(30, refused.getRetryAfterSeconds());
    }

    @Test
    void testAPause_ShouldCountDownAndThenEnd() {
        // WHAT: Test what a paused device is told as time passes
        // WHY: The PIN pad shows this message; it should say how long is really left

        // Given
        wrongPins(5, BAR);

        // When & Then
        clock.advance(Duration.ofSeconds(18));
        assertEquals("Too many wrong PINs on this device. Try again in 12 seconds.", refusal(BAR).getMessage());

        clock.advance(Duration.ofSeconds(11));
        assertEquals("Too many wrong PINs on this device. Try again in 1 second.", refusal(BAR).getMessage());

        clock.advance(Duration.ofSeconds(1));
        assertFalse(isPaused(BAR));
    }

    @Test
    void testOneDevicesWrongPins_ShouldNotPauseAnother() {
        // WHAT: Test that each device has its own count
        // WHY: The count used to be shared by the whole restaurant, so one fumbling
        //      terminal, or anything on the network, could stop everyone signing in

        // When
        wrongPins(5, BAR);

        // Then
        assertTrue(isPaused(BAR));
        assertFalse(isPaused(PATIO));
    }

    @Test
    void testARightPin_ShouldStartTheRowAgain() {
        // WHAT: Test mistakes spread through a shift on a shared terminal
        // WHY: A mistype is nearly always followed by the same person typing it right.
        //      Those must never add up to a pause, however many there are in a night.

        // When - forty people each mistype three times and then get it right, ten minutes apart
        for (int person = 0; person < 40; person++) {
            wrongPins(3, BAR);
            rightPin(BAR);
            clock.advance(Duration.ofMinutes(10));
        }

        // Then
        assertFalse(isPaused(BAR));
    }

    @Test
    void testWrongPinsSpreadOverHours_ShouldStillAddUp() {
        // WHAT: Test guessing slowly: one wrong PIN an hour, never a right one
        // WHY: The old limit was "five within a minute", so four a minute went on for
        //      ever. A row of wrong PINs is a row however slowly it is typed.

        // When
        for (int guess = 0; guess < 4; guess++) {
            wrongPin(BAR);
            clock.advance(Duration.ofHours(1));
        }
        wrongPin(BAR);

        // Then
        assertTrue(isPaused(BAR));
    }

    // ------------------------------------------------------------------
    // Rule 2: ten wrong within ten minutes, whatever came between
    // ------------------------------------------------------------------

    @Test
    void testGuessingBetweenProperSignIns_ShouldStillBePaused() {
        // WHAT: Test the way around rule 1: four guesses, a proper sign-in to clear the row, repeat
        // WHY: Someone with a PIN of their own could otherwise guess at a manager's for
        //      ever without tripping anything

        // When - two rounds of "four wrong, one right" are let through
        for (int round = 0; round < 2; round++) {
            wrongPins(4, BAR);
            rightPin(BAR);
            assertFalse(isPaused(BAR));
        }

        // And - the third round reaches ten wrong within ten minutes
        wrongPins(2, BAR);

        // Then
        assertTrue(isPaused(BAR));
    }

    @Test
    void testTenWrongSpreadOverALongShift_ShouldNotCountTogether() {
        // WHAT: Test the same pattern at the speed of an honest busy terminal
        // WHY: Rule 2 looks at ten minutes only. Ten mistypes in a night, each followed
        //      by the right PIN, is an ordinary Friday.

        // When - a mistype and a right PIN every two minutes, for an hour
        for (int signIn = 0; signIn < 30; signIn++) {
            wrongPin(BAR);
            rightPin(BAR);
            clock.advance(Duration.ofMinutes(2));
        }

        // Then
        assertFalse(isPaused(BAR));
    }

    // ------------------------------------------------------------------
    // Rule 3: each pause is longer than the last
    // ------------------------------------------------------------------

    @Test
    void testEachPauseOnADevice_ShouldBeLongerThanTheLast() {
        // WHAT: Test five wrong PINs, wait out the pause, five more, and so on
        // WHY: The old pause was always 30 seconds and the count started fresh after it:
        //      about ten guesses a minute for ever. Now it ends up at five an hour.

        String[] expected = {"30 seconds", "1 minute", "5 minutes", "15 minutes", "1 hour", "1 hour", "1 hour"};

        for (int pause = 0; pause < expected.length; pause++) {
            // When - five more wrong PINs
            wrongPins(5, BAR);

            // Then - a longer pause than last time
            TooManyAttemptsException refused = refusal(BAR);
            assertEquals("Too many wrong PINs on this device. Try again in " + expected[pause] + ".", refused.getMessage());

            // And - it ends exactly when it said it would
            clock.advance(Duration.ofSeconds(refused.getRetryAfterSeconds() - 1));
            assertTrue(isPaused(BAR));
            clock.advance(Duration.ofSeconds(1));
            assertFalse(isPaused(BAR));
        }
    }

    @Test
    void testARightPin_ShouldNotShortenTheNextPause() {
        // WHAT: Test signing in properly between two rounds of wrong PINs
        // WHY: Otherwise someone with a PIN of their own could keep every pause at 30 seconds

        // Given - one pause served
        wrongPins(5, BAR);
        clock.advance(Duration.ofSeconds(30));

        // When - a right PIN, then five more wrong ones
        rightPin(BAR);
        wrongPins(5, BAR);

        // Then - the second pause is still the longer one
        assertEquals("Too many wrong PINs on this device. Try again in 1 minute.", refusal(BAR).getMessage());
    }

    @Test
    void testADeviceWithACleanDay_ShouldStartAgainAt30Seconds() {
        // WHAT: Test a device that was paused, then typed no wrong PIN for a day
        // WHY: A terminal that had a bad night should not carry it for ever. One day is
        //      far longer than the longest pause, so waiting it out gains a guesser nothing.

        // Given - two pauses served
        wrongPins(5, BAR);
        clock.advance(Duration.ofSeconds(30));
        wrongPins(5, BAR);
        clock.advance(Duration.ofMinutes(1));

        // When - a day with only right PINs, and the sweep runs
        rightPin(BAR);
        clock.advance(SignInGuard.CLEAN_SLATE_AFTER);
        assertEquals(1, guard.forgetQuietDevices());

        // Then
        wrongPins(5, BAR);
        assertEquals("Too many wrong PINs on this device. Try again in 30 seconds.", refusal(BAR).getMessage());
    }

    @Test
    void testForgetQuietDevices_ShouldKeepADeviceThatIsPausedOrHasWrongPinsOnTheCount() {
        // WHAT: Test what the sweep leaves alone
        // WHY: Forgetting a device forgives it. A device mid-pause, or part-way to one,
        //      must not be forgiven by the tidying-up job.

        // Given - the bar is paused; the patio has typed two wrong PINs
        wrongPins(5, BAR);
        wrongPins(2, PATIO);

        // When
        clock.advance(Duration.ofSeconds(10));
        assertEquals(0, guard.forgetQuietDevices());

        // Then
        assertTrue(isPaused(BAR));
        wrongPins(3, PATIO);                                         // Three more make five in a row
        assertTrue(isPaused(PATIO));
    }

    // ------------------------------------------------------------------
    // Rule 4: the whole restaurant
    // ------------------------------------------------------------------

    @Test
    void testFortyWrongPinsFromManyDevices_ShouldPauseSignInEverywhereForAMinute() {
        // WHAT: Test a guesser who uses a new address for every few guesses
        // WHY: Rules 1 to 3 count per device. A guesser who never shows the same address
        //      twice would never be counted by them. This is the backstop.

        // When - forty devices type one wrong PIN each
        for (int device = 1; device <= 39; device++) {
            wrongPin("10.0.0." + device);
        }
        assertFalse(isPaused(PATIO));
        wrongPin("10.0.0.40");

        // Then - nobody can sign in, on any device, including one that did nothing wrong
        TooManyAttemptsException refused = refusal(PATIO);
        assertEquals("Too many wrong PINs across the restaurant. Sign-in is paused for 1 minute.", refused.getMessage());
        assertEquals(60, refused.getRetryAfterSeconds());

        // And - it lifts after a minute
        clock.advance(Duration.ofMinutes(1));
        assertFalse(isPaused(PATIO));
    }

    @Test
    void testAFullFloorMistypingAllNight_ShouldNotReachTheRestaurantLimit() {
        // WHAT: Test fifteen terminals, each with a mistype every five minutes, for six hours
        // WHY: The old restaurant-wide limit could be tripped by a busy shift. This one
        //      is set far above honest use: thirty wrong PINs every ten minutes is under it.

        for (int minute = 0; minute < 6 * 60; minute += 5) {
            for (int terminal = 1; terminal <= 15; terminal++) {
                String device = "192.168.1." + terminal;
                wrongPin(device);
                rightPin(device);
            }
            assertFalse(isPaused(PATIO), "Sign-in was paused everywhere at minute " + minute);
            clock.advance(Duration.ofMinutes(5));
        }
    }

    // ------------------------------------------------------------------
    // Many attempts at the same instant
    // ------------------------------------------------------------------

    @Test
    void testManyAttemptsSentAtOnce_ShouldNotGetMoreGuessesThanAreLeft() {
        // WHAT: Test a script that sends a hundred PINs at the same moment
        // WHY: Checking a PIN takes time. If an attempt were only counted once it had
        //      been checked, all hundred would be checked before the first was counted.
        //      An attempt holds its place from begin(), so only five get in.

        // When - a hundred attempts begin before any has been answered
        int letIn = 0;
        for (int attempt = 0; attempt < 100; attempt++) {
            try {
                guard.begin(BAR);
                letIn++;
            } catch (TooManyAttemptsException refused) {
                assertEquals("Too many sign-in attempts at once. Try again in a moment.", refused.getMessage());
            }
        }

        // Then
        assertEquals(SignInGuard.WRONG_IN_A_ROW, letIn);

        // And - when those five turn out wrong, the device is paused like any other
        for (int attempt = 0; attempt < letIn; attempt++) {
            guard.endWrong(BAR);
        }
        assertEquals("Too many wrong PINs on this device. Try again in 30 seconds.", refusal(BAR).getMessage());
    }

    @Test
    void testAnAttemptThatWasNeverAnswered_ShouldNotCountAsWrong() {
        // WHAT: Test attempts that end without the PIN being checked (the database failed)
        // WHY: Nobody typed a wrong PIN, so nothing should be counted, and the places
        //      those attempts held must be given back

        // When
        for (int attempt = 0; attempt < 20; attempt++) {
            guard.begin(BAR);
            guard.endWithoutAnswer(BAR);
        }

        // Then - still all five guesses left
        wrongPins(4, BAR);
        assertFalse(isPaused(BAR));
    }

    // ------------------------------------------------------------------
    // Wording
    // ------------------------------------------------------------------

    @Test
    void testInWords_ShouldRoundUpToWhatAPersonWouldSay() {
        // WHAT: Test turning seconds into the words shown on the PIN pad
        // WHY: "Try again in 287 seconds" is not something to read mid-service

        assertEquals("1 second", SignInGuard.inWords(1));
        assertEquals("30 seconds", SignInGuard.inWords(30));
        assertEquals("1 minute", SignInGuard.inWords(60));
        assertEquals("5 minutes", SignInGuard.inWords(287));         // Rounded up: never told a wait shorter than it is
        assertEquals("1 hour", SignInGuard.inWords(3600));
        assertEquals("2 hours", SignInGuard.inWords(3601));
    }
}
