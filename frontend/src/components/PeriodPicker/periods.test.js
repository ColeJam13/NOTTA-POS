import { rangeOf, periodOf, toLocalDateTime, PERIOD_KINDS } from './periods';
import { forgetServerTime, noteServerTime } from '../../services/serverClock';

/**
 * Tests for what each reporting period means.
 *
 * WHY THE DATE IS PINNED: "this week" depends on what day it is. On a Sunday it is the
 * same two moments as "today", and a test that only runs on real dates would prove
 * nothing one day in seven. So the clock is held at a known Wednesday (and, for two
 * tests, moved to a Sunday and to New Year's Day).
 *
 * Only Date is faked (toFake: ['Date']); timers still run, so nothing here waits.
 */

const at = (year, month, date, hour = 14, minute = 30) => vi.setSystemTime(new Date(year, month - 1, date, hour, minute, 0));

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  at(2026, 11, 18);                 // Wednesday 18 November 2026, 2:30 in the afternoon
  forgetServerTime();               // The backend's clock is this device's, unless a test says otherwise
});

afterEach(() => {
  forgetServerTime();
  vi.useRealTimers();
});

test('today is midnight to the last second of today', () => {
  expect(rangeOf(periodOf('today'))).toEqual({ from: '2026-11-18T00:00:00', to: '2026-11-18T23:59:59' });
});

test('this week starts on Sunday and runs to the end of today', () => {
  // Sunday the 15th. Through the end of today, not the end of the week: nothing has been sold tomorrow.
  expect(rangeOf(periodOf('week'))).toEqual({ from: '2026-11-15T00:00:00', to: '2026-11-18T23:59:59' });
});

test('on a Sunday, this week is today', () => {
  at(2026, 11, 22);
  expect(rangeOf(periodOf('week'))).toEqual({ from: '2026-11-22T00:00:00', to: '2026-11-22T23:59:59' });
  expect(rangeOf(periodOf('week'))).toEqual(rangeOf(periodOf('today')));
});

test('a week that began last year starts in last year', () => {
  at(2026, 1, 1);                   // A Thursday. The Sunday before it is 28 December 2025.
  expect(rangeOf(periodOf('week'))).toEqual({ from: '2025-12-28T00:00:00', to: '2026-01-01T23:59:59' });
});

test('this month is its first day through its last', () => {
  expect(rangeOf(periodOf('month'))).toEqual({ from: '2026-11-01T00:00:00', to: '2026-11-30T23:59:59' });

  at(2028, 2, 10);                  // A leap year's February has 29 days
  expect(rangeOf(periodOf('month'))).toEqual({ from: '2028-02-01T00:00:00', to: '2028-02-29T23:59:59' });
});

test('a custom period runs from the start of its first day to the end of its last', () => {
  expect(rangeOf({ kind: 'custom', customStart: '2026-03-05', customEnd: '2026-03-07' }))
    .toEqual({ from: '2026-03-05T00:00:00', to: '2026-03-07T23:59:59' });

  // One day picked as both dates is that whole day, not an empty period
  expect(rangeOf({ kind: 'custom', customStart: '2026-11-18', customEnd: '2026-11-18' }))
    .toEqual({ from: '2026-11-18T00:00:00', to: '2026-11-18T23:59:59' });
});

test('a custom period with a date missing reaches back to 2000, or forward to the end of today', () => {
  expect(rangeOf(periodOf('custom'))).toEqual({ from: '2000-01-01T00:00:00', to: '2026-11-18T23:59:59' });
  expect(rangeOf({ kind: 'custom', customStart: '2026-03-05', customEnd: '' }))
    .toEqual({ from: '2026-03-05T00:00:00', to: '2026-11-18T23:59:59' });
  expect(rangeOf({ kind: 'custom', customStart: '', customEnd: '2026-03-07' }))
    .toEqual({ from: '2000-01-01T00:00:00', to: '2026-03-07T23:59:59' });
});

test('the dates of a custom period are ignored while another kind is chosen', () => {
  // The boxes keep their dates so that going back to CUSTOM shows them again
  expect(rangeOf({ kind: 'today', customStart: '2026-03-05', customEnd: '2026-03-07' }))
    .toEqual({ from: '2026-11-18T00:00:00', to: '2026-11-18T23:59:59' });
});

test('a kind nobody has heard of is treated as today', () => {
  expect(rangeOf(periodOf('fortnight'))).toEqual(rangeOf(periodOf('today')));
});

test('every period is counted from the backend\'s day, not the device\'s', () => {
  // The backend is 12 hours ahead of this tablet: it is already Thursday the 19th there,
  // and Thursday is the day checks are being stamped with.
  const backendTime = new Date(Date.now() + 12 * 60 * 60 * 1000);
  noteServerTime({ headers: { get: (name) => (name.toLowerCase() === 'x-server-time' ? toLocalDateTime(backendTime) : null) } });

  expect(rangeOf(periodOf('today'))).toEqual({ from: '2026-11-19T00:00:00', to: '2026-11-19T23:59:59' });
  expect(rangeOf(periodOf('week'))).toEqual({ from: '2026-11-15T00:00:00', to: '2026-11-19T23:59:59' });
});

test('the periods on offer are today, this week, this month, and custom, in that order', () => {
  expect(PERIOD_KINDS.map(({ kind, label }) => `${kind}:${label}`))
    .toEqual(['today:TODAY', 'week:THIS WEEK', 'month:THIS MONTH', 'custom:CUSTOM']);
});

test('a moment is written the way the backend reads one: local time, no zone', () => {
  expect(toLocalDateTime(new Date(2026, 0, 5, 7, 8, 9))).toBe('2026-01-05T07:08:09');
});
