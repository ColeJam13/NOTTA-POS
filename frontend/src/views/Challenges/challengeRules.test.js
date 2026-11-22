import {
  MEASURES, describeGoal, formatAmount, participantFor, percentOf, describeTime, statusFor, describePrize,
  emptyForm, formFrom, requestFrom, countableFrom
} from './challengeRules';
import { asBackendStamp, forgetServerTime, noteServerTime, serverNow } from '../../services/serverClock';

/**
 * Tests for how a challenge reads on screen.
 *
 * These are plain functions, so each test hands one a challenge in the shape the
 * backend answers with (ChallengeView) and looks at the words that come back.
 */

const challenge = (fields) => ({
  challengeId: 1, name: 'A challenge', description: null, measure: 'total_sales', target: 500,
  menuCategory: null, menuItemId: null, menuItemName: null, minChecks: null,
  startsAt: asBackendStamp(serverNow() - 60 * 60 * 1000), endsAt: asBackendStamp(serverNow() + 60 * 60 * 1000),
  prize: '$20', createdByName: 'Morgan', state: 'active', participants: [], ...fields
});

const person = (fields) => ({ staffId: 7, name: 'Sarah', progress: 0, checksClosed: 0, meetsTarget: false, completed: false, awardedAt: null, awardedByName: null, ...fields });

afterEach(() => {
  forgetServerTime();
});

// ---------------------------------------------------------------------------
// The goal, and amounts
// ---------------------------------------------------------------------------

test('each measure reads as a sentence about what to do', () => {
  expect(describeGoal(challenge({ measure: 'items_sold', target: 15, menuCategory: 'Sweet' }))).toBe('Sell 15 from Sweet');
  expect(describeGoal(challenge({ measure: 'items_sold', target: 20, menuItemId: 41, menuItemName: 'Daily Pastry' }))).toBe('Sell 20 of Daily Pastry');
  expect(describeGoal(challenge({ measure: 'total_sales', target: 500 }))).toBe('Reach $500.00 in sales');
  expect(describeGoal(challenge({ measure: 'check_average', target: 40, minChecks: 5 }))).toBe('Hold a check average of at least $40.00, over 5 or more checks');
  expect(describeGoal(challenge({ measure: 'check_average', target: 40, minChecks: 1 }))).toBe('Hold a check average of at least $40.00, over 1 check or more');
  expect(describeGoal(challenge({ measure: 'biggest_check', target: 150 }))).toBe('Close one check of at least $150.00');
});

test('a challenge on a menu item that no longer exists still reads as a sentence', () => {
  expect(describeGoal(challenge({ measure: 'items_sold', target: 20, menuItemId: 41, menuItemName: null }))).toBe('Sell 20 of an item no longer on the menu');
});

test('items are written as a whole number and everything else as money', () => {
  expect(formatAmount(challenge({ measure: 'items_sold' }), 12)).toBe('12');
  expect(formatAmount(challenge({ measure: 'items_sold' }), 15.0)).toBe('15');
  expect(formatAmount(challenge({ measure: 'total_sales' }), 340.5)).toBe('$340.50');
  expect(formatAmount(challenge({ measure: 'check_average' }), 40)).toBe('$40.00');
  expect(formatAmount(challenge({ measure: 'biggest_check' }), 149.99)).toBe('$149.99');
});

test('there are four measures, in the order the form offers them', () => {
  expect(MEASURES.map(measure => measure.value)).toEqual(['items_sold', 'total_sales', 'check_average', 'biggest_check']);
});

// ---------------------------------------------------------------------------
// One person's place
// ---------------------------------------------------------------------------

test('a person is found by staff ID, not by name', () => {
  const two = challenge({ participants: [person({ staffId: 31, name: 'Dan', progress: 80 }), person({ staffId: 8, name: 'Dan', progress: 20 })] });

  expect(participantFor(two, 8).progress).toBe(20);
  expect(participantFor(two, 31).progress).toBe(80);
});

test('someone the backend did not list has closed no check yet: zero, not an error', () => {
  const nobodyYet = challenge({ participants: [] });

  expect(participantFor(nobodyYet, 7)).toEqual({ progress: 0, checksClosed: 0, meetsTarget: false, completed: false, awardedAt: null, awardedByName: null });
});

test('the bar fills in whole percent, and stops at full', () => {
  expect(percentOf(0, 15)).toBe(0);
  expect(percentOf(12, 15)).toBe(80);
  expect(percentOf(14.99, 15)).toBe(99);          // Not rounded up to 100: 100 means reached
  expect(percentOf(15, 15)).toBe(100);
  expect(percentOf(40, 15)).toBe(100);            // Past the target is still a full bar
  expect(percentOf(5, 0)).toBe(0);
});

// ---------------------------------------------------------------------------
// Status and prize
// ---------------------------------------------------------------------------

test('the status word for a running challenge', () => {
  const running = challenge({ state: 'active' });

  expect(statusFor(running, person({}))).toEqual({ word: 'IN PROGRESS', look: 'working' });
  expect(statusFor(running, person({ meetsTarget: true, completed: true }))).toEqual({ word: 'COMPLETE', look: 'done' });
});

test('a check average at the target while the challenge runs is on track, not complete', () => {
  // The backend sends meetsTarget true and completed false: the average can still fall
  const average = challenge({ state: 'active', measure: 'check_average', minChecks: 3 });

  expect(statusFor(average, person({ meetsTarget: true, completed: false }))).toEqual({ word: 'ON TRACK', look: 'working' });
});

test('the status word before a challenge starts and after it ends', () => {
  expect(statusFor(challenge({ state: 'upcoming' }), person({}))).toEqual({ word: 'NOT STARTED', look: 'quiet' });
  expect(statusFor(challenge({ state: 'finished' }), person({ completed: true }))).toEqual({ word: 'COMPLETED', look: 'done' });
  expect(statusFor(challenge({ state: 'finished' }), person({ completed: false }))).toEqual({ word: 'NOT COMPLETED', look: 'quiet' });
});

test('the prize is waiting, or awarded by a manager, only for someone who completed it', () => {
  const withPrize = challenge({ prize: '$20' });

  expect(describePrize(withPrize, person({ completed: false }))).toBe('');
  expect(describePrize(withPrize, person({ completed: true }))).toBe('Prize waiting');
  expect(describePrize(withPrize, person({ completed: true, awardedAt: '2026-11-18T22:15:00', awardedByName: 'Morgan' }))).toBe('Prize awarded by Morgan');
  // No prize was set: there is nothing to wait for
  expect(describePrize(challenge({ prize: null }), person({ completed: true }))).toBe('');
});

// ---------------------------------------------------------------------------
// Time, by the backend's clock
// ---------------------------------------------------------------------------

const MINUTE = 60 * 1000, HOUR = 60 * MINUTE, DAY = 24 * HOUR;

test('time left on a running challenge, in the largest two units', () => {
  const endsIn = (ms) => challenge({ state: 'active', endsAt: asBackendStamp(serverNow() + ms + 1000) });

  expect(describeTime(endsIn(12 * MINUTE))).toBe('12m left');
  expect(describeTime(endsIn(3 * HOUR + 15 * MINUTE))).toBe('3h 15m left');
  expect(describeTime(endsIn(2 * DAY + 4 * HOUR + 30 * MINUTE))).toBe('2d 4h left');
  expect(describeTime(challenge({ state: 'active', endsAt: asBackendStamp(serverNow() + 20 * 1000) }))).toBe('less than a minute left');
});

test('when a challenge starts, and how long ago one ended', () => {
  expect(describeTime(challenge({ state: 'upcoming', startsAt: asBackendStamp(serverNow() + 2 * HOUR + 5 * MINUTE + 1000) }))).toBe('Starts in 2h 5m');
  expect(describeTime(challenge({ state: 'finished', endsAt: asBackendStamp(serverNow() - 5 * MINUTE) }))).toBe('Ended 5m ago');
});

test('time left is measured from the backend\'s clock, on a tablet whose own clock is nine hours off', () => {
  // The backend's time arrives with every answer. Its stamps are written on the same
  // clock, so "3 hours left" is 3 hours whatever this tablet thinks the time is.
  noteServerTime({ headers: { get: (name) => (name === 'X-Server-Time' ? asBackendStamp(Date.now() + 9 * HOUR) : null) } });

  expect(describeTime(challenge({ state: 'active', endsAt: asBackendStamp(serverNow() + 3 * HOUR + 1000) }))).toBe('3h 0m left');
});

// ---------------------------------------------------------------------------
// The form
// ---------------------------------------------------------------------------

test('a new form starts now and ends at the end of today, by the backend\'s clock', () => {
  const now = asBackendStamp(serverNow());
  const form = emptyForm();

  expect(form.measure).toBe('total_sales');
  expect(form.startsAt).toBe(now.slice(0, 16));
  expect(form.endsAt).toBe(`${now.slice(0, 10)}T23:59`);
  expect(form.name).toBe('');
  expect(form.target).toBe('');
});

test('the request for total sales carries none of the other measures\' fields', () => {
  const form = { ...emptyForm(), name: 'Big night', target: '500', counts: 'category:Sweet', minChecks: '9', startsAt: '2026-11-18T17:00', endsAt: '2026-11-18T23:00', prize: '$20' };

  expect(requestFrom(form)).toEqual({
    name: 'Big night', description: '', measure: 'total_sales', target: 500,
    menuCategory: null, menuItemId: null, minChecks: null,
    startsAt: '2026-11-18T17:00:00', endsAt: '2026-11-18T23:00:00', prize: '$20'
  });
});

test('an items challenge sends the category, or the one item, that was picked', () => {
  const items = { ...emptyForm(), measure: 'items_sold', target: '15' };

  expect(requestFrom({ ...items, counts: 'category:Sweet' })).toMatchObject({ menuCategory: 'Sweet', menuItemId: null, minChecks: null });
  expect(requestFrom({ ...items, counts: 'item:41' })).toMatchObject({ menuCategory: null, menuItemId: 41 });
  // Nothing picked: both empty, and the backend says so in words
  expect(requestFrom({ ...items, counts: '' })).toMatchObject({ menuCategory: null, menuItemId: null });
});

test('a check average sends the minimum number of checks', () => {
  expect(requestFrom({ ...emptyForm(), measure: 'check_average', target: '40', minChecks: '5' })).toMatchObject({ minChecks: 5, target: 40 });
});

test('an empty target is sent as nothing, so the backend can say a target is needed', () => {
  expect(requestFrom({ ...emptyForm(), target: '' }).target).toBeNull();
});

test('a challenge fills the form in for changing, and comes back out the same', () => {
  const pastry = challenge({ name: 'Pastry push', description: 'Sell the case', measure: 'items_sold', target: 20, menuItemId: 41, menuItemName: 'Daily Pastry',
    startsAt: '2026-11-18T17:00:00', endsAt: '2026-11-18T23:30:00', prize: 'First cut Friday' });

  const form = formFrom(pastry);
  expect(form).toMatchObject({ name: 'Pastry push', description: 'Sell the case', measure: 'items_sold', target: '20', counts: 'item:41',
    startsAt: '2026-11-18T17:00', endsAt: '2026-11-18T23:30', prize: 'First cut Friday' });
  expect(requestFrom(form)).toMatchObject({ name: 'Pastry push', measure: 'items_sold', target: 20, menuItemId: 41, menuCategory: null,
    startsAt: '2026-11-18T17:00:00', endsAt: '2026-11-18T23:30:00' });

  expect(formFrom(challenge({ measure: 'items_sold', menuCategory: 'Sweet' })).counts).toBe('category:Sweet');
  expect(formFrom(challenge({ measure: 'check_average', minChecks: 5 })).minChecks).toBe('5');
  expect(formFrom(challenge({ description: null, prize: null }))).toMatchObject({ description: '', prize: '' });
});

test('what can be counted comes from the menu: its categories, then its items, each once and in order', () => {
  const menu = [
    { menuItemId: 3, name: 'Pie', category: 'Sweet' },
    { menuItemId: 1, name: 'Burger', category: 'Savory' },
    { menuItemId: 2, name: 'Cake', category: 'Sweet' }
  ];

  expect(countableFrom(menu)).toEqual({
    categories: ['Savory', 'Sweet'],
    items: [{ menuItemId: 1, name: 'Burger' }, { menuItemId: 2, name: 'Cake' }, { menuItemId: 3, name: 'Pie' }]
  });
  expect(countableFrom([])).toEqual({ categories: [], items: [] });
});
