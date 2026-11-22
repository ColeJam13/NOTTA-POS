import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import NotificationBell from './NotificationBell';
import { NotificationsContext } from '../../context/NotificationsContext';
import { StaffContext } from '../../context/StaffContext';

/**
 * Behavior tests for the bell in the top bar and the panel under it.
 *
 * WHAT: Draws the bell with a list of notifications handed to it, and checks the
 *       number it shows, what the panel lists, and what dismissing and writing a
 *       message do.
 * WHY:  The bell showed a 2 that was typed into the code, on every terminal, for
 *       everyone. It must now show the backend's number or nothing, and the panel
 *       must offer a role only what that role may do.
 *
 * Where the list comes from (asking the backend every few seconds) is
 * NotificationsContext's job, tested in context/NotificationsContext.test.jsx. Here
 * the context is filled in by the test, so no request is made.
 */

const minutesAgo = (minutes) => new Date(Date.now() - minutes * 60 * 1000).toISOString();

const TOAST_86 = { notificationId: 3, kind: 'item_86', text: "Garden Toast is 86'd", fromName: 'Kit', createdAt: minutesAgo(5) };
const ORDER_UP = { notificationId: 2, kind: 'order_up', text: 'Table W5 is up', fromName: 'Kit', createdAt: minutesAgo(12) };
const MESSAGE = { notificationId: 1, kind: 'message', text: 'Fryer is down for 10 minutes', fromName: 'Dan', createdAt: minutesAgo(40) };
const BACK_ON = { notificationId: 4, kind: 'item_back', text: 'Garden Toast is back on', fromName: null, createdAt: minutesAgo(1) };

// The bell as a given role sees it, holding the given notifications
const renderBell = (notifications, role = 'server', actions = {}) => {
  const value = {
    notifications,
    dismiss: vi.fn(async () => {}),
    dismissAll: vi.fn(async () => {}),
    sendMessage: vi.fn(async () => {}),
    ...actions
  };
  render(
    <StaffContext.Provider value={{ staff: { staffId: 7, name: 'Sarah', role }, signOut: () => {} }}>
      <NotificationsContext.Provider value={value}>
        <NotificationBell />
        <button>somewhere else</button>
      </NotificationsContext.Provider>
    </StaffContext.Provider>
  );
  return value;
};

const bell = () => screen.getByRole('button', { name: /^Notifications:/ });
const openPanel = async () => { await userEvent.click(bell()); return screen.getByRole('dialog', { name: 'Notifications' }); };

// ============================================================================
// The number on the bell
// ============================================================================

test('the bell shows how many notifications the person has', () => {
  renderBell([TOAST_86, ORDER_UP, MESSAGE]);

  expect(bell()).toHaveAccessibleName('Notifications: 3');
  expect(bell().querySelector('.notifications-badge')).toHaveTextContent('3');
});

test('with none there is no number at all: not a 0, and not a leftover', () => {
  renderBell([]);

  expect(bell()).toHaveAccessibleName('Notifications: none');
  expect(bell().querySelector('.notifications-badge')).toBeNull();
  expect(bell()).toHaveTextContent('');               // Nothing is written on the bell: only its icon
});

// ============================================================================
// The panel
// ============================================================================

test('pressing the bell lists each notification with a word for its kind, its text, who, and how long ago', async () => {
  renderBell([BACK_ON, TOAST_86, ORDER_UP, MESSAGE]);
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

  const panel = await openPanel();
  // Each row as its three parts: the kind, the text, and who and when
  const part = (row, name) => row.querySelector(`.notification-${name}`).textContent.trim();
  const rows = [...panel.querySelectorAll('.notification-row')].map(row => [part(row, 'kind'), part(row, 'text'), part(row, 'meta')]);

  // Newest first, as the backend sent them. Each kind has a word; the kind is never told by color alone.
  expect(rows).toEqual([
    ['BACK ON', 'Garden Toast is back on', '1m ago'],                 // Nobody on record: just the time
    ["86'D", "Garden Toast is 86'd", 'Kit · 5m ago'],
    ['ORDER UP', 'Table W5 is up', 'Kit · 12m ago'],
    ['MESSAGE', 'Fryer is down for 10 minutes', 'Dan · 40m ago']
  ]);
  // ...and an icon beside the word
  expect(panel.querySelectorAll('.notification-kind svg')).toHaveLength(4);
  expect(bell()).toHaveAttribute('aria-expanded', 'true');
});

test('with none the panel says so in words', async () => {
  renderBell([]);

  const panel = await openPanel();

  expect(within(panel).getByText('No notifications.')).toBeInTheDocument();
  expect(within(panel).queryByRole('button', { name: 'DISMISS ALL' })).not.toBeInTheDocument();
});

test('a kind this screen does not know is still shown, as a message', async () => {
  renderBell([{ notificationId: 9, kind: 'something_new', text: 'A notice from a newer backend', fromName: null, createdAt: minutesAgo(2) }]);

  const panel = await openPanel();

  expect(within(panel).getByText('A notice from a newer backend')).toBeInTheDocument();
  expect(within(panel).getByText('MESSAGE')).toBeInTheDocument();
});

test('the panel closes on a second press, on Escape, and on a click anywhere else', async () => {
  renderBell([TOAST_86]);

  await openPanel();
  await userEvent.click(bell());
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

  await openPanel();
  await userEvent.keyboard('{Escape}');
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

  await openPanel();
  await userEvent.click(screen.getByRole('button', { name: 'somewhere else' }));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  expect(bell()).toHaveAttribute('aria-expanded', 'false');
});

// ============================================================================
// Dismissing
// ============================================================================

test('the x on a notification dismisses that one', async () => {
  const { dismiss, dismissAll } = renderBell([TOAST_86, ORDER_UP]);
  await openPanel();

  await userEvent.click(screen.getByRole('button', { name: 'Dismiss: Table W5 is up' }));

  expect(dismiss).toHaveBeenCalledTimes(1);
  expect(dismiss).toHaveBeenCalledWith(2);
  expect(dismissAll).not.toHaveBeenCalled();
  expect(screen.getByRole('dialog')).toBeInTheDocument();          // The panel stays open for the next one
});

test('DISMISS ALL dismisses the whole list', async () => {
  const { dismiss, dismissAll } = renderBell([TOAST_86, ORDER_UP, MESSAGE]);
  await openPanel();

  await userEvent.click(screen.getByRole('button', { name: 'DISMISS ALL' }));

  expect(dismissAll).toHaveBeenCalledTimes(1);
  expect(dismiss).not.toHaveBeenCalled();
});

// ============================================================================
// Writing a message
// ============================================================================

test('the kitchen, bartenders, and managers are offered a box to write a message; servers and hosts are not', async () => {
  for (const [role, offered] of [['kitchen', true], ['bartender', true], ['manager', true], ['server', false], ['host', false]]) {
    const { unmount } = render(
      <StaffContext.Provider value={{ staff: { staffId: 1, name: 'Someone', role }, signOut: () => {} }}>
        <NotificationsContext.Provider value={{ notifications: [], dismiss: vi.fn(), dismissAll: vi.fn(), sendMessage: vi.fn() }}>
          <NotificationBell />
        </NotificationsContext.Provider>
      </StaffContext.Provider>
    );
    await userEvent.click(bell());

    expect(screen.queryByLabelText('Message to everyone') !== null, role).toBe(offered);
    unmount();
  }
});

test('SEND sends what was written, empties the box, and says it was sent', async () => {
  const { sendMessage } = renderBell([], 'kitchen');
  await openPanel();
  const box = screen.getByLabelText('Message to everyone');
  expect(screen.getByRole('button', { name: 'SEND' })).toBeDisabled();           // Nothing written yet

  await userEvent.type(box, '  Fryer is down for 10 minutes ');
  expect(screen.getByText('31 / 200')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'SEND' }));

  expect(sendMessage).toHaveBeenCalledWith('Fryer is down for 10 minutes');      // Without the spaces around it
  expect(await screen.findByRole('status')).toHaveTextContent('Sent to everyone else.');
  expect(box).toHaveValue('');
});

test('Enter in the box sends too', async () => {
  const { sendMessage } = renderBell([], 'manager');
  await openPanel();

  await userEvent.type(screen.getByLabelText('Message to everyone'), 'Last call in ten{Enter}');

  expect(sendMessage).toHaveBeenCalledWith('Last call in ten');
});

test('if the message could not be sent, it says so and keeps what was written', async () => {
  const sendMessage = vi.fn(async () => { throw new Error('POST /api/notifications/messages failed with status 500'); });
  renderBell([], 'bartender', { sendMessage });
  await openPanel();
  const box = screen.getByLabelText('Message to everyone');

  await userEvent.type(box, 'Out of limes');
  await userEvent.click(screen.getByRole('button', { name: 'SEND' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('That was not sent. Try again.');
  expect(box).toHaveValue('Out of limes');
  expect(screen.queryByRole('status')).not.toBeInTheDocument();
});

test('the box takes 200 characters and no more', async () => {
  renderBell([], 'kitchen');
  await openPanel();
  const box = screen.getByLabelText('Message to everyone');

  expect(box).toHaveAttribute('maxLength', '200');
  await userEvent.type(box, 'x'.repeat(205));
  expect(box.value).toHaveLength(200);
  expect(screen.getByText('200 / 200')).toBeInTheDocument();
});

test('only spaces is not a message', async () => {
  const { sendMessage } = renderBell([], 'kitchen');
  await openPanel();

  await userEvent.type(screen.getByLabelText('Message to everyone'), '    {Enter}');

  expect(screen.getByRole('button', { name: 'SEND' })).toBeDisabled();
  expect(sendMessage).not.toHaveBeenCalled();
});
