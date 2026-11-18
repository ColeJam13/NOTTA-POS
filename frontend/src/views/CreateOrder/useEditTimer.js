import { useState, useEffect, useRef, useCallback } from 'react';

// How long a server can still edit items after pressing Send
export const EDIT_WINDOW_SECONDS = 15;

// The countdown is set 1 second longer than what is displayed, to make up for
// the time the screen takes to render. Without it the display would start at 14.
const RENDER_COMPENSATION_SECONDS = 1;

/**
 * The edit-window countdown that runs after a server presses Send.
 *
 * WHAT: A custom hook - a function that bundles React state and effects so a
 *       component can use them with one line.
 * WHY:  The countdown state, the once-a-second interval, and the three places
 *       that reset it were all woven through CreateOrder.js. Here they have
 *       one home and clear names.
 *
 * @param {Function} onExpire - Called once when the countdown reaches zero
 * @returns {Object} - isRunning, secondsLeft, and functions to control the timer
 *
 * secondsLeft meanings:
 *   null     -> no timer has run (or the "locked" message was cleared)
 *   1 to 15  -> counting down, items still editable
 *   0        -> just expired, items locked
 */
export function useEditTimer(onExpire) {
  const [timerExpires, setTimerExpires] = useState(null);
  const [secondsLeft, setSecondsLeft] = useState(null);

  // Keep the latest onExpire in a ref so the interval below always calls the
  // current version without having to restart every time the parent re-renders.
  const onExpireRef = useRef(onExpire);
  useEffect(() => {
    onExpireRef.current = onExpire;
  }, [onExpire]);

  useEffect(() => {
    if (!timerExpires) return;

    const interval = setInterval(() => {                              // timer logic
      const now = new Date();
      const diff = Math.floor((timerExpires - now) / 1000);

      if (diff <= 0) {
        setSecondsLeft(0);
        setTimerExpires(null);
        clearInterval(interval);
        onExpireRef.current();
      } else {
        setSecondsLeft(diff);
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [timerExpires]);

  // Begin counting down to the expiry time the backend sent back
  const start = useCallback((expiresAt) => {
    setTimerExpires(expiresAt);
    setSecondsLeft(EDIT_WINDOW_SECONDS);  // Start at 15, let interval handle countdown
  }, []);

  // Give the server a fresh full window (used when an item is added or removed mid-countdown)
  const restart = useCallback(() => {
    const newExpires = new Date(Date.now() + (EDIT_WINDOW_SECONDS + RENDER_COMPENSATION_SECONDS) * 1000);
    setTimerExpires(newExpires);
    setSecondsLeft(EDIT_WINDOW_SECONDS);
  }, []);

  // Pick up a countdown that is already running on the backend (used when a table is
  // reopened during its edit window). Unlike start(), the display begins at the seconds
  // actually left instead of a full 15.
  const resume = useCallback((expiresAt) => {
    const remaining = Math.floor((expiresAt - new Date()) / 1000);
    setTimerExpires(expiresAt);
    setSecondsLeft(Math.min(EDIT_WINDOW_SECONDS, Math.max(1, remaining)));
  }, []);

  // Stop immediately and show the "locked" state (used by Send Now)
  const finishNow = useCallback(() => {
    setSecondsLeft(0);
    setTimerExpires(null);
  }, []);

  // Clear the leftover "locked" message once the server starts a new round of items
  const clearLockedMessage = useCallback(() => {
    setSecondsLeft(current => (current === 0 ? null : current));
  }, []);

  // Back to "no timer has run" (used when the screen starts over after a check is closed)
  const reset = useCallback(() => {
    setTimerExpires(null);
    setSecondsLeft(null);
  }, []);

  return {
    isRunning: timerExpires !== null,
    secondsLeft,
    start,
    restart,
    resume,
    finishNow,
    clearLockedMessage,
    reset
  };
}
