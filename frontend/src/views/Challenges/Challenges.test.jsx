import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Challenges, { LEADERBOARD_REFRESH_MS } from './Challenges';
import Leaderboard from './Leaderboard';
import { StaffContext } from '../../context/StaffContext';

/**
 * Tests for the Challenges screen and its leaderboard.
 *
 * WHAT: Loads the screen against a fake backend that answers
 *       GET /api/reports/staff-sales?from=...&to=..., and checks what is drawn.
 * WHY:  The leaderboard used to be seven invented people typed into the screen, one of
 *       them called "YOU (CJ)", with an average tip beside each name. Now every row is
 *       the backend's, the signed-in person's own row is found by staff ID, and no tip
 *       is anywhere on the screen.
 *
 * The fake backend works the same way as in Financials.test.jsx: global.fetch is
 * replaced with a function that returns canned JSON and records what was asked.
 */

// "2026-11-18" for a Date, in this device's own time zone
const day = (date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

const SARAH = { staffId: 7, name: 'Sarah Miller', rank: 1, checksClosed: 12, sales: 1240.5, checkAverage: 103.38, biggestCheck: 310, itemsSold: 86 };
const DAN = { staffId: 8, name: 'Dan', rank: 2, checksClosed: 9, sales: 980, checkAverage: 108.89, biggestCheck: 240.25, itemsSold: 61 };
const MORGAN = { staffId: 2, name: 'Morgan', rank: 3, checksClosed: 1, sales: 45, checkAverage: 45, biggestCheck: 45, itemsSold: 3 };

let requests;         // every request path, in order
let salesOnBackend;   // the rows the fake backend answers with (or a function of the request path)
let backendIsDown;

const answer = (data) => ({ ok: true, status: 200, headers: { get: () => null }, json: async () => data });

beforeEach(() => {
  requests = [];
  salesOnBackend = [SARAH, DAN, MORGAN];
  backendIsDown = false;

  global.fetch = vi.fn(async (url) => {
    const path = url.replace('http://localhost:8080', '');
    requests.push(path);
    if (path === '/api/notifications') return answer([]);
    if (path.startsWith('/api/reports/staff-sales?')) {
      if (backendIsDown) return { ok: false, status: 500, headers: { get: () => null }, json: async () => ({ message: 'Something went wrong' }) };
      return answer({ staff: typeof salesOnBackend === 'function' ? salesOnBackend(path) : salesOnBackend });
    }
    throw new Error(`Unexpected request in test: ${path}`);
  });
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

// The screen as a given person sees it
const renderAs = (staff) => render(
  <StaffContext.Provider value={{ staff, signOut: () => {} }}>
    <Challenges setCurrentView={vi.fn()} setSelectedTable={vi.fn()} />
  </StaffContext.Provider>
);
const asDan = () => renderAs({ staffId: 8, name: 'Dan', role: 'bartender' });

// The leaderboard itself. Names are looked for in here, not on the whole screen: the
// signed-in person's name is also in the nav bar.
const board = () => within(document.querySelector('.leaderboard-table'));
const rowOf = (name) => board().getByText(name).closest('tr');
const salesRequests = () => requests.filter(path => path.startsWith('/api/reports/staff-sales'));

// ---------------------------------------------------------------------------
// What is drawn
// ---------------------------------------------------------------------------

test('each person the backend sends is a row, in the backend\'s order, with its five numbers', async () => {
  asDan();

  const sarah = within(await screen.findByText('Sarah Miller').then(cell => cell.closest('tr')));
  expect(sarah.getByText('1')).toBeInTheDocument();                 // Rank
  expect(sarah.getByText('$1240.50')).toBeInTheDocument();          // Sales
  expect(sarah.getByText('12')).toBeInTheDocument();                // Checks closed
  expect(sarah.getByText('$103.38')).toBeInTheDocument();           // Check average
  expect(sarah.getByText('$310.00')).toBeInTheDocument();           // Biggest check
  expect(sarah.getByText('86')).toBeInTheDocument();                // Items sold

  const names = [...document.querySelectorAll('.leaderboard-name')].map(cell => cell.firstChild.textContent);
  expect(names).toEqual(['Sarah Miller', 'Dan', 'Morgan']);

  // Every number has a column heading a screen reader can say
  const headings = screen.getAllByRole('columnheader').map(heading => heading.textContent);
  expect(headings).toEqual(['RANK', 'NAME', 'SALES', 'CHECKS', 'CHECK AVG', 'BIGGEST CHECK', 'ITEMS']);
});

test('the signed-in person\'s own row is marked, by staff ID', async () => {
  asDan();
  await screen.findByText('Sarah Miller');

  expect(rowOf('Dan')).toHaveClass('own');
  expect(within(rowOf('Dan')).getByText('YOU')).toBeInTheDocument();          // A word, not only a color
  expect(rowOf('Sarah Miller')).not.toHaveClass('own');
  expect(screen.getAllByText('YOU')).toHaveLength(1);
});

test('someone else with the same name is not marked as you', async () => {
  // Two people called Dan. The one signed in is staff 8; the other is staff 31.
  salesOnBackend = [{ ...SARAH }, { ...DAN, staffId: 31, rank: 2 }, { ...DAN, staffId: 8, rank: 3, sales: 500 }];
  asDan();
  await screen.findByText('Sarah Miller');

  const dans = board().getAllByText('Dan').map(cell => cell.closest('tr'));
  expect(dans.map(row => row.classList.contains('own'))).toEqual([false, true]);
});

test('someone who sold nothing this period sees the board with no row marked', async () => {
  renderAs({ staffId: 99, name: 'Harper', role: 'server' });
  await screen.findByText('Sarah Miller');

  expect(document.querySelectorAll('.leaderboard-row.own')).toHaveLength(0);
  expect(screen.queryByText('YOU')).not.toBeInTheDocument();
});

test('equal sales share a rank, exactly as the backend ranked them', async () => {
  // The backend sends 1, 1, 3. The screen prints those; it does not number the rows itself.
  salesOnBackend = [{ ...SARAH, rank: 1 }, { ...DAN, rank: 1, sales: 1240.5 }, { ...MORGAN, rank: 3 }];
  asDan();
  await screen.findByText('Sarah Miller');

  const ranks = [...document.querySelectorAll('.leaderboard-rank-number')].map(cell => cell.textContent);
  expect(ranks).toEqual(['1', '1', '3']);
});

test('the first three ranks get a medal and everyone gets a number', async () => {
  salesOnBackend = [SARAH, DAN, MORGAN, { staffId: 40, name: 'Kit', rank: 4, checksClosed: 1, sales: 12, checkAverage: 12, biggestCheck: 12, itemsSold: 1 }];
  asDan();
  await screen.findByText('Kit');

  const rankCells = [...document.querySelectorAll('.leaderboard-rank')];
  expect(rankCells.map(cell => cell.querySelector('svg') !== null)).toEqual([true, true, true, false]);
  // The number is there with or without a medal: the medal's color is not what says who is where
  expect(rankCells.map(cell => cell.querySelector('.leaderboard-rank-number').textContent)).toEqual(['1', '2', '3', '4']);
});

test('nobody sold anything: the screen says so', async () => {
  salesOnBackend = [];
  asDan();

  expect(await screen.findByText('No sales yet for this period.')).toBeInTheDocument();
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
});

test('there is no tip, no streak, and nothing invented on the screen', async () => {
  asDan();
  await screen.findByText('Sarah Miller');

  const page = document.querySelector('.challenges-page').textContent;
  expect(page).not.toMatch(/tip/i);
  expect(page).not.toMatch(/streak/i);
  // The names and challenges that were typed into the old screen
  for (const invented of ['MARCUS', 'YOU (CJ)', 'TAYLOR', 'APPETIZER CHAMPION', 'WINE PAIRING', 'HIGH ROLLER', 'CLAIM REWARD', 'START CHALLENGE']) {
    expect(page).not.toContain(invented);
  }
  expect(document.querySelector('.challenge-card')).toBeNull();
});

// ---------------------------------------------------------------------------
// The period
// ---------------------------------------------------------------------------

test('it opens on this week: Sunday through the last second of today', async () => {
  asDan();
  await screen.findByText('Sarah Miller');

  const now = new Date();
  const sunday = day(new Date(now.getFullYear(), now.getMonth(), now.getDate() - now.getDay()));
  expect(salesRequests()).toEqual([`/api/reports/staff-sales?from=${sunday}T00:00:00&to=${day(now)}T23:59:59`]);
  expect(screen.getByRole('button', { name: 'THIS WEEK' })).toHaveClass('active');
});

test('the period is picked with the same four buttons as Financials, and each asks for its own period', async () => {
  asDan();
  await screen.findByText('Sarah Miller');
  expect([...document.querySelectorAll('.period-btn')].map(button => button.textContent)).toEqual(['TODAY', 'THIS WEEK', 'THIS MONTH', 'CUSTOM']);

  const now = new Date();
  const today = day(now);

  await userEvent.click(screen.getByRole('button', { name: 'TODAY' }));
  await waitFor(() => expect(salesRequests()).toContain(`/api/reports/staff-sales?from=${today}T00:00:00&to=${today}T23:59:59`));

  await userEvent.click(screen.getByRole('button', { name: 'THIS MONTH' }));
  const firstDay = day(new Date(now.getFullYear(), now.getMonth(), 1));
  const lastDay = day(new Date(now.getFullYear(), now.getMonth() + 1, 0));
  await waitFor(() => expect(salesRequests()).toContain(`/api/reports/staff-sales?from=${firstDay}T00:00:00&to=${lastDay}T23:59:59`));

  await userEvent.click(screen.getByRole('button', { name: 'CUSTOM' }));
  fireEvent.change(screen.getByLabelText('Start Date:'), { target: { value: '2026-03-05' } });
  fireEvent.change(screen.getByLabelText('End Date:'), { target: { value: '2026-03-07' } });
  await waitFor(() => expect(salesRequests()).toContain('/api/reports/staff-sales?from=2026-03-05T00:00:00&to=2026-03-07T23:59:59'));
});

// A custom period with no dates picked asks from the year 2000. The two tests below
// tell the two periods' requests apart by that, so they do not depend on what day of
// the week it is (on a Sunday, "this week" and "today" are the same two moments).
const isEverything = (text) => text.includes('from=2000-01-01T00:00:00');

test('picking another period takes the old rows off the screen until the new answer arrives', async () => {
  // This week holds Sarah. "Everything" holds nobody, and its answer is held back.
  let releaseEverything;
  salesOnBackend = (path) => (isEverything(path) ? [] : [SARAH]);
  const fakeBackend = global.fetch;
  global.fetch = vi.fn(async (url) => {
    if (isEverything(url)) await new Promise(resolve => { releaseEverything = resolve; });
    return fakeBackend(url);
  });

  asDan();
  await screen.findByText('Sarah Miller');

  await userEvent.click(screen.getByRole('button', { name: 'CUSTOM' }));

  // Sarah's week is not this period's board: nothing is drawn while its answer is on its way
  expect(screen.queryByText('Sarah Miller')).not.toBeInTheDocument();
  expect(screen.queryByText('No sales yet for this period.')).not.toBeInTheDocument();

  await waitFor(() => expect(releaseEverything).toBeDefined());
  await act(async () => { releaseEverything(); });
  expect(await screen.findByText('No sales yet for this period.')).toBeInTheDocument();
});

test('an answer that arrives after another period was picked is not drawn', async () => {
  // This week's answer is slow. By the time it arrives, CUSTOM has been picked and answered.
  let releaseWeek;
  salesOnBackend = (path) => (isEverything(path) ? [MORGAN] : [SARAH]);
  const fakeBackend = global.fetch;
  global.fetch = vi.fn(async (url) => {
    if (url.includes('staff-sales') && !isEverything(url)) await new Promise(resolve => { releaseWeek = resolve; });
    return fakeBackend(url);
  });

  asDan();
  await waitFor(() => expect(releaseWeek).toBeDefined());
  await userEvent.click(screen.getByRole('button', { name: 'CUSTOM' }));
  await screen.findByText('Morgan');

  await act(async () => { releaseWeek(); });

  expect(screen.getByText('Morgan')).toBeInTheDocument();
  expect(screen.queryByText('Sarah Miller')).not.toBeInTheDocument();
});

// ---------------------------------------------------------------------------
// Refreshing, and the backend being away
// ---------------------------------------------------------------------------

test('the board is asked for again every 15 seconds, and a new sale shows up', async () => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  asDan();
  await screen.findByText('Sarah Miller');
  expect(salesRequests()).toHaveLength(1);

  // Dan closes a check: the backend now has him first
  salesOnBackend = [{ ...DAN, rank: 1, sales: 2000 }, { ...SARAH, rank: 2 }, MORGAN];
  await act(async () => { await vi.advanceTimersByTimeAsync(LEADERBOARD_REFRESH_MS); });

  expect(salesRequests()).toHaveLength(2);
  expect(await screen.findByText('$2000.00')).toBeInTheDocument();
  const names = [...document.querySelectorAll('.leaderboard-name')].map(cell => cell.firstChild.textContent);
  expect(names).toEqual(['Dan', 'Sarah Miller', 'Morgan']);
});

test('leaving the screen stops the asking', async () => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  const { unmount } = asDan();
  await screen.findByText('Sarah Miller');

  unmount();
  await act(async () => { await vi.advanceTimersByTimeAsync(LEADERBOARD_REFRESH_MS * 3); });

  expect(salesRequests()).toHaveLength(1);
});

test('when the backend cannot be reached the screen says so, and recovers by itself', async () => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.spyOn(console, 'error').mockImplementation(() => {});
  backendIsDown = true;
  asDan();

  expect(await screen.findByRole('alert')).toHaveTextContent('The leaderboard could not be loaded.');
  // It does not claim that nobody sold anything: it does not know
  expect(screen.queryByText('No sales yet for this period.')).not.toBeInTheDocument();

  backendIsDown = false;
  await act(async () => { await vi.advanceTimersByTimeAsync(LEADERBOARD_REFRESH_MS); });

  expect(await screen.findByText('Sarah Miller')).toBeInTheDocument();
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

// ---------------------------------------------------------------------------
// The table on its own (as the template gallery draws it)
// ---------------------------------------------------------------------------

test('Leaderboard draws nothing while it has no answer, and needs nobody signed in', () => {
  const { container, rerender } = render(<Leaderboard rows={null} />);
  expect(container).toBeEmptyDOMElement();

  rerender(<Leaderboard rows={[SARAH]} />);
  expect(screen.getByText('Sarah Miller')).toBeInTheDocument();
  expect(document.querySelectorAll('.leaderboard-row.own')).toHaveLength(0);
});
