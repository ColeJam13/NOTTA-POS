import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Search from './Search';
import { StaffContext } from '../../context/StaffContext';
import { SEARCH_DELAY_MS, longEnough } from './searchRules';

/**
 * Behavior tests for the search box in the top bar.
 *
 * WHAT: Types into the box against a fake backend and checks when it asks, what it
 *       draws, and what choosing a result does for each kind of person.
 * WHY:  The box sat in the top bar for a year doing nothing. It is now a way to reach
 *       any table or check, so it has to ask sparingly, never show an answer for
 *       something other than what is in the box, and never offer a role something
 *       that role may not open.
 *
 * The fake backend works the same way as in the other tests: global.fetch is replaced
 * with a function that returns canned JSON for GET /api/search.
 */

let calls;            // every request path, in order
let answers;          // what GET /api/search returns, by what was typed (the q parameter)

const minutesAgo = (minutes) => new Date(Date.now() - minutes * 60 * 1000).toISOString();
const group = (...results) => ({ results, more: false });
const NOTHING = { tables: group(), openChecks: group(), closedChecks: group(), menuItems: group() };

const TABLE_W7 = { tableId: 7, tableNumber: 'W7', section: 'Window', seatCount: 4, status: 'available', isQuickOrder: false };
const TABLE_W17 = { tableId: 17, tableNumber: 'W17', section: 'Window', seatCount: 2, status: 'occupied', isQuickOrder: false };
const OPEN_ON_W17 = { orderId: 31, tableId: 17, tableNumber: 'W17', status: 'occupied', isQuickOrder: false, serverName: 'Sarah', itemCount: 3, subtotal: 48.5, openedAt: minutesAgo(12) };
// Two paid checks: one the person asking was sent the tip on, one they were not
const closedCheck = (orderId, tableNumber, withTip) => ({
  orderId, tableId: orderId, tableNumber, isQuickOrder: false, serverName: withTip ? 'Sarah' : 'Dan',
  createdAt: minutesAgo(90), closedAt: minutesAgo(30),
  subtotal: 20, tax: 0.6, total: 20.6,
  ...(withTip ? { tipAmount: 5, paymentMethod: 'credit_card' } : {}),
  items: [{ orderItemId: orderId * 10, name: 'Burger', quantity: 1, price: 20, specialInstructions: 'no onions', modifiers: [] }]
});
const GARDEN_TOAST = { menuItemId: 3, name: 'Garden Toast', category: 'Savory', price: 12.5, is86d: true };
const OMELETTE = { menuItemId: 4, name: 'Omelette of the Day', category: 'Savory', price: 16, is86d: false };

const EVERYTHING = {
  tables: group(TABLE_W7, TABLE_W17),
  openChecks: group(OPEN_ON_W17),
  closedChecks: group(closedCheck(21, 'W7', true), closedCheck(22, 'W7', false)),
  menuItems: group(GARDEN_TOAST, OMELETTE)
};

beforeEach(() => {
  calls = [];
  answers = { '7': EVERYTHING };

  global.fetch = vi.fn(async (url) => {
    const path = url.replace('http://localhost:8080', '');
    calls.push(path);
    const typed = new URL(path, 'http://x').searchParams.get('q');
    return { ok: true, status: 200, json: async () => answers[typed] || NOTHING };
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

// The box as a given role sees it. onOpenTable and onOpenScreen are what the top bar hands in.
const renderSearch = (role = 'server', { stay = false } = {}) => {
  const onOpenTable = vi.fn(() => !stay);
  const onOpenScreen = vi.fn(() => !stay);
  render(
    <StaffContext.Provider value={{ staff: { staffId: 7, name: 'Sarah', role }, signOut: () => {} }}>
      <Search onOpenTable={onOpenTable} onOpenScreen={onOpenScreen} />
      <button>somewhere else</button>
    </StaffContext.Provider>
  );
  return { onOpenTable, onOpenScreen };
};

const box = () => screen.getByRole('combobox', { name: 'Search tables, checks, and the menu' });
const results = () => screen.findByRole('listbox', { name: 'Search results' });
const type = (text) => userEvent.type(box(), text);
const searches = () => calls.filter(path => path.startsWith('/api/search'));
// Longer than the pause the box waits for, so "nothing was sent" means it never will be
const afterThePause = () => new Promise(done => setTimeout(done, SEARCH_DELAY_MS + 150));

// ============================================================================
// When it asks
// ============================================================================

test('typing several characters sends one request, for all of them, after a pause', async () => {
  // Without the pause, "table 12" would send eight requests
  answers['w17'] = EVERYTHING;
  renderSearch();

  await type('w17');
  await results();

  expect(searches()).toEqual(['/api/search?q=w17&closed=today']);
});

test('one letter is not searched for; one digit is', async () => {
  renderSearch();

  await type('w');
  await afterThePause();
  expect(searches()).toEqual([]);                    // A single letter matches half the menu
  expect(screen.queryByRole('listbox')).not.toBeInTheDocument();

  await userEvent.clear(box());
  await type('7');
  await results();
  expect(searches()).toEqual(['/api/search?q=7&closed=today']);       // "7" is table 7
});

test('the rule for how much must be typed', () => {
  expect(longEnough('')).toBe(false);
  expect(longEnough('   ')).toBe(false);
  expect(longEnough('w')).toBe(false);
  expect(longEnough(' w ')).toBe(false);
  expect(longEnough('7')).toBe(true);
  expect(longEnough('12')).toBe(true);
  expect(longEnough('w7')).toBe(true);
  expect(longEnough('sa')).toBe(true);
});

test('what is typed is sent as a value, whatever characters it has', async () => {
  renderSearch();

  await type('50% & more?');
  await afterThePause();

  // The text is encoded into the address; none of it can become part of the address itself
  expect(searches()).toEqual(['/api/search?q=50%25%20%26%20more%3F&closed=today']);
});

test('an answer that arrives late, for something no longer in the box, is not shown', async () => {
  // The first request is held back until after the second has been answered
  let releaseFirst;
  answers['w17'] = { ...NOTHING, tables: group(TABLE_W17) };
  global.fetch = vi.fn(async (url) => {
    const typed = new URL(url, 'http://x').searchParams.get('q');
    calls.push(typed);
    if (typed === '7') await new Promise(done => { releaseFirst = done; });
    return { ok: true, status: 200, json: async () => answers[typed] || NOTHING };
  });
  renderSearch();

  await type('7');
  await waitFor(() => expect(calls).toEqual(['7']));
  await userEvent.clear(box());
  await type('w17');
  expect(await screen.findByText('Table W17')).toBeInTheDocument();

  releaseFirst();                                    // Now the answer for "7" comes in, with everything in it
  await afterThePause();

  expect(screen.queryByText('Garden Toast')).not.toBeInTheDocument();
  expect(screen.getByText('Table W17')).toBeInTheDocument();
});

// ============================================================================
// What it draws
// ============================================================================

test('results are drawn in four groups, each with what the person needs to tell them apart', async () => {
  renderSearch();
  await type('7');
  const list = await results();

  const tables = within(list).getByRole('group', { name: 'TABLES' });
  expect(within(tables).getByText('Table W7')).toBeInTheDocument();
  expect(within(tables).getByText('Window · 4 seats')).toBeInTheDocument();
  expect(within(tables).getByText('AVAILABLE')).toBeInTheDocument();
  expect(within(tables).getByText('OCCUPIED')).toBeInTheDocument();

  const open = within(list).getByRole('group', { name: 'OPEN CHECKS' });
  expect(within(open).getByText('Table W17')).toBeInTheDocument();
  expect(within(open).getByText('Check #31 · Sarah · 3 items · opened 12m ago')).toBeInTheDocument();
  expect(within(open).getByText('$48.50')).toBeInTheDocument();

  const closed = within(list).getByRole('group', { name: 'CLOSED CHECKS' });
  expect(within(closed).getByText('Check #21 · Sarah · closed 30m ago')).toBeInTheDocument();
  // The amount is what the guest paid (check + tip) where the tip was sent, and the check alone where it was not
  expect(within(closed).getByText('$25.60')).toBeInTheDocument();
  expect(within(closed).getByText('$20.60')).toBeInTheDocument();

  const menu = within(list).getByRole('group', { name: 'MENU' });
  expect(within(menu).getByText('Garden Toast')).toBeInTheDocument();
  expect(within(menu).getByText('$12.50')).toBeInTheDocument();
  expect(within(menu).getByText("86'D")).toBeInTheDocument();          // A word, not only a color
  expect(within(menu).getByText('AVAILABLE')).toBeInTheDocument();
});

test('a group with nothing in it is not drawn', async () => {
  answers['toast'] = { ...NOTHING, menuItems: group(GARDEN_TOAST) };
  renderSearch();
  await type('toast');
  const list = await results();

  expect(within(list).getByRole('group', { name: 'MENU' })).toBeInTheDocument();
  expect(within(list).queryByRole('group', { name: 'TABLES' })).not.toBeInTheDocument();
  expect(within(list).queryByRole('group', { name: 'OPEN CHECKS' })).not.toBeInTheDocument();
  expect(within(list).queryByRole('group', { name: 'CLOSED CHECKS' })).not.toBeInTheDocument();
});

test('when nothing is found it says so, with what was searched for', async () => {
  renderSearch();
  await type('zucchini');

  expect(await screen.findByText('Nothing found for “zucchini”.')).toBeInTheDocument();
  expect(screen.queryByRole('group', { name: 'TABLES' })).not.toBeInTheDocument();
});

test('when a group was cut short, it says there are more', async () => {
  answers['w1'] = { ...NOTHING, tables: { results: [TABLE_W7, TABLE_W17], more: true } };
  renderSearch();
  await type('w1');

  expect(await screen.findByText('Showing the first 2. Type more to narrow it down.')).toBeInTheDocument();
});

test('when the backend cannot be asked, it says so and shows no results', async () => {
  global.fetch = vi.fn(async () => ({ ok: false, status: 500, json: async () => ({ message: 'boom' }) }));
  vi.spyOn(console, 'error').mockImplementation(() => {});          // The box logs the failure; keep the test output clean
  renderSearch();
  await type('w7');

  expect(await screen.findByRole('alert')).toHaveTextContent('Search is not available right now.');
  expect(screen.queryByRole('group', { name: 'TABLES' })).not.toBeInTheDocument();
});

// ============================================================================
// Choosing a result
// ============================================================================

test('someone who takes orders: choosing a table opens its order screen, and the box is emptied', async () => {
  const { onOpenTable } = renderSearch('server');
  await type('7');
  await results();

  await userEvent.click(screen.getByRole('option', { name: /^Table W7, Window/ }));

  // The table's own fields: what the order screen needs
  expect(onOpenTable).toHaveBeenCalledWith(expect.objectContaining({ tableId: 7, tableNumber: 'W7', status: 'available', isQuickOrder: false }));
  expect(box()).toHaveValue('');
  expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
});

test('choosing an open check opens the order screen for the table it is on', async () => {
  const { onOpenTable } = renderSearch('host');
  await type('7');
  const list = await results();

  await userEvent.click(within(within(list).getByRole('group', { name: 'OPEN CHECKS' })).getByRole('option'));

  expect(onOpenTable).toHaveBeenCalledWith(expect.objectContaining({ tableId: 17, tableNumber: 'W17', status: 'occupied', serverName: 'Sarah' }));
});

test('if the person chooses to stay where they are, the box keeps what was typed', async () => {
  // The order screen asks "leave and lose unsaved items?" and the answer was no
  const { onOpenTable } = renderSearch('server', { stay: true });
  await type('7');
  await results();

  await userEvent.click(screen.getByRole('option', { name: /^Table W7, Window/ }));

  expect(onOpenTable).toHaveBeenCalled();
  expect(box()).toHaveValue('7');
  expect(screen.getByRole('listbox')).toBeInTheDocument();
});

test('choosing a closed check opens it as a pop-up, with its tip only if the tip was sent', async () => {
  const { onOpenTable, onOpenScreen } = renderSearch('server');
  await type('7');
  await results();

  // The check this person was sent the tip on
  await userEvent.click(screen.getByRole('option', { name: /Check #21/ }));
  expect(screen.getByRole('heading', { name: 'Table W7' })).toBeInTheDocument();
  expect(screen.getByText('Burger')).toBeInTheDocument();
  expect(screen.getByText('NOTE: no onions')).toBeInTheDocument();
  expect(screen.getByText('Tip:')).toBeInTheDocument();
  expect(screen.getByText('Payment Method:').closest('p')).toHaveTextContent('Payment Method: CREDIT CARD');
  expect(onOpenTable).not.toHaveBeenCalled();        // The screen underneath stays where it is
  expect(onOpenScreen).not.toHaveBeenCalled();

  await userEvent.click(screen.getByRole('button', { name: '×' }));
  expect(screen.queryByText('Burger')).not.toBeInTheDocument();

  // The check they were not: no tip row, no payment method, nothing in their place
  await type('7');
  await results();
  await userEvent.click(screen.getByRole('option', { name: /Check #22/ }));
  expect(screen.getByText('Burger')).toBeInTheDocument();
  expect(screen.queryByText('Tip:')).not.toBeInTheDocument();
  expect(screen.queryByText(/Payment Method/)).not.toBeInTheDocument();
});

test('a menu item is information, not a button, for someone who may not change the 86 board', async () => {
  const { onOpenScreen } = renderSearch('server');
  await type('7');
  const list = await results();

  const row = within(within(list).getByRole('group', { name: 'MENU' })).getByText('Garden Toast').closest('[role="option"]');
  expect(row.tagName).toBe('DIV');
  expect(row).toHaveAttribute('aria-disabled', 'true');
  await userEvent.click(row);
  expect(onOpenScreen).not.toHaveBeenCalled();
});

test('the kitchen: tables and open checks are information; a menu item opens the 86 board', async () => {
  // The kitchen opens no checks, and is who knows what ran out
  const { onOpenTable, onOpenScreen } = renderSearch('kitchen');
  await type('7');
  const list = await results();

  const table = within(within(list).getByRole('group', { name: 'TABLES' })).getByText('Table W7').closest('[role="option"]');
  expect(table.tagName).toBe('DIV');
  await userEvent.click(table);
  expect(onOpenTable).not.toHaveBeenCalled();

  await userEvent.click(screen.getByRole('option', { name: /Garden Toast/ }));
  expect(onOpenScreen).toHaveBeenCalledWith('eightySix');
});

// ============================================================================
// The keyboard
// ============================================================================

test('Enter opens the first result when the arrow keys have not been used', async () => {
  const { onOpenTable } = renderSearch('server');
  await type('7');
  await results();

  await userEvent.keyboard('{Enter}');

  expect(onOpenTable).toHaveBeenCalledWith(expect.objectContaining({ tableNumber: 'W7' }));
});

test('the arrow keys move through the results and Enter opens the one they are on', async () => {
  const { onOpenTable } = renderSearch('server');
  await type('7');
  await results();

  await userEvent.keyboard('{ArrowDown}');
  expect(screen.getByRole('option', { name: /^Table W7, Window/ })).toHaveAttribute('aria-selected', 'true');
  expect(box()).toHaveAttribute('aria-activedescendant', 'search-table-7');     // What a screen reader announces

  await userEvent.keyboard('{ArrowDown}');
  expect(screen.getByRole('option', { name: /^Table W17, Window/ })).toHaveAttribute('aria-selected', 'true');
  expect(screen.getByRole('option', { name: /^Table W7, Window/ })).toHaveAttribute('aria-selected', 'false');

  await userEvent.keyboard('{ArrowUp}{ArrowUp}');    // Up from the first wraps round to the last
  expect(screen.getByRole('option', { name: /Check #22/ })).toHaveAttribute('aria-selected', 'true');

  await userEvent.keyboard('{ArrowDown}{ArrowDown}{Enter}');
  expect(onOpenTable).toHaveBeenCalledWith(expect.objectContaining({ tableNumber: 'W17', tableId: 17 }));
});

test('the arrow keys skip results that are only information', async () => {
  // For a server the two menu items cannot be chosen: the last stop is the second closed check
  renderSearch('server');
  await type('7');
  await results();

  await userEvent.keyboard('{ArrowUp}');

  expect(screen.getByRole('option', { name: /Check #22/ })).toHaveAttribute('aria-selected', 'true');
});

test('Escape closes the panel and keeps what was typed; coming back to the box shows it again', async () => {
  renderSearch();
  await type('7');
  await results();

  await userEvent.keyboard('{Escape}');
  expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  expect(box()).toHaveValue('7');
  expect(box()).toHaveAttribute('aria-expanded', 'false');

  await userEvent.click(screen.getByRole('button', { name: 'somewhere else' }));
  await userEvent.click(box());
  expect(screen.getByRole('listbox')).toBeInTheDocument();
  expect(searches()).toHaveLength(1);                // The answer it already had; nothing new was asked
});

test('a click anywhere else closes the panel', async () => {
  renderSearch();
  await type('7');
  await results();

  await userEvent.click(screen.getByRole('button', { name: 'somewhere else' }));

  expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
});

// ============================================================================
// How far back to look for paid checks
// ============================================================================

test('paid checks are looked for today unless a longer period is chosen, which asks again', async () => {
  renderSearch();
  await type('7');
  await results();
  const periods = screen.getByRole('group', { name: 'Closed checks from' });
  expect(within(periods).getByRole('button', { name: 'TODAY' })).toHaveAttribute('aria-pressed', 'true');

  await userEvent.click(within(periods).getByRole('button', { name: '30 DAYS' }));

  await waitFor(() => expect(searches()).toEqual(['/api/search?q=7&closed=today', '/api/search?q=7&closed=30days']));
  expect(within(screen.getByRole('group', { name: 'Closed checks from' })).getByRole('button', { name: '30 DAYS' })).toHaveAttribute('aria-pressed', 'true');
  expect(box()).toHaveValue('7');
});
