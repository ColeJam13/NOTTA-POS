import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import { setSessionToken } from './services/api';

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

const json = (data, status = 200) => ({ ok: status < 300, status, json: async () => data });

beforeEach(() => {
  requests = [];
  liveTokens = {};
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
    throw new Error(`Unexpected request in test: ${method} ${path}`);
  });
});

afterEach(() => {
  vi.restoreAllMocks();
  setSessionToken(null);
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
