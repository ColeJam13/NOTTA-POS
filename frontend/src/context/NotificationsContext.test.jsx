import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NotificationsProvider, useNotifications, NOTIFICATIONS_REFRESH_MS } from './NotificationsContext';

/**
 * Behavior tests for where notifications come from: NotificationsProvider.
 *
 * WHAT: Puts a small test screen inside the provider, against a fake backend, and
 *       checks when the backend is asked, what the screen is handed, and what
 *       dismissing and sending do to the list and to the backend.
 * WHY:  Every screen's bell reads from this one place. It must ask once (not once per
 *       screen), keep asking, stop when the person signs out, and never show a
 *       notification as dismissed when the backend was not told.
 *
 * Time is the test's to move here (vi.useFakeTimers): "five seconds later" happens at
 * once, and no test waits.
 */

let onBackend;        // The notifications the fake backend holds for this person
let calls;            // [{ method, path, body }] in order
let failing;          // Paths that answer 500

const TOAST_86 = { notificationId: 3, kind: 'item_86', text: "Garden Toast is 86'd", fromName: 'Kit', createdAt: new Date().toISOString() };
const ORDER_UP = { notificationId: 2, kind: 'order_up', text: 'Table W5 is up', fromName: 'Kit', createdAt: new Date().toISOString() };

const json = (data, status = 200) => ({ ok: status < 300, status, json: async () => data });

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  onBackend = [TOAST_86, ORDER_UP];
  calls = [];
  failing = new Set();

  global.fetch = vi.fn(async (url, options = {}) => {
    const path = url.replace('http://localhost:8080', '');
    const method = options.method || 'GET';
    calls.push({ method, path, body: options.body ? JSON.parse(options.body) : undefined });
    if (failing.has(path)) return json({ message: 'boom' }, 500);

    if (method === 'GET' && path === '/api/notifications') return json(onBackend);
    const dismissed = path.match(/^\/api\/notifications\/(\d+)\/dismiss$/);
    if (method === 'POST' && dismissed) {
      onBackend = onBackend.filter(notification => notification.notificationId !== Number(dismissed[1]));
      return { ok: true, status: 204, json: async () => { throw new Error('a 204 has no body'); } };
    }
    if (method === 'POST' && path === '/api/notifications/dismiss-all') {
      onBackend = [];
      return { ok: true, status: 204, json: async () => { throw new Error('a 204 has no body'); } };
    }
    if (method === 'POST' && path === '/api/notifications/messages') {
      return json({ notificationId: 44, kind: 'message', text: JSON.parse(options.body).text, fromName: 'Kit', createdAt: new Date().toISOString() }, 201);
    }
    throw new Error(`Unexpected request in test: ${method} ${path}`);
  });
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

// A screen that shows what the provider hands it, with a button for each action
let lastSendOutcome;
function TestScreen() {
  const { notifications, dismiss, dismissAll, sendMessage } = useNotifications();
  return (
    <div>
      <p data-testid="texts">{notifications.map(notification => notification.text).join(' | ') || 'none'}</p>
      <button onClick={() => dismiss(3)}>dismiss toast</button>
      <button onClick={() => dismissAll()}>dismiss all</button>
      <button onClick={() => sendMessage('Fryer is down').then(() => { lastSendOutcome = 'sent'; }, () => { lastSendOutcome = 'failed'; })}>send</button>
    </div>
  );
}

const asks = () => calls.filter(call => call.method === 'GET' && call.path === '/api/notifications').length;
// Let answers that have already come back be drawn
const settle = () => act(async () => { await vi.advanceTimersByTimeAsync(0); });
const later = (ms) => act(async () => { await vi.advanceTimersByTimeAsync(ms); });

const renderProvider = async () => {
  const view = render(<NotificationsProvider><TestScreen /><TestScreen /></NotificationsProvider>);
  await settle();
  return view;
};

test('the backend is asked once when the provider appears, however many screens read from it', async () => {
  await renderProvider();                             // Two screens inside one provider

  expect(asks()).toBe(1);
  expect(screen.getAllByTestId('texts').map(p => p.textContent)).toEqual([
    "Garden Toast is 86'd | Table W5 is up", "Garden Toast is 86'd | Table W5 is up"]);
});

test('it asks again every five seconds and shows what is new', async () => {
  await renderProvider();
  onBackend = [{ notificationId: 5, kind: 'message', text: 'Last call in ten', fromName: 'Dan', createdAt: new Date().toISOString() }, ...onBackend];

  await later(NOTIFICATIONS_REFRESH_MS - 100);
  expect(asks()).toBe(1);                             // Not yet
  await later(200);

  expect(asks()).toBe(2);
  expect(screen.getAllByTestId('texts')[0]).toHaveTextContent("Last call in ten | Garden Toast is 86'd | Table W5 is up");
});

test('when the backend does not answer, the list on screen is kept and it asks again later', async () => {
  await renderProvider();
  failing.add('/api/notifications');

  await later(NOTIFICATIONS_REFRESH_MS);
  expect(screen.getAllByTestId('texts')[0]).toHaveTextContent("Garden Toast is 86'd | Table W5 is up");

  failing.clear();
  onBackend = [ORDER_UP];
  await later(NOTIFICATIONS_REFRESH_MS);
  expect(screen.getAllByTestId('texts')[0]).toHaveTextContent('Table W5 is up');
});

test('when the provider goes away (sign-out), the asking stops', async () => {
  const { unmount } = await renderProvider();
  unmount();

  await later(NOTIFICATIONS_REFRESH_MS * 3);

  expect(asks()).toBe(1);
});

test('dismissing takes the notification off the screen at once and tells the backend', async () => {
  await renderProvider();

  userEvent.click(screen.getAllByRole('button', { name: 'dismiss toast' })[0]);
  await settle();

  expect(screen.getAllByTestId('texts')[0]).toHaveTextContent('Table W5 is up');
  expect(calls).toContainEqual({ method: 'POST', path: '/api/notifications/3/dismiss', body: undefined });
  await later(NOTIFICATIONS_REFRESH_MS);              // And it stays gone at the next ask
  expect(screen.getAllByTestId('texts')[0]).toHaveTextContent('Table W5 is up');
});

test('if the backend could not be told, the notification comes back: nothing is shown as dismissed that is not', async () => {
  await renderProvider();
  failing.add('/api/notifications/3/dismiss');

  userEvent.click(screen.getAllByRole('button', { name: 'dismiss toast' })[0]);
  await settle();

  expect(asks()).toBe(2);                             // It asked for the truth again straight away
  expect(screen.getAllByTestId('texts')[0]).toHaveTextContent("Garden Toast is 86'd | Table W5 is up");
});

test('dismissing all empties the list and tells the backend once', async () => {
  await renderProvider();

  userEvent.click(screen.getAllByRole('button', { name: 'dismiss all' })[0]);
  await settle();

  expect(screen.getAllByTestId('texts')[0]).toHaveTextContent('none');
  expect(calls.filter(call => call.path === '/api/notifications/dismiss-all')).toHaveLength(1);
});

test('sending a message posts the words and reports that it was sent', async () => {
  lastSendOutcome = null;
  await renderProvider();

  userEvent.click(screen.getAllByRole('button', { name: 'send' })[0]);
  await settle();

  expect(calls).toContainEqual({ method: 'POST', path: '/api/notifications/messages', body: { text: 'Fryer is down' } });
  expect(lastSendOutcome).toBe('sent');
  // The writer is not shown their own message: the list is as it was
  expect(screen.getAllByTestId('texts')[0]).toHaveTextContent("Garden Toast is 86'd | Table W5 is up");
});

test('a message the backend refuses is reported as not sent', async () => {
  lastSendOutcome = null;
  await renderProvider();
  failing.add('/api/notifications/messages');

  userEvent.click(screen.getAllByRole('button', { name: 'send' })[0]);
  await settle();

  expect(lastSendOutcome).toBe('failed');
});
