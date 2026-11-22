import { serverNow, asBackendStamp } from '../../services/serverClock';
import { formatTimeAgo } from '../../utils/formatters';

/**
 * How a challenge reads on screen: its goal in words, a person's progress, the time
 * left, its status, and what the form sends.
 *
 * WHAT IS NOT HERE: any adding up. Who has got how far, and who has completed a
 * challenge, is worked out by the backend from the checks (ChallengeService) and
 * arrives ready-made in each participant. These functions only turn the answer into
 * words and numbers for the screen.
 *
 * Plain functions with no React in them, so they are easy to test and so the card,
 * the form, and the template gallery all say the same thing.
 */

// The four things a challenge can measure (the owner's decision D6), in the order the
// form offers them. "counts" says whether progress is a number of items or dollars.
export const MEASURES = [
  { value: 'items_sold', label: 'Items sold', counts: 'items', targetLabel: 'How many to sell' },
  { value: 'total_sales', label: 'Total sales', counts: 'money', targetLabel: 'Sales to reach ($)' },
  { value: 'check_average', label: 'Check average', counts: 'money', targetLabel: 'Average to hold ($)' },
  { value: 'biggest_check', label: 'Biggest single check', counts: 'money', targetLabel: 'Check to reach ($)' }
];

const measureOf = (challenge) => MEASURES.find(measure => measure.value === challenge.measure);

const money = (amount) => `$${Number(amount).toFixed(2)}`;

/**
 * A progress or a target in the challenge's own unit: "12" for items, "$340.00" for money.
 */
export const formatAmount = (challenge, amount) => {
  const measure = measureOf(challenge);
  return measure && measure.counts === 'items' ? String(Math.round(Number(amount))) : money(amount);
};

/**
 * What a person has to do, in a sentence.
 *   "Sell 15 from Sweet"   "Sell 20 of Daily Pastry"   "Reach $500.00 in sales"
 *   "Hold a check average of at least $40.00, over 5 or more checks"
 *   "Close one check of at least $150.00"
 */
export const describeGoal = (challenge) => {
  const target = formatAmount(challenge, challenge.target);
  switch (challenge.measure) {
    case 'items_sold':
      if (challenge.menuCategory) return `Sell ${target} from ${challenge.menuCategory}`;
      // The item's name is the menu's; an item that no longer exists has none
      return `Sell ${target} of ${challenge.menuItemName || 'an item no longer on the menu'}`;
    case 'total_sales':
      return `Reach ${target} in sales`;
    case 'check_average': {
      const checks = challenge.minChecks === 1 ? '1 check or more' : `${challenge.minChecks} or more checks`;
      return `Hold a check average of at least ${target}, over ${checks}`;
    }
    case 'biggest_check':
      return `Close one check of at least ${target}`;
    default:
      return '';
  }
};

// Someone who has closed no check in the challenge's period: at the start, not absent
const NOT_IN_IT_YET = { progress: 0, checksClosed: 0, meetsTarget: false, completed: false, awardedAt: null, awardedByName: null };

/**
 * One person's place in a challenge. Found by staff ID, never by name. A person the
 * backend did not list has not closed a check in the period yet, which is progress of zero.
 */
export const participantFor = (challenge, staffId) =>
  challenge.participants.find(participant => participant.staffId === staffId) || NOT_IN_IT_YET;

/**
 * How full the progress bar is, 0 to 100. Past the target is still a full bar.
 */
export const percentOf = (progress, target) => {
  const share = Number(target) > 0 ? (Number(progress) / Number(target)) * 100 : 0;
  return Math.max(0, Math.min(100, Math.floor(share)));
};

// 3h 15m, 2d 4h, 12m. Whole minutes: anything under one is "less than a minute".
const formatDuration = (ms) => {
  const minutes = Math.floor(ms / 60000);
  if (minutes < 1) return 'less than a minute';
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ${minutes % 60}m`;
  return `${Math.floor(hours / 24)}d ${hours % 24}h`;
};

/**
 * When, in words, measured against the backend's clock (never this device's):
 *   a running challenge   "3h 15m left"
 *   one still to start    "Starts in 2d 4h"
 *   one that has ended    "Ended 5m ago"
 */
export const describeTime = (challenge) => {
  const now = serverNow();
  if (challenge.state === 'upcoming') return `Starts in ${formatDuration(new Date(challenge.startsAt).getTime() - now)}`;
  if (challenge.state === 'finished') return `Ended ${formatTimeAgo(challenge.endsAt)}`;
  return `${formatDuration(new Date(challenge.endsAt).getTime() - now)} left`;
};

/**
 * The word on a challenge's card for one person, and which of three looks it has
 * ('working', 'done', or 'quiet'). The word is what tells the states apart; the look
 * only groups them.
 *
 *   running    COMPLETE       reached the target (items, sales, biggest check)
 *              ON TRACK       a check average at or above the target: it can still fall,
 *                             so it is not complete until the challenge ends
 *              IN PROGRESS    not there yet
 *   to start   NOT STARTED
 *   ended      COMPLETED / NOT COMPLETED
 */
export const statusFor = (challenge, participant) => {
  if (challenge.state === 'upcoming') return { word: 'NOT STARTED', look: 'quiet' };
  if (challenge.state === 'finished') {
    return participant.completed ? { word: 'COMPLETED', look: 'done' } : { word: 'NOT COMPLETED', look: 'quiet' };
  }
  if (participant.completed) return { word: 'COMPLETE', look: 'done' };
  if (participant.meetsTarget) return { word: 'ON TRACK', look: 'working' };
  return { word: 'IN PROGRESS', look: 'working' };
};

/**
 * What to say about the prize for one person, or '' when there is nothing to say
 * (no prize was set, or they have not completed the challenge).
 */
export const describePrize = (challenge, participant) => {
  if (!challenge.prize || !participant.completed) return '';
  return participant.awardedAt ? `Prize awarded by ${participant.awardedByName}` : 'Prize waiting';
};

// ---------------------------------------------------------------------------
// The form
// ---------------------------------------------------------------------------

// "2026-11-22T17:05": what a date-and-time box holds (no seconds)
const boxValue = (stamp) => stamp.slice(0, 16);

/**
 * An empty form for a new challenge: total sales, from now until the end of today,
 * by the backend's clock.
 */
export const emptyForm = () => {
  const now = asBackendStamp(serverNow());
  return {
    name: '',
    description: '',
    measure: 'total_sales',
    target: '',
    counts: '',                           // items_sold only: 'category:Sweet' or 'item:41'
    minChecks: '3',                       // check_average only
    startsAt: boxValue(now),
    endsAt: `${now.slice(0, 10)}T23:59`,
    prize: ''
  };
};

/**
 * The form filled in from an existing challenge, for changing it.
 */
export const formFrom = (challenge) => ({
  name: challenge.name,
  description: challenge.description || '',
  measure: challenge.measure,
  target: String(Number(challenge.target)),
  counts: challenge.menuCategory ? `category:${challenge.menuCategory}`
    : challenge.menuItemId ? `item:${challenge.menuItemId}` : '',
  minChecks: challenge.minChecks ? String(challenge.minChecks) : '3',
  startsAt: boxValue(challenge.startsAt),
  endsAt: boxValue(challenge.endsAt),
  prize: challenge.prize || ''
});

/**
 * What the backend is sent for a form (ChallengeRequest). Only the fields that belong
 * to the chosen measure are filled in; the backend ignores the others anyway, and
 * checks everything again.
 */
export const requestFrom = (form) => {
  const request = {
    name: form.name,
    description: form.description,
    measure: form.measure,
    target: form.target === '' ? null : Number(form.target),
    menuCategory: null,
    menuItemId: null,
    minChecks: null,
    // A date-and-time box has no seconds; the backend reads "2026-11-22T17:05:00"
    startsAt: form.startsAt ? `${form.startsAt}:00` : null,
    endsAt: form.endsAt ? `${form.endsAt}:00` : null,
    prize: form.prize
  };
  if (form.measure === 'items_sold') {
    if (form.counts.startsWith('category:')) request.menuCategory = form.counts.slice('category:'.length);
    if (form.counts.startsWith('item:')) request.menuItemId = Number(form.counts.slice('item:'.length));
  }
  if (form.measure === 'check_average') {
    request.minChecks = form.minChecks === '' ? null : Number(form.minChecks);
  }
  return request;
};

/**
 * What the "which items count" list offers, from the menu itself: every category that
 * has an item, then every item. Nothing here is a list in the code.
 *
 * @param {Array} menuItems - The menu, from GET /api/menu-items
 * @returns {{categories: string[], items: Array<{menuItemId: number, name: string}>}}
 */
export const countableFrom = (menuItems) => ({
  categories: [...new Set(menuItems.map(item => item.category))].sort((a, b) => a.localeCompare(b)),
  items: [...menuItems].sort((a, b) => a.name.localeCompare(b.name)).map(item => ({ menuItemId: item.menuItemId, name: item.name }))
});
