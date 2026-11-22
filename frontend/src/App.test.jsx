import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import { setSessionToken } from './services/api';
import { TEMPLATES, forgetTemplates } from './templates';
import { TEST_LOOK } from './templates/testSupport';

/**
 * Tests for the app as a whole: signing in with a PIN, which screens each
 * role is offered, and signing out.
 *
 * The fake backend works the same way as in CreateOrder.test.jsx: global.fetch
 * is replaced with a function that returns canned JSON. Here it also plays
 * the part of sign-in: it knows five PINs, hands out a token for each, and
 * (like the real backend) answers 401 to a token it no longer knows. One of
 * the five is the starter PIN, whose owner must choose a new one.
 */

// PIN -> who it belongs to
const STAFF_BY_PIN = {
  '1111': { staffId: 1, name: 'Sarah Miller', role: 'server', isActive: true },
  '2222': { staffId: 2, name: 'Morgan', role: 'manager', isActive: true },
  '3333': { staffId: 3, name: 'Kit', role: 'kitchen', isActive: true },
  '4444': { staffId: 4, name: 'Harper', role: 'host', isActive: true },
  // The first manager of a new database, still on the starter PIN
  '1234': { staffId: 9, name: 'Manager', role: 'manager', isActive: true, mustChangePin: true }
};

let requests;         // [{ method, path, token }] - token is the X-Staff-Token header that was sent
let liveTokens;       // token -> staff, for sessions the fake backend still knows
let templateOnBackend;    // what GET /api/settings says the restaurant's template is

const json = (data, status = 200) => ({ ok: status < 300, status, json: async () => data });

beforeEach(() => {
  requests = [];
  liveTokens = {};
  templateOnBackend = '8-bit';
  // The app has one real template so far; a made-up second one gives the tests something to switch to
  TEMPLATES.push(TEST_LOOK);
  window.localStorage.clear();
  forgetTemplates();
  window.sessionStorage.clear();
  setSessionToken(null);

  global.fetch = vi.fn(async (url, options = {}) => {
    const method = options.method || 'GET';
    const path = url.replace('http://localhost:8080', '');
    const token = options.headers ? options.headers['X-Staff-Token'] : undefined;
    requests.push({ method, path, token });

    // --- sign-in ---
    if (method === 'POST' && path === '/api/staff/sign-in') {
      const staff = STAFF_BY_PIN[JSON.parse(options.body).pin];
      if (!staff) return json({ message: 'PIN not recognized.' }, 401);
      const newToken = `token-for-${staff.name}`;
      liveTokens[newToken] = staff;
      return json({ staff, token: newToken });
    }
    if (method === 'POST' && path === '/api/staff/sign-out') {
      delete liveTokens[token];
      return { ok: true, status: 204, json: async () => null };
    }
    // --- the restaurant's settings: every signed-in terminal asks which template is in use ---
    if (method === 'GET' && path === '/api/settings') {
      if (!liveTokens[token]) return json({ message: 'Sign in to do that.' }, 401);
      if (liveTokens[token].mustChangePin) return json({ message: 'Choose a new PIN before doing anything else.', code: 'PIN_CHANGE_REQUIRED' }, 403);
      return json({ taxRate: 0.03, template: templateOnBackend });
    }
    if (method === 'PUT' && path === '/api/settings/template') {
      templateOnBackend = JSON.parse(options.body).template;
      return json({ taxRate: 0.03, template: templateOnBackend });
    }
    if (path === '/api/staff/me') {
      return liveTokens[token] ? json(liveTokens[token]) : json({ message: 'Sign in to continue.' }, 401);
    }
    if (method === 'PUT' && path === '/api/staff/me/pin') {
      // Like the real backend: the person is handed back with nothing left to change
      liveTokens[token] = { ...liveTokens[token], mustChangePin: false };
      return json(liveTokens[token]);
    }

    // --- the screens these tests open ---
    if (path === '/api/tables?quickOrders=false') {
      // Like a restricted endpoint on the real backend: a token it does not know gets a 401
      if (token && !liveTokens[token]) return json({ message: 'Sign in to do that.' }, 401);
      return json([{ tableId: 1, tableNumber: 'W1', seatCount: 2, status: 'occupied', shape: 'circle', rotation: 0, xposition: 50, yposition: 40 }]);
    }
    if (path === '/api/menu-items') return json([{ menuItemId: 1, name: 'Chicken Cutty', price: 17.0, category: 'Savory', is86d: false }]);
    if (path === '/api/menu-items/modifier-groups') return json([]);
    if (path === '/api/orders?tableId=1') return json([{ orderId: 77, tableId: 1, status: 'open' }]);
    if (path === '/api/order-items/order/77') {
      return json([{ orderItemId: 50, menuItemId: 1, price: 17.0, quantity: 1, status: 'fired' }]);
    }
    if (path === '/api/order-item-modifiers/order/77') return json([]);
    // The search box in the top bar finds table W1 for "1", and nothing else
    if (path === '/api/search?q=1&closed=today') {
      const none = { results: [], more: false };
      return json({
        tables: { results: [{ tableId: 1, tableNumber: 'W1', section: 'Window', seatCount: 2, status: 'occupied', isQuickOrder: false }], more: false },
        openChecks: none, closedChecks: none, menuItems: none
      });
    }
    throw new Error(`Unexpected request in test: ${method} ${path}`);
  });
});

afterEach(() => {
  vi.restoreAllMocks();
  setSessionToken(null);
  TEMPLATES.pop();                  // The made-up template added in beforeEach
  forgetTemplates();
});

// Tap a PIN on the pad, one digit at a time
const typePin = async (pin) => {
  for (const digit of pin) {
    await userEvent.click(screen.getByRole('button', { name: digit }));
  }
};

// Start the app and sign in with a PIN
const signIn = async (pin) => {
  const view = render(<App />);
  await typePin(pin);
  await screen.findByText(/Signed in as/);
  return view;
};

// The labels of the buttons on the Home screen, in order
const homeButtons = () =>
  [...document.querySelectorAll('.landing-btn')].map(button => button.textContent);

// ============================================================================
// Signing in
// ============================================================================

test('the app opens on the PIN pad, and nothing else can be reached before signing in', () => {
  render(<App />);

  expect(screen.getByText('ENTER YOUR PIN')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /FLOOR MAP/i })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /CREATE ORDER/i })).not.toBeInTheDocument();
  expect(requests).toEqual([]);                                     // Nothing was asked of the backend
});

test('the right PIN signs that person in and shows who they are', async () => {
  await signIn('2222');

  // The logo proves the Home view mounted
  expect(screen.getByAltText(/NOTTA-POS Logo/i)).toBeInTheDocument();
  expect(screen.getByText(/Signed in as/)).toHaveTextContent('Signed in as Morgan (Manager)');
  expect(screen.queryByText('ENTER YOUR PIN')).not.toBeInTheDocument();
});

test('a wrong PIN says so, clears the dots, and lets the person try again', async () => {
  render(<App />);

  await typePin('9999');

  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('PIN not recognized.'));
  expect(screen.getByRole('status')).toHaveAccessibleName('0 of 4 digits entered');
  expect(screen.getByText('ENTER YOUR PIN')).toBeInTheDocument();

  // Still usable: the right PIN now works
  await typePin('1111');
  expect(await screen.findByText(/Signed in as/)).toHaveTextContent('Sarah Miller (Server)');
});

// ============================================================================
// What each role is offered
// ============================================================================

test('a manager is offered every screen, including Financials and Staff', async () => {
  await signIn('2222');

  expect(homeButtons()).toEqual([
    'FLOOR MAP', 'TABLES', 'QUICK ORDERS', 'ACTIVE ORDERS', 'CREATE ORDER', '86 BOARD', 'FINANCIALS', 'CHALLENGES', 'STAFF'
  ]);
});

test('a server is offered the floor screens, without Financials or Staff', async () => {
  await signIn('1111');

  expect(homeButtons()).toEqual([
    'FLOOR MAP', 'TABLES', 'QUICK ORDERS', 'ACTIVE ORDERS', 'CREATE ORDER', '86 BOARD', 'CHALLENGES'
  ]);
});

test('the kitchen is offered the kitchen display and the 86 board only', async () => {
  await signIn('3333');

  expect(homeButtons()).toEqual(['ACTIVE ORDERS', '86 BOARD']);
});

test('a host is offered the screens for seating and taking an order, and tapping a table opens its order', async () => {
  // Hosts may open a check, ring in, send, and take payment (the owner's decision D2, and
  // what the backend allows), so the screens that do those things are offered. The
  // kitchen display and the 86 board are not.
  await signIn('4444');
  expect(homeButtons()).toEqual(['FLOOR MAP', 'TABLES', 'QUICK ORDERS', 'CREATE ORDER']);

  await userEvent.click(screen.getByRole('button', { name: 'FLOOR MAP' }));
  await userEvent.click(await screen.findByText('W1'));

  // The order screen for that table
  expect(await screen.findByText('Current Order - Table W1')).toBeInTheDocument();

  // The nav bar offers a host the same four screens
  const navButtons = [...document.querySelectorAll('.nav-btn')].map(button => button.textContent);
  expect(navButtons).toEqual(['FLOOR MAP', 'TABLES', 'QUICK ORDERS', 'CREATE ORDER']);
});

// ============================================================================
// The session
// ============================================================================

test('every request after sign-in carries the signed-in person\'s token', async () => {
  await signIn('1111');

  await userEvent.click(screen.getByRole('button', { name: 'FLOOR MAP' }));
  await screen.findByText('W1');

  const tablesRequest = requests.find(r => r.path === '/api/tables?quickOrders=false');
  expect(tablesRequest.token).toBe('token-for-Sarah Miller');
  // The sign-in request itself went out before there was a token
  expect(requests[0]).toEqual({ method: 'POST', path: '/api/staff/sign-in', token: undefined });
});

test('SIGN OUT goes back to the PIN pad and ends the session on the backend', async () => {
  await signIn('1111');
  await userEvent.click(screen.getByRole('button', { name: 'FLOOR MAP' }));
  await screen.findByText('W1');

  // The nav bar shows who is signed in
  const userInfo = document.querySelector('.user-info');
  expect(within(userInfo).getByText('SM')).toBeInTheDocument();       // Initials of Sarah Miller
  expect(within(userInfo).getByText('Sarah Miller')).toBeInTheDocument();
  expect(within(userInfo).getByText('Server')).toBeInTheDocument();

  await userEvent.click(within(userInfo).getByRole('button', { name: /SIGN OUT/ }));

  expect(screen.getByText('ENTER YOUR PIN')).toBeInTheDocument();
  expect(requests).toContainEqual({ method: 'POST', path: '/api/staff/sign-out', token: 'token-for-Sarah Miller' });

  // The next person signs in as themselves, starting from Home
  await typePin('2222');
  expect(await screen.findByText(/Signed in as/)).toHaveTextContent('Morgan (Manager)');
});

// ============================================================================
// The starter PIN
// ============================================================================

test('signing in with the starter PIN leads to choosing a new PIN, and to nothing else until that is done', async () => {
  render(<App />);
  await typePin('1234');

  // Not Home: the only screen is the one that asks for a new PIN
  expect(await screen.findByText('ENTER YOUR CURRENT PIN')).toBeInTheDocument();
  expect(screen.getByText(/You signed in with the starter PIN/)).toBeInTheDocument();
  expect(screen.queryByText(/Signed in as/)).not.toBeInTheDocument();
  expect(homeButtons()).toEqual([]);

  // Current PIN, new PIN, new PIN again
  await typePin('1234');
  await screen.findByText('CHOOSE A NEW PIN');
  await typePin('4821');
  await screen.findByText('ENTER THE NEW PIN AGAIN');
  await typePin('4821');

  // The app opens, as the same person, without signing in again
  expect(await screen.findByText(/Signed in as/)).toHaveTextContent('Signed in as Manager (Manager)');
  expect(homeButtons()).toContain('STAFF');
  expect(requests).toContainEqual({ method: 'PUT', path: '/api/staff/me/pin', token: 'token-for-Manager' });
});

test('reloading while a new PIN is still owed comes back to the same screen, not to Home', async () => {
  const first = render(<App />);
  await typePin('1234');
  await screen.findByText('ENTER YOUR CURRENT PIN');
  first.unmount();                                                  // The page goes away...
  setSessionToken(null);

  render(<App />);                                                  // ...and loads again

  // Reloading is not a way around it: the backend still says a new PIN is owed
  expect(await screen.findByText('ENTER YOUR CURRENT PIN')).toBeInTheDocument();
  expect(screen.queryByText(/Signed in as/)).not.toBeInTheDocument();
});

test('SIGN OUT on the choose-a-new-PIN screen goes back to the PIN pad, so someone else can sign in', async () => {
  render(<App />);
  await typePin('1234');
  await screen.findByText('ENTER YOUR CURRENT PIN');

  await userEvent.click(screen.getByRole('button', { name: 'SIGN OUT' }));

  expect(screen.getByText('ENTER YOUR PIN')).toBeInTheDocument();
  expect(requests).toContainEqual({ method: 'POST', path: '/api/staff/sign-out', token: 'token-for-Manager' });

  await typePin('1111');
  expect(await screen.findByText(/Signed in as/)).toHaveTextContent('Sarah Miller (Server)');
});

test('reloading the page keeps the person signed in', async () => {
  const first = await signIn('2222');
  first.unmount();                                                  // The page goes away...
  setSessionToken(null);

  render(<App />);                                                  // ...and loads again

  // No PIN pad: the stored token was checked with the backend and is still good
  expect(await screen.findByText(/Signed in as/)).toHaveTextContent('Morgan (Manager)');
  expect(screen.queryByText('ENTER YOUR PIN')).not.toBeInTheDocument();
  expect(requests).toContainEqual({ method: 'GET', path: '/api/staff/me', token: 'token-for-Morgan' });
});

test('reloading after the session ended on the backend shows the PIN pad', async () => {
  const first = await signIn('2222');
  first.unmount();
  setSessionToken(null);
  liveTokens = {};                                                  // The backend no longer knows the token (a manager changed this person's PIN, say)

  render(<App />);

  expect(await screen.findByText('ENTER YOUR PIN')).toBeInTheDocument();
  expect(window.sessionStorage.getItem('notta.staffToken')).toBeNull();
});

test('when the backend stops recognizing the token mid-shift, the app returns to the PIN pad', async () => {
  await signIn('1111');

  // A manager changed Sarah's PIN, which ends her session on the backend
  liveTokens = {};
  await userEvent.click(screen.getByRole('button', { name: 'FLOOR MAP' }));

  expect(await screen.findByText('ENTER YOUR PIN')).toBeInTheDocument();
});

// ============================================================================
// Moving between screens
// ============================================================================

// Walks Floor Map -> table W1 -> CREATE ORDER in the nav bar
test('tapping CREATE ORDER while a table is on screen starts a fresh order, not more items on that table', async () => {
  const { container } = await signIn('1111');
  await userEvent.click(screen.getByRole('button', { name: 'FLOOR MAP' }));
  await userEvent.click(await screen.findByText('W1'));

  // Table W1's order is on screen, with the item it already has
  expect(await screen.findByText('Current Order - Table W1')).toBeInTheDocument();
  const list = () => container.querySelector('.order-items-list');
  expect(await within(list()).findByText('Chicken Cutty')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: 'CREATE ORDER' }));

  // A new order: W1's item is gone from the list and nothing links this screen to W1's check.
  // (This used to keep W1's items and order under a "New Quick Order" heading.)
  expect(await screen.findByText('Current Order - New Quick Order')).toBeInTheDocument();
  expect(within(list()).queryByText('Chicken Cutty')).not.toBeInTheDocument();
  expect(screen.getByText('Add items to order')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'CLOSE ORDER' })).toBeDisabled();
  await waitFor(() => expect(screen.getByRole('button', { name: 'SEND ORDER' })).toBeDisabled());
});

// ============================================================================
// The search box in the top bar
// ============================================================================

test('a server finds a table from the search box on any screen and lands on its order', async () => {
  await signIn('1111');
  await userEvent.click(screen.getByRole('button', { name: 'CREATE ORDER' }));
  expect(await screen.findByText('Current Order - New Quick Order')).toBeInTheDocument();

  await userEvent.type(screen.getByRole('combobox', { name: 'Search tables, checks, and the menu' }), '1');
  await userEvent.click(await screen.findByRole('option', { name: /^Table W1, Window/ }));

  // Table W1's order, loaded fresh, with the item it already has
  expect(await screen.findByText('Current Order - Table W1')).toBeInTheDocument();
  expect(await within(document.querySelector('.order-items-list')).findByText('Chicken Cutty')).toBeInTheDocument();
  expect(screen.getByRole('combobox', { name: 'Search tables, checks, and the menu' })).toHaveValue('');
});

test('the kitchen can search, and a table it finds is information, not a way onto the order screen', async () => {
  await signIn('3333');
  await userEvent.click(screen.getByRole('button', { name: '86 BOARD' }));

  await userEvent.type(await screen.findByRole('combobox', { name: 'Search tables, checks, and the menu' }), '1');
  const found = await screen.findByRole('option', { name: /^Table W1, Window/ });

  expect(found.tagName).toBe('DIV');
  await userEvent.click(found);
  expect(screen.queryByText(/Current Order/)).not.toBeInTheDocument();
});

// ============================================================================
// The restaurant's template (the look of every screen)
// ============================================================================

const templateOnThePage = () => document.documentElement.getAttribute('data-template');
const settingsRequests = () => requests.filter(r => r.path.startsWith('/api/settings'));

test('after sign-in the app asks which template the restaurant uses and puts it on the page', async () => {
  templateOnBackend = 'test-look';

  await signIn('1111');

  await waitFor(() => expect(templateOnThePage()).toBe('test-look'));
  expect(settingsRequests()[0]).toEqual({ method: 'GET', path: '/api/settings', token: 'token-for-Sarah Miller' });
  // Remembered on this device, so the PIN pad is drawn in it next time, before anyone signs in
  expect(window.localStorage.getItem('notta.template')).toBe('test-look');
});

test('a template name the app does not know leaves the screens in the default look', async () => {
  templateOnBackend = 'a-template-from-the-future';

  await signIn('1111');

  await waitFor(() => expect(settingsRequests()).toHaveLength(1));
  expect(templateOnThePage()).toBe('8-bit');
});

test('nothing is asked about the template while a new PIN is still owed', async () => {
  // The backend refuses everything but choosing a PIN from this person, so asking would
  // only fill the log with refusals
  render(<App />);
  await typePin('1234');
  await screen.findByText(/CHOOSE A NEW PIN|ENTER YOUR CURRENT PIN/i);

  expect(settingsRequests()).toEqual([]);
});

test('only a manager is offered the Settings gear, on Home and in the nav bar', async () => {
  const view = await signIn('2222');
  expect(screen.getByRole('button', { name: 'Settings' })).toBeInTheDocument();           // On Home, beside SIGN OUT
  await userEvent.click(screen.getByRole('button', { name: 'STAFF' }));
  await screen.findByRole('heading', { name: 'STAFF' });
  expect(screen.getByRole('button', { name: 'Settings' })).toBeInTheDocument();           // In the nav bar
  view.unmount();

  setSessionToken(null);
  window.sessionStorage.clear();
  await signIn('1111');
  expect(screen.queryByRole('button', { name: 'Settings' })).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'TABLES' }));
  await screen.findByRole('heading', { name: 'TABLES' });
  expect(screen.queryByRole('button', { name: 'Settings' })).not.toBeInTheDocument();
});

test('a manager previews a template, takes it to another screen, and chooses it for the whole restaurant', async () => {
  await signIn('2222');
  await waitFor(() => expect(templateOnThePage()).toBe('8-bit'));

  // The gear, then PREVIEW on the other template
  await userEvent.click(screen.getByRole('button', { name: 'Settings' }));
  await userEvent.click(await screen.findByRole('button', { name: 'Preview Test Look' }));
  expect(templateOnThePage()).toBe('test-look');
  expect(screen.getByRole('status', { name: 'Template preview' })).toBeInTheDocument();
  expect(templateOnBackend).toBe('8-bit');                          // Nothing has changed for anyone else

  // The preview and its bar go along to another screen
  await userEvent.click(screen.getByRole('button', { name: 'STAFF' }));
  await screen.findByRole('heading', { name: 'STAFF' });
  expect(templateOnThePage()).toBe('test-look');
  expect(screen.getByRole('status', { name: 'Template preview' })).toBeInTheDocument();

  // Choose it
  await userEvent.click(screen.getByRole('button', { name: 'USE TEST LOOK EVERYWHERE' }));

  await waitFor(() => expect(screen.queryByRole('status', { name: 'Template preview' })).not.toBeInTheDocument());
  expect(templateOnBackend).toBe('test-look');
  expect(templateOnThePage()).toBe('test-look');
  expect(requests.find(r => r.method === 'PUT' && r.path === '/api/settings/template').token).toBe('token-for-Morgan');
});

test('signing out during a preview ends it, so the next person sees the restaurant\'s own template', async () => {
  await signIn('2222');
  await userEvent.click(screen.getByRole('button', { name: 'Settings' }));
  await userEvent.click(await screen.findByRole('button', { name: 'Preview Test Look' }));
  expect(templateOnThePage()).toBe('test-look');

  await userEvent.click(screen.getByRole('button', { name: /SIGN OUT/ }));

  await screen.findByText('ENTER YOUR PIN');
  expect(templateOnThePage()).toBe('8-bit');
});
