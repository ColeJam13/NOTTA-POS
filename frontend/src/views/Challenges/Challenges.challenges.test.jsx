import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Challenges, { CHALLENGES_REFRESH_MS } from './Challenges';
import { StaffContext } from '../../context/StaffContext';
import { asBackendStamp, serverNow } from '../../services/serverClock';

/**
 * Tests for the challenges on the Challenges screen. (The leaderboard under them is
 * tested in Challenges.test.jsx.)
 *
 * WHAT: Loads the screen against a fake backend that holds a list of challenges and
 *       answers the same requests the real one does, and checks what a server sees,
 *       what a manager sees, and what each button sends.
 * WHY:  The screen used to show three invented challenges with invented progress, a
 *       CLAIM REWARD button that did nothing, and START CHALLENGE buttons for
 *       challenges nobody had made. Now every card is the backend's, progress is the
 *       backend's number, nobody "starts" a challenge, and only a manager's screen has
 *       the buttons that change anything.
 *
 * The fake backend works the same way as in Challenges.test.jsx: global.fetch is
 * replaced with a function that returns canned JSON and records what was asked.
 */

const HOUR = 60 * 60 * 1000;
const inHours = (hours) => asBackendStamp(serverNow() + hours * HOUR);

// One person's line in a challenge, in the shape the backend sends (ChallengeView.Participant)
const person = (staffId, name, progress, fields = {}) =>
  ({ staffId, name, progress, checksClosed: 3, meetsTarget: false, completed: false, awardedAt: null, awardedByName: null, ...fields });

// A challenge in the shape the backend sends (ChallengeView): running, reach $500 in sales, prize $20
const challenge = (challengeId, name, fields = {}) => ({
  challengeId, name, description: null, measure: 'total_sales', target: 500,
  menuCategory: null, menuItemId: null, menuItemName: null, minChecks: null,
  startsAt: inHours(-1), endsAt: inHours(3.26), prize: '$20', createdByName: 'Morgan', state: 'active', participants: [], ...fields
});

const SARAH = { staffId: 7, name: 'Sarah', role: 'server' };
const MORGAN = { staffId: 2, name: 'Morgan', role: 'manager' };

const MENU = [
  { menuItemId: 41, name: 'Daily Pastry', category: 'Sweet', price: 5 },
  { menuItemId: 12, name: 'Burger', category: 'Savory', price: 20 },
  { menuItemId: 42, name: 'Cake', category: 'Sweet', price: 8 }
];

let challengesOnBackend;    // What GET /api/challenges answers with
let requests;               // Every request that changes something: [{ method, path, body }]
let asks;                   // How many times the list was asked for
let refuseWith;             // { status, body } to answer the next change with, or null to accept it
let challengesAreDown;

const answer = (data, status = 200) => ({ ok: status < 400, status, headers: { get: () => null }, json: async () => data });

beforeEach(() => {
  challengesOnBackend = [];
  requests = [];
  asks = 0;
  refuseWith = null;
  challengesAreDown = false;

  global.fetch = vi.fn(async (url, options = {}) => {
    const path = url.replace('http://localhost:8080', '');
    const method = options.method || 'GET';

    if (method === 'GET') {
      if (path === '/api/notifications') return answer([]);
      if (path === '/api/menu-items') return answer(MENU);
      if (path.startsWith('/api/reports/staff-sales?')) return answer({ staff: [] });
      if (path === '/api/challenges') {
        asks++;
        return challengesAreDown ? answer({ message: 'Something went wrong on the server' }, 500) : answer(challengesOnBackend);
      }
      throw new Error(`Unexpected request in test: ${path}`);
    }

    // Anything that changes a challenge
    requests.push({ method, path, body: options.body ? JSON.parse(options.body) : undefined });
    if (refuseWith) {
      const refusal = refuseWith;
      refuseWith = null;
      return answer(refusal.body, refusal.status);
    }
    const id = Number((path.match(/^\/api\/challenges\/(\d+)/) || [])[1]);
    if (method === 'POST' && path === '/api/challenges') {
      const made = challenge(99, requests[requests.length - 1].body.name, requests[requests.length - 1].body);
      challengesOnBackend = [...challengesOnBackend, { ...made, state: 'active', participants: [] }];
      return answer(made, 201);
    }
    if (method === 'PUT') {
      challengesOnBackend = challengesOnBackend.map(c => (c.challengeId === id ? { ...c, ...requests[requests.length - 1].body } : c));
      return answer(challengesOnBackend.find(c => c.challengeId === id));
    }
    if (method === 'POST' && path.endsWith('/end')) {
      challengesOnBackend = challengesOnBackend.map(c => (c.challengeId === id ? { ...c, state: 'finished', endsAt: inHours(0) } : c));
      return answer(challengesOnBackend.find(c => c.challengeId === id));
    }
    if (method === 'DELETE') {
      challengesOnBackend = challengesOnBackend.filter(c => c.challengeId !== id);
      return { ok: true, status: 204, headers: { get: () => null }, json: async () => { throw new Error('no body'); } };
    }
    const award = path.match(/^\/api\/challenges\/(\d+)\/awards\/(\d+)$/);
    if (method === 'POST' && award) {
      challengesOnBackend = challengesOnBackend.map(c => (c.challengeId !== id ? c : {
        ...c, participants: c.participants.map(p => (p.staffId === Number(award[2]) ? { ...p, awardedAt: inHours(0), awardedByName: 'Morgan' } : p))
      }));
      return answer(challengesOnBackend.find(c => c.challengeId === id));
    }
    throw new Error(`Unexpected request in test: ${method} ${path}`);
  });

  vi.spyOn(window, 'confirm').mockReturnValue(true);       // The manager says yes, unless a test says otherwise
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

const renderAs = (staff) => render(
  <StaffContext.Provider value={{ staff, signOut: () => {} }}>
    <Challenges setCurrentView={vi.fn()} setSelectedTable={vi.fn()} />
  </StaffContext.Provider>
);

// A challenge's card, found by its name (each card is labelled with it)
const card = (name) => screen.findByRole('article', { name });
const section = (name) => screen.getByRole('region', { name });

// ---------------------------------------------------------------------------
// No challenges
// ---------------------------------------------------------------------------

test('with no challenges the screen says so, and only a manager is offered the button to make one', async () => {
  const { unmount } = renderAs(SARAH);
  expect(await screen.findByText('No challenges yet.')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'NEW CHALLENGE' })).not.toBeInTheDocument();
  unmount();

  renderAs(MORGAN);
  expect(await screen.findByText('No challenges yet.')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'NEW CHALLENGE' })).toBeInTheDocument();
});

// ---------------------------------------------------------------------------
// What everyone sees
// ---------------------------------------------------------------------------

test('a running challenge shows what to do, your own progress toward the target, the time left, and the prize', async () => {
  challengesOnBackend = [challenge(1, 'Big night', { description: 'Friday dinner only', participants: [person(7, 'Sarah', 340.5), person(8, 'Dan', 499)] })];
  renderAs(SARAH);

  const bigNight = within(await card('Big night'));
  expect(bigNight.getByText('Reach $500.00 in sales')).toBeInTheDocument();
  expect(bigNight.getByText('Friday dinner only')).toBeInTheDocument();
  expect(bigNight.getByText('$340.50 / $500.00')).toBeInTheDocument();          // Sarah's own, not Dan's
  expect(bigNight.getByText('68%')).toBeInTheDocument();
  expect(bigNight.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '68');
  expect(bigNight.getByText('IN PROGRESS')).toBeInTheDocument();
  expect(bigNight.getByText('Prize: $20')).toBeInTheDocument();
  expect(bigNight.getByText('3h 15m left')).toBeInTheDocument();
  expect(within(section('Active challenges')).getByRole('article', { name: 'Big night' })).toBeInTheDocument();
});

test('your progress is found by staff ID, and someone who has closed no check yet is at zero', async () => {
  // Two people called Sarah are in it; the one signed in is staff 7
  challengesOnBackend = [challenge(1, 'Big night', { participants: [person(31, 'Sarah', 480), person(7, 'Sarah', 120)] })];
  const { unmount } = renderAs(SARAH);
  expect(within(await card('Big night')).getByText('$120.00 / $500.00')).toBeInTheDocument();
  unmount();

  renderAs({ staffId: 99, name: 'Harper', role: 'server' });
  const bigNight = within(await card('Big night'));
  expect(bigNight.getByText('$0.00 / $500.00')).toBeInTheDocument();
  expect(bigNight.getByText('0%')).toBeInTheDocument();
  expect(bigNight.getByText('IN PROGRESS')).toBeInTheDocument();
});

test('reaching the target reads COMPLETE with a full bar, and says the prize is waiting', async () => {
  challengesOnBackend = [challenge(1, 'Big night', { participants: [person(7, 'Sarah', 612, { meetsTarget: true, completed: true })] })];
  renderAs(SARAH);

  const bigNight = within(await card('Big night'));
  expect(bigNight.getByText('COMPLETE')).toBeInTheDocument();
  expect(bigNight.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100');
  expect(bigNight.getByText('Prize waiting')).toBeInTheDocument();
});

test('an items challenge counts in whole items and names what counts', async () => {
  challengesOnBackend = [
    challenge(1, 'Sweet tooth', { measure: 'items_sold', target: 15, menuCategory: 'Sweet', participants: [person(7, 'Sarah', 12)] }),
    challenge(2, 'Pastry push', { measure: 'items_sold', target: 20, menuItemId: 41, menuItemName: 'Daily Pastry', participants: [] })
  ];
  renderAs(SARAH);

  expect(within(await card('Sweet tooth')).getByText('Sell 15 from Sweet')).toBeInTheDocument();
  expect(within(await card('Sweet tooth')).getByText('12 / 15')).toBeInTheDocument();
  expect(within(await card('Pastry push')).getByText('Sell 20 of Daily Pastry')).toBeInTheDocument();
  expect(within(await card('Pastry push')).getByText('0 / 20')).toBeInTheDocument();
});

test('a check average at the target while the challenge runs is ON TRACK, with how many checks are behind it', async () => {
  // The backend says "meets the target" but not "completed": the average can still fall
  challengesOnBackend = [challenge(1, 'High average', { measure: 'check_average', target: 40, minChecks: 5,
    participants: [person(7, 'Sarah', 45, { checksClosed: 6, meetsTarget: true, completed: false })] })];
  renderAs(SARAH);

  const average = within(await card('High average'));
  expect(average.getByText('Hold a check average of at least $40.00, over 5 or more checks')).toBeInTheDocument();
  expect(average.getByText('ON TRACK')).toBeInTheDocument();
  expect(average.getByText('6 of the 5 checks needed')).toBeInTheDocument();
  expect(average.queryByText('Prize waiting')).not.toBeInTheDocument();
});

test('finished challenges say whether you completed them, and whether the prize is waiting or awarded', async () => {
  const ended = { state: 'finished', startsAt: inHours(-30), endsAt: asBackendStamp(serverNow() - 5 * 60 * 1000) };
  challengesOnBackend = [
    challenge(1, 'Awarded one', { ...ended, participants: [person(7, 'Sarah', 600, { meetsTarget: true, completed: true, awardedAt: inHours(-1), awardedByName: 'Morgan' })] }),
    challenge(2, 'Waiting one', { ...ended, participants: [person(7, 'Sarah', 600, { meetsTarget: true, completed: true })] }),
    challenge(3, 'Missed one', { ...ended, participants: [person(7, 'Sarah', 100)] })
  ];
  renderAs(SARAH);

  expect(within(await card('Awarded one')).getByText('COMPLETED')).toBeInTheDocument();
  expect(within(await card('Awarded one')).getByText('Prize awarded by Morgan')).toBeInTheDocument();
  expect(within(await card('Awarded one')).getByText('Ended 5m ago')).toBeInTheDocument();
  expect(within(await card('Waiting one')).getByText('Prize waiting')).toBeInTheDocument();
  expect(within(await card('Missed one')).getByText('NOT COMPLETED')).toBeInTheDocument();
  expect(within(await card('Missed one')).queryByText(/Prize (waiting|awarded)/)).not.toBeInTheDocument();

  // All three are under FINISHED, and ACTIVE says it has none
  expect(within(section('Finished challenges')).getAllByRole('article')).toHaveLength(3);
  expect(within(section('Active challenges')).getByText('No active challenges.')).toBeInTheDocument();
});

test('a challenge set for later is listed as coming up, with when it starts and no progress yet', async () => {
  challengesOnBackend = [
    challenge(1, 'Running', {}),
    challenge(2, 'Tomorrow', { state: 'upcoming', startsAt: asBackendStamp(serverNow() + 26 * HOUR + 1000), endsAt: inHours(50) })
  ];
  renderAs(SARAH);

  const tomorrow = within(await card('Tomorrow'));
  expect(tomorrow.getByText('NOT STARTED')).toBeInTheDocument();
  expect(tomorrow.getByText('Starts in 1d 2h')).toBeInTheDocument();
  expect(tomorrow.queryByRole('progressbar')).not.toBeInTheDocument();
  expect(within(section('Challenges coming up')).getByRole('article', { name: 'Tomorrow' })).toBeInTheDocument();
});

test('there is no COMING UP section when nothing is coming, and no prize line when none was set', async () => {
  challengesOnBackend = [challenge(1, 'No prize', { prize: null, participants: [person(7, 'Sarah', 600, { meetsTarget: true, completed: true })] })];
  renderAs(SARAH);

  const noPrize = within(await card('No prize'));
  expect(screen.queryByRole('region', { name: 'Challenges coming up' })).not.toBeInTheDocument();
  expect(noPrize.queryByText(/Prize/)).not.toBeInTheDocument();
});

test('a server sees nobody else\'s progress and no button that changes anything', async () => {
  challengesOnBackend = [challenge(1, 'Big night', { participants: [person(7, 'Sarah', 600, { meetsTarget: true, completed: true }), person(8, 'Dan', 200)] })];
  renderAs(SARAH);

  const bigNight = within(await card('Big night'));
  expect(bigNight.queryByText('Everyone\'s progress')).not.toBeInTheDocument();
  expect(bigNight.queryByText('Dan')).not.toBeInTheDocument();
  for (const name of ['EDIT', 'END NOW', 'DELETE', 'MARK AWARDED', 'NEW CHALLENGE']) {
    expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
  }
  // And nothing of the old invented screen: nobody starts or claims anything
  const page = document.querySelector('.challenges-page').textContent;
  for (const invented of ['START CHALLENGE', 'CLAIM REWARD', 'CLAIMED', 'APPETIZER CHAMPION', 'WINE PAIRING', 'tip']) {
    expect(page).not.toContain(invented);
  }
});

test('the challenges are asked for again every 15 seconds, and progress follows', async () => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  challengesOnBackend = [challenge(1, 'Big night', { participants: [person(7, 'Sarah', 340.5)] })];
  renderAs(SARAH);
  expect(within(await card('Big night')).getByText('$340.50 / $500.00')).toBeInTheDocument();
  expect(asks).toBe(1);

  // A check of hers is paid at another terminal
  challengesOnBackend = [challenge(1, 'Big night', { participants: [person(7, 'Sarah', 520, { meetsTarget: true, completed: true })] })];
  await act(async () => { await vi.advanceTimersByTimeAsync(CHALLENGES_REFRESH_MS); });

  expect(asks).toBe(2);
  expect(within(await card('Big night')).getByText('$520.00 / $500.00')).toBeInTheDocument();
  expect(within(await card('Big night')).getByText('COMPLETE')).toBeInTheDocument();
});

test('when the challenges cannot be loaded the screen says so, and does not claim there are none', async () => {
  vi.spyOn(console, 'error').mockImplementation(() => {});
  challengesAreDown = true;
  renderAs(SARAH);

  expect(await screen.findByText('The challenges could not be loaded. They will be tried again in a moment.')).toBeInTheDocument();
  expect(screen.queryByText('No challenges yet.')).not.toBeInTheDocument();
  // The leaderboard under it is a separate request and is still there
  expect(await screen.findByText('No sales yet for this period.')).toBeInTheDocument();
});

// ---------------------------------------------------------------------------
// What a manager sees and does
// ---------------------------------------------------------------------------

test('a manager sees everyone\'s progress, and MARK AWARDED only for someone who completed it and has not been awarded', async () => {
  challengesOnBackend = [challenge(1, 'Big night', { participants: [
    person(7, 'Sarah', 612, { meetsTarget: true, completed: true }),
    person(9, 'Kit', 540, { meetsTarget: true, completed: true, awardedAt: inHours(-1), awardedByName: 'Alex' }),
    person(8, 'Dan', 200)
  ] })];
  renderAs(MORGAN);

  const bigNight = within(await card('Big night'));
  expect(bigNight.getByText('Everyone\'s progress')).toBeInTheDocument();
  const line = (name) => within(bigNight.getByText(name).closest('li'));

  expect(line('Sarah').getByText('$612.00 / $500.00')).toBeInTheDocument();
  expect(line('Sarah').getByText('COMPLETE')).toBeInTheDocument();
  expect(line('Sarah').getByRole('button', { name: 'MARK AWARDED' })).toBeInTheDocument();

  expect(line('Kit').getByText('Awarded by Alex')).toBeInTheDocument();
  expect(line('Kit').queryByRole('button')).not.toBeInTheDocument();

  expect(line('Dan').getByText('$200.00 / $500.00')).toBeInTheDocument();
  expect(line('Dan').getByText('IN PROGRESS')).toBeInTheDocument();
  expect(line('Dan').queryByRole('button')).not.toBeInTheDocument();

  // The manager's own progress is still at the top of the card: nothing, they sold nothing
  expect(bigNight.getByText('$0.00 / $500.00')).toBeInTheDocument();
});

test('a manager on a challenge nobody has sold toward is told so', async () => {
  challengesOnBackend = [challenge(1, 'Big night', {})];
  renderAs(MORGAN);

  expect(within(await card('Big night')).getByText('Nobody has closed a check in this challenge yet.')).toBeInTheDocument();
});

test('MARK AWARDED asks first, records it, and the line then says who awarded it', async () => {
  challengesOnBackend = [challenge(1, 'Big night', { participants: [person(7, 'Sarah', 612, { meetsTarget: true, completed: true })] })];
  renderAs(MORGAN);

  await userEvent.click(within(await card('Big night')).getByRole('button', { name: 'MARK AWARDED' }));

  expect(window.confirm).toHaveBeenCalledWith('Mark the prize of "Big night" as handed to Sarah? This cannot be undone.');
  await waitFor(() => expect(requests).toEqual([{ method: 'POST', path: '/api/challenges/1/awards/7', body: undefined }]));
  expect(await within(await card('Big night')).findByText('Awarded by Morgan')).toBeInTheDocument();
  expect(within(await card('Big night')).queryByRole('button', { name: 'MARK AWARDED' })).not.toBeInTheDocument();
});

test('a manager who answers no to the question changes nothing', async () => {
  window.confirm.mockReturnValue(false);
  challengesOnBackend = [challenge(1, 'Big night', { participants: [person(7, 'Sarah', 612, { meetsTarget: true, completed: true })] })];
  renderAs(MORGAN);
  const bigNight = within(await card('Big night'));

  await userEvent.click(bigNight.getByRole('button', { name: 'MARK AWARDED' }));
  await userEvent.click(bigNight.getByRole('button', { name: 'END NOW' }));
  await userEvent.click(bigNight.getByRole('button', { name: 'DELETE' }));

  expect(window.confirm).toHaveBeenCalledTimes(3);
  expect(requests).toEqual([]);
});

test('END NOW moves a running challenge to FINISHED, and is only offered on a running one', async () => {
  challengesOnBackend = [
    challenge(1, 'Running', {}),
    challenge(2, 'Ended', { state: 'finished', endsAt: inHours(-1) }),
    challenge(3, 'Later', { state: 'upcoming', startsAt: inHours(5), endsAt: inHours(9) })
  ];
  renderAs(MORGAN);

  expect(within(await card('Ended')).queryByRole('button', { name: 'END NOW' })).not.toBeInTheDocument();
  expect(within(await card('Later')).queryByRole('button', { name: 'END NOW' })).not.toBeInTheDocument();
  await userEvent.click(within(await card('Running')).getByRole('button', { name: 'END NOW' }));

  expect(window.confirm).toHaveBeenCalledWith('End "Running" now? Checks paid after this will not count toward it.');
  await waitFor(() => expect(requests).toEqual([{ method: 'POST', path: '/api/challenges/1/end', body: undefined }]));
  await waitFor(() => expect(within(section('Finished challenges')).getByRole('article', { name: 'Running' })).toBeInTheDocument());
});

test('DELETE removes the challenge from the screen', async () => {
  challengesOnBackend = [challenge(1, 'Big night', {}), challenge(2, 'Other', {})];
  renderAs(MORGAN);

  await userEvent.click(within(await card('Big night')).getByRole('button', { name: 'DELETE' }));

  expect(window.confirm).toHaveBeenCalledWith('Delete "Big night"? This cannot be undone.');
  await waitFor(() => expect(requests).toEqual([{ method: 'DELETE', path: '/api/challenges/1', body: undefined }]));
  await waitFor(() => expect(screen.queryByRole('article', { name: 'Big night' })).not.toBeInTheDocument());
  expect(screen.getByRole('article', { name: 'Other' })).toBeInTheDocument();
});

test('when the backend refuses, its own words are shown and the challenge stays', async () => {
  challengesOnBackend = [challenge(1, 'Big night', {})];
  refuseWith = { status: 409, body: { message: 'A prize has been marked awarded on this challenge. It stays as a record and cannot be deleted.' } };
  renderAs(MORGAN);

  await userEvent.click(within(await card('Big night')).getByRole('button', { name: 'DELETE' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('A prize has been marked awarded on this challenge. It stays as a record and cannot be deleted.');
  expect(screen.getByRole('article', { name: 'Big night' })).toBeInTheDocument();
});

// ---------------------------------------------------------------------------
// The form
// ---------------------------------------------------------------------------

const openNewForm = async () => {
  await screen.findByText('No challenges yet.');
  await userEvent.click(screen.getByRole('button', { name: 'NEW CHALLENGE' }));
  return within(screen.getByRole('dialog', { name: 'New challenge' }));
};

test('NEW CHALLENGE opens a form, and CREATE sends what was typed and shows the new challenge', async () => {
  renderAs(MORGAN);
  const form = await openNewForm();

  await userEvent.type(form.getByLabelText('Name'), 'Big night');
  await userEvent.type(form.getByLabelText('A line about it (optional)'), 'Friday dinner only');
  await userEvent.type(form.getByLabelText('Sales to reach ($)'), '500');
  fireEvent.change(form.getByLabelText('Starts'), { target: { value: '2026-11-20T17:00' } });
  fireEvent.change(form.getByLabelText('Ends'), { target: { value: '2026-11-20T23:30' } });
  await userEvent.type(form.getByLabelText('Prize, in your own words (optional)'), '$20');
  await userEvent.click(form.getByRole('button', { name: 'CREATE CHALLENGE' }));

  await waitFor(() => expect(requests).toEqual([{ method: 'POST', path: '/api/challenges', body: {
    name: 'Big night', description: 'Friday dinner only', measure: 'total_sales', target: 500,
    menuCategory: null, menuItemId: null, minChecks: null,
    startsAt: '2026-11-20T17:00:00', endsAt: '2026-11-20T23:30:00', prize: '$20'
  } }]));
  // The form closes and the list is read again
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  expect(await card('Big night')).toBeInTheDocument();
});

test('items sold asks which items count, offering the menu\'s own categories and items', async () => {
  renderAs(MORGAN);
  const form = await openNewForm();

  // Total sales is the measure a new form starts on: no such question yet
  expect(form.queryByLabelText('Which items count')).not.toBeInTheDocument();

  await userEvent.selectOptions(form.getByLabelText('What it measures'), 'Items sold');
  const which = await form.findByLabelText('Which items count');
  await waitFor(() => expect(within(which).getByRole('option', { name: 'Daily Pastry' })).toBeInTheDocument());
  expect(within(which).getAllByRole('option').map(option => option.textContent))
    .toEqual(['Pick a category or an item', 'Savory', 'Sweet', 'Burger', 'Cake', 'Daily Pastry']);

  await userEvent.type(form.getByLabelText('Name'), 'Sweet tooth');
  await userEvent.type(form.getByLabelText('How many to sell'), '15');
  await userEvent.selectOptions(which, 'Sweet');
  await userEvent.click(form.getByRole('button', { name: 'CREATE CHALLENGE' }));

  await waitFor(() => expect(requests).toHaveLength(1));
  expect(requests[0].body).toMatchObject({ measure: 'items_sold', target: 15, menuCategory: 'Sweet', menuItemId: null, minChecks: null });
});

test('one item can be picked in place of a category, and a check average asks for a number of checks', async () => {
  renderAs(MORGAN);
  const form = await openNewForm();
  await userEvent.type(form.getByLabelText('Name'), 'x');

  await userEvent.selectOptions(form.getByLabelText('What it measures'), 'Items sold');
  await waitFor(() => expect(form.getByRole('option', { name: 'Daily Pastry' })).toBeInTheDocument());
  await userEvent.type(form.getByLabelText('How many to sell'), '20');
  await userEvent.selectOptions(form.getByLabelText('Which items count'), 'Daily Pastry');
  await userEvent.click(form.getByRole('button', { name: 'CREATE CHALLENGE' }));
  await waitFor(() => expect(requests).toHaveLength(1));
  expect(requests[0].body).toMatchObject({ measure: 'items_sold', menuItemId: 41, menuCategory: null });

  // A second one: a check average
  await userEvent.click(screen.getByRole('button', { name: 'NEW CHALLENGE' }));
  const second = within(screen.getByRole('dialog', { name: 'New challenge' }));
  await userEvent.type(second.getByLabelText('Name'), 'High average');
  await userEvent.selectOptions(second.getByLabelText('What it measures'), 'Check average');
  expect(second.queryByLabelText('Which items count')).not.toBeInTheDocument();
  await userEvent.type(second.getByLabelText('Average to hold ($)'), '40');
  const checks = second.getByLabelText('Checks needed before an average counts');
  await userEvent.clear(checks);
  await userEvent.type(checks, '5');
  await userEvent.click(second.getByRole('button', { name: 'CREATE CHALLENGE' }));

  await waitFor(() => expect(requests).toHaveLength(2));
  expect(requests[1].body).toMatchObject({ measure: 'check_average', target: 40, minChecks: 5, menuCategory: null, menuItemId: null });
});

test('when the backend refuses a challenge its words are shown in the form, which stays open with what was typed', async () => {
  refuseWith = { status: 400, body: { message: 'The end has to be after the start.' } };
  renderAs(MORGAN);
  const form = await openNewForm();

  await userEvent.type(form.getByLabelText('Name'), 'Backwards');
  await userEvent.type(form.getByLabelText('Sales to reach ($)'), '500');
  await userEvent.click(form.getByRole('button', { name: 'CREATE CHALLENGE' }));

  expect(await form.findByRole('alert')).toHaveTextContent('The end has to be after the start.');
  expect(form.getByLabelText('Name')).toHaveValue('Backwards');

  // Typing again clears the old message
  await userEvent.type(form.getByLabelText('Name'), '!');
  expect(form.queryByRole('alert')).not.toBeInTheDocument();
});

test('a refusal that names fields shows each field\'s message', async () => {
  refuseWith = { status: 400, body: { message: 'Validation failed', fieldErrors: { target: 'The target has to be more than zero', name: 'The name can be 60 characters at most' } } };
  renderAs(MORGAN);
  const form = await openNewForm();

  await userEvent.type(form.getByLabelText('Name'), 'x');
  await userEvent.type(form.getByLabelText('Sales to reach ($)'), '5');
  await userEvent.click(form.getByRole('button', { name: 'CREATE CHALLENGE' }));

  const alert = await form.findByRole('alert');
  expect(alert).toHaveTextContent('The target has to be more than zero');
  expect(alert).toHaveTextContent('The name can be 60 characters at most');
});

test('EDIT opens the form filled in, and SAVE CHANGES sends the whole challenge to that challenge', async () => {
  challengesOnBackend = [challenge(4, 'Sweet tooth', { description: 'Pie and cake', measure: 'items_sold', target: 15, menuCategory: 'Sweet',
    startsAt: '2026-11-20T17:00:00', endsAt: '2026-11-20T23:30:00', prize: 'First cut Friday' })];
  renderAs(MORGAN);

  await userEvent.click(within(await card('Sweet tooth')).getByRole('button', { name: 'EDIT' }));
  const form = within(screen.getByRole('dialog', { name: 'Change challenge' }));

  expect(form.getByLabelText('Name')).toHaveValue('Sweet tooth');
  expect(form.getByLabelText('A line about it (optional)')).toHaveValue('Pie and cake');
  expect(form.getByLabelText('What it measures')).toHaveValue('items_sold');
  expect(form.getByLabelText('How many to sell')).toHaveValue(15);
  expect(form.getByLabelText('Starts')).toHaveValue('2026-11-20T17:00');
  expect(form.getByLabelText('Ends')).toHaveValue('2026-11-20T23:30');
  expect(form.getByLabelText('Prize, in your own words (optional)')).toHaveValue('First cut Friday');
  await waitFor(() => expect(form.getByLabelText('Which items count')).toHaveValue('category:Sweet'));

  const target = form.getByLabelText('How many to sell');
  await userEvent.clear(target);
  await userEvent.type(target, '20');
  await userEvent.click(form.getByRole('button', { name: 'SAVE CHANGES' }));

  await waitFor(() => expect(requests).toEqual([{ method: 'PUT', path: '/api/challenges/4', body: {
    name: 'Sweet tooth', description: 'Pie and cake', measure: 'items_sold', target: 20,
    menuCategory: 'Sweet', menuItemId: null, minChecks: null,
    startsAt: '2026-11-20T17:00:00', endsAt: '2026-11-20T23:30:00', prize: 'First cut Friday'
  } }]));
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  expect(await within(await card('Sweet tooth')).findByText('Sell 20 from Sweet')).toBeInTheDocument();
});

test('a challenge on an item that has left the menu can still be opened and saved', async () => {
  // Item 77 is not in GET /api/menu-items any more (it was deactivated)
  challengesOnBackend = [challenge(4, 'Old special', { measure: 'items_sold', target: 10, menuItemId: 77, menuItemName: 'Old Special' })];
  renderAs(MORGAN);

  await userEvent.click(within(await card('Old special')).getByRole('button', { name: 'EDIT' }));
  const form = within(screen.getByRole('dialog', { name: 'Change challenge' }));

  await waitFor(() => expect(form.getByRole('option', { name: 'Old Special' })).toBeInTheDocument());
  expect(form.getByLabelText('Which items count')).toHaveValue('item:77');
  await userEvent.click(form.getByRole('button', { name: 'SAVE CHANGES' }));

  await waitFor(() => expect(requests).toHaveLength(1));
  expect(requests[0].body).toMatchObject({ menuItemId: 77, menuCategory: null });
});

test('CANCEL closes the form and sends nothing', async () => {
  renderAs(MORGAN);
  const form = await openNewForm();
  await userEvent.type(form.getByLabelText('Name'), 'Never mind');

  await userEvent.click(form.getByRole('button', { name: 'CANCEL' }));

  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  expect(requests).toEqual([]);
});
