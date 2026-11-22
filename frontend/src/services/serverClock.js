/**
 * What time the backend thinks it is.
 *
 * WHY: The backend stamps things with its own clock: "this edit window closes
 *      at 19:15:31", "sent at 19:02", "closed at 21:40". To show a countdown or
 *      "5m ago", a screen has to compare such a stamp with "now". The only
 *      "now" a tablet has by itself is its own clock, and that is not the
 *      clock the stamp was written with:
 *        - a tablet running two minutes slow showed an edit window with two
 *          extra minutes on it, long after the kitchen had the items;
 *        - a tablet set to another time zone showed every ticket as hours
 *          late, or as brand new.
 *
 * HOW: The backend puts its current time on every answer, in the X-Server-Time
 *      header. apiFetch (services/api.js) hands each answer to noteServerTime
 *      below, which works out how far the backend's clock is ahead of this
 *      device's. From then on:
 *
 *        serverNow()            "now", by the backend's clock
 *        onThisDevice(stamp)    a backend stamp, moved onto this device's clock
 *
 *      This device's clock is still used for one thing: counting time passing
 *      (a second is a second on any clock). It is no longer trusted to know
 *      what time it IS.
 *
 * THE RULE FOR SCREENS: never compare a time from the backend with Date.now()
 *      or new Date(). Use serverNow() for "how long ago" and "how long until",
 *      and onThisDevice() when something has to run at a backend time.
 *
 * The header has no time zone, and neither do the backend's stamps. JavaScript
 * reads both as if they were this device's local time. That is wrong by the
 * same amount for both, so the difference between them is right, which is all
 * anything here uses.
 */

// The header the backend sends its time in (ServerTimeFilter on the backend)
const SERVER_TIME_HEADER = 'X-Server-Time';

// How many milliseconds the backend's clock is ahead of this device's (negative =
// behind). 0 until the first answer arrives, which means "trust this device" - there
// is nothing else to go on yet. Signing in is always the first answer, so no screen
// that shows a time is drawn before this is known.
let backendAheadMs = 0;

/**
 * Take the backend's time from an answer. Called by apiFetch for every answer.
 *
 * The newest answer always wins: if someone corrects this device's clock in the
 * middle of a shift, the very next answer puts the difference right again.
 *
 * (The time was written a few milliseconds before it is read here, while the
 * answer travelled. That makes this clock a few milliseconds slow, which no
 * screen can show: they all count in whole seconds or minutes.)
 *
 * @param {Response} response - An answer from the backend
 */
export const noteServerTime = (response) => {
  // An answer with no headers, or without this one, changes nothing. (Tests that fake
  // the backend with plain objects send answers like that.)
  const headers = response && response.headers;
  const text = headers && typeof headers.get === 'function' ? headers.get(SERVER_TIME_HEADER) : null;
  if (!text) return;

  const backendTime = new Date(text).getTime();
  if (Number.isNaN(backendTime)) return;        // Not a time at all: keep what we had

  backendAheadMs = backendTime - Date.now();
};

/**
 * "Now" by the backend's clock, in milliseconds: the number to compare a
 * backend stamp with.
 *
 * Example: minutes since an item was sent
 *   (serverNow() - new Date(item.sentAt).getTime()) / 60000
 *
 * @returns {number} - Milliseconds, in the same reading as new Date(stamp).getTime()
 */
export const serverNow = () => Date.now() + backendAheadMs;

/**
 * A backend stamp as a moment on THIS device's clock.
 *
 * Use it when something on this device has to happen at a backend time. The
 * edit-window countdown is the example: the backend says when the window
 * closes, and the timer that counts down to it runs on this device.
 *
 * @param {string} backendTimestamp - e.g. "2026-11-21T19:15:31.250" (delayExpiresAt)
 * @returns {Date} - The same moment, readable against new Date() on this device
 */
export const onThisDevice = (backendTimestamp) =>
  new Date(new Date(backendTimestamp).getTime() - backendAheadMs);

/**
 * A moment by the backend's clock, written the way the backend writes its stamps:
 * a local date and time with no zone, "2026-11-21T19:15:31".
 *
 * Use it when the frontend has to MAKE a stamp that will be read like the backend's:
 * the template gallery's made-up tickets and notifications ("sent 12 minutes ago" is
 * asBackendStamp(serverNow() - 12 * 60 * 1000)), or a default for a date-and-time box.
 * Never toISOString() for this: that writes the moment in UTC with a "Z", which is a
 * different clock, and "12m ago" then comes out hours wrong on a device set to another
 * time zone than the backend.
 *
 * @param {number} backendMs - Milliseconds in the reading serverNow() gives
 * @returns {string} - e.g. "2026-11-21T19:15:31"
 */
export const asBackendStamp = (backendMs) => {
  const date = new Date(backendMs);
  const pad = (number) => String(number).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
};

/**
 * Go back to "nothing heard from the backend yet". For tests, so one test's
 * pretend clock does not leak into the next.
 */
export const forgetServerTime = () => {
  backendAheadMs = 0;
};
