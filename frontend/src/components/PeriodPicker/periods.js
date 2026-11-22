import { serverNow } from '../../services/serverClock';

/**
 * A reporting period, and the two moments it runs between.
 *
 * WHAT: The Financials screen and the leaderboard both report on "the checks closed
 *       between two moments". This file turns a choice (today, this week, this month,
 *       or two dates) into those two moments, written the way the backend reads them.
 * WHY ONE FILE: both screens must mean the same thing by "this week", or a manager
 *       comparing them would be comparing two different weeks. (The backend applies
 *       one counting rule to both reports; this is the frontend's half of that.)
 *
 * A period is a plain object: { kind, customStart, customEnd }.
 *   kind         'today', 'week', 'month', or 'custom'
 *   customStart  what the start date box holds, e.g. '2026-11-18' ('' when empty)
 *   customEnd    the same for the end date box
 * The two dates are only used when kind is 'custom'. They are kept while another kind
 * is chosen, so going back to CUSTOM shows the dates that were there.
 */

// The periods on offer, in the order their buttons are drawn
export const PERIOD_KINDS = [
  { kind: 'today', label: 'TODAY' },
  { kind: 'week', label: 'THIS WEEK' },
  { kind: 'month', label: 'THIS MONTH' },
  { kind: 'custom', label: 'CUSTOM' }
];

// A period of the given kind with no dates picked: periodOf('week')
export const periodOf = (kind) => ({ kind, customStart: '', customEnd: '' });

// Write a Date the way the backend reads it: "2026-11-18T00:00:00", in this device's own
// time with no time zone. (toISOString() would convert to UTC, which is a different clock
// from the one the backend stamps orders with.)
export const toLocalDateTime = (date) => {
  const pad = (number) => String(number).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
};

/**
 * The two moments a period runs between, as text for the backend.
 *
 * @param {{kind: string, customStart: string, customEnd: string}} period
 * @returns {{from: string, to: string}} - e.g. { from: '2026-11-18T00:00:00', to: '2026-11-18T23:59:59' }
 */
export const rangeOf = (period) => {
  // "Today" is the backend's today. Checks are stamped with the backend's clock, so
  // a device whose own date is wrong, or which sits in another time zone, would
  // otherwise ask for a day that is not the one the checks were closed on
  // (see services/serverClock.js). backendToday() makes a new Date each time it is
  // called, because setHours() below changes the Date it is called on.
  const backendToday = () => new Date(serverNow());
  const now = backendToday();
  let startDate, endDate;

  switch (period.kind) {
    case 'week': {
      const weekStart = new Date(now);
      weekStart.setDate(now.getDate() - now.getDay());
      weekStart.setHours(0, 0, 0, 0);
      startDate = weekStart;
      endDate = new Date(backendToday().setHours(23, 59, 59, 999));  // Through the end of today, so a check closed a second from now is still counted
      break;
    }
    case 'month':
      startDate = new Date(now.getFullYear(), now.getMonth(), 1);
      endDate = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59, 999);
      break;
    case 'custom':
      // A date box gives text like "2026-11-18". new Date("2026-11-18") reads that as
      // midnight in UTC, which is the evening BEFORE in US time zones, so picking
      // today as both dates found nothing. Adding a time of day makes JavaScript
      // read it as local time, and the end date runs to the last moment of its day.
      startDate = period.customStart ? new Date(`${period.customStart}T00:00:00`) : new Date(2000, 0, 1);
      endDate = period.customEnd ? new Date(`${period.customEnd}T23:59:59.999`) : new Date(backendToday().setHours(23, 59, 59, 999));
      break;
    case 'today':
    default:
      startDate = new Date(now.setHours(0, 0, 0, 0));
      endDate = new Date(now.setHours(23, 59, 59, 999));
  }

  return { from: toLocalDateTime(startDate), to: toLocalDateTime(endDate) };
};
