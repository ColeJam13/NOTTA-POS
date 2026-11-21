import { forgetServerTime, noteServerTime, onThisDevice, serverNow } from './serverClock';
import { apiFetch } from './api';
import { formatTimeAgo } from '../utils/formatters';

/**
 * Tests for the backend's clock as the frontend knows it.
 *
 * WHY: Every countdown and every "5m ago" on screen is a comparison between a
 *      time the backend wrote and "now". These pin down that "now" comes from
 *      the backend's own time, sent with every answer, and not from whatever
 *      this device's clock happens to say.
 */

const TWO_HOURS_MS = 2 * 60 * 60 * 1000;

// A moment written the way the backend writes one: local date and time with no
// time zone, e.g. "2026-11-21T19:15:31.250"
const asBackendTime = (ms) => {
  const date = new Date(ms);
  const pad = (number, length = 2) => String(number).padStart(length, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${pad(date.getMilliseconds(), 3)}`;
};

// An answer from a backend whose clock is this far ahead of this device's
const answerFromABackend = (aheadMs) => ({
  ok: true,
  status: 200,
  headers: { get: (name) => (name === 'X-Server-Time' ? asBackendTime(Date.now() + aheadMs) : null) },
  json: async () => ({})
});

// Two readings of "now" taken a moment apart are never exactly equal
const expectAbout = (actualMs, expectedMs) => expect(Math.abs(actualMs - expectedMs)).toBeLessThan(250);

beforeEach(() => {
  forgetServerTime();
});

afterEach(() => {
  vi.restoreAllMocks();
});

test('before any answer has arrived, the backend is assumed to agree with this device', () => {
  expectAbout(serverNow(), Date.now());
});

test('after an answer, serverNow is the backend\'s time, not this device\'s', () => {
  noteServerTime(answerFromABackend(TWO_HOURS_MS));
  expectAbout(serverNow(), Date.now() + TWO_HOURS_MS);

  // A backend that is behind this device works the same way
  noteServerTime(answerFromABackend(-TWO_HOURS_MS));
  expectAbout(serverNow(), Date.now() - TWO_HOURS_MS);
});

test('onThisDevice moves a backend time onto this device\'s clock', () => {
  // The backend, two hours ahead, says an edit window closes ten seconds from its "now".
  // On this device that is ten seconds from this device's "now".
  noteServerTime(answerFromABackend(TWO_HOURS_MS));
  const closesAt = asBackendTime(Date.now() + TWO_HOURS_MS + 10000);

  expectAbout(onThisDevice(closesAt).getTime(), Date.now() + 10000);
});

test('the newest answer wins, so a clock corrected mid-shift is picked up straight away', () => {
  noteServerTime(answerFromABackend(TWO_HOURS_MS));
  noteServerTime(answerFromABackend(0));

  expectAbout(serverNow(), Date.now());
});

test('an answer with no time on it, or one that is not a time, changes nothing', () => {
  noteServerTime(answerFromABackend(TWO_HOURS_MS));

  noteServerTime({ ok: true, status: 200, json: async () => ({}) });                           // No headers at all
  noteServerTime({ headers: { get: () => null } });                                             // Headers, but not this one
  noteServerTime({ headers: { get: () => 'not a time' } });
  noteServerTime(undefined);

  expectAbout(serverNow(), Date.now() + TWO_HOURS_MS);
});

test('every answer that comes through apiFetch sets the clock, whatever its status', async () => {
  // A refusal carries the time too (the backend puts it on every answer)
  global.fetch = vi.fn(async () => ({ ...answerFromABackend(TWO_HOURS_MS), ok: false, status: 409 }));

  await apiFetch('/api/anything');

  expectAbout(serverNow(), Date.now() + TWO_HOURS_MS);
});

test('"5m ago" is measured from the backend\'s now', () => {
  // A check closed five minutes ago by the backend's clock, read on a device two hours behind.
  // By the device's own clock that moment is almost two hours in the FUTURE, which
  // used to be shown as "0m ago".
  noteServerTime(answerFromABackend(TWO_HOURS_MS));
  const closedAt = asBackendTime(Date.now() + TWO_HOURS_MS - 5 * 60 * 1000);

  expect(formatTimeAgo(closedAt)).toBe('5m ago');
});
