import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ChangePin from './ChangePin';

/**
 * Behavior tests for the "choose a new PIN" screen.
 *
 * WHAT: Taps PINs through the three steps against a fake backend and checks
 *       what is sent and what the screen says.
 * WHY:  This is the only way past the starter PIN. A slip here (sending the
 *       wrong digits, accepting two PINs that differ) would lock the first
 *       manager out of a new restaurant's POS.
 *
 * The fake backend works the same way as in SignIn.test.jsx: global.fetch is
 * replaced with a function that returns canned JSON.
 */

let changesSent;      // every { currentPin, newPin } the screen sent to the backend
let answer;           // what the fake backend answers: { status, body }, or 'offline' to fail the request

const firstManager = { staffId: 1, name: 'Manager', role: 'manager', isActive: true, mustChangePin: true };

beforeEach(() => {
  changesSent = [];
  answer = { status: 200, body: { ...firstManager, mustChangePin: false } };

  global.fetch = vi.fn(async (url, options = {}) => {
    const path = url.replace('http://localhost:8080', '');
    if (options.method === 'PUT' && path === '/api/staff/me/pin') {
      changesSent.push(JSON.parse(options.body));
      if (answer === 'offline') throw new TypeError('Failed to fetch');
      return { ok: answer.status < 300, status: answer.status, json: async () => answer.body };
    }
    throw new Error(`Unexpected request in test: ${path}`);
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

// Tap a PIN on the pad, one digit at a time
const typePin = async (pin) => {
  for (const digit of pin) {
    await userEvent.click(screen.getByRole('button', { name: digit }));
  }
};
const heading = () => screen.getByRole('heading').textContent;
const filledDots = () => document.querySelectorAll('.pin-dot.filled').length;

test('it asks for the current PIN, the new PIN, and the new PIN again, then sends both', async () => {
  const onPinChanged = vi.fn();
  render(<ChangePin staff={firstManager} onPinChanged={onPinChanged} onSignOut={vi.fn()} />);

  // It says why it is here
  expect(screen.getByText(/You signed in with the starter PIN/)).toBeInTheDocument();

  expect(heading()).toBe('ENTER YOUR CURRENT PIN');
  await typePin('1234');

  await waitFor(() => expect(heading()).toBe('CHOOSE A NEW PIN'));
  expect(filledDots()).toBe(0);                                     // The pad emptied itself for the next PIN
  expect(screen.getByRole('alert')).toBeEmptyDOMElement();          // Nothing was sent, so nothing says "Saving..."
  await typePin('4821');

  await waitFor(() => expect(heading()).toBe('ENTER THE NEW PIN AGAIN'));
  expect(changesSent).toEqual([]);                                  // Nothing is sent until the new PIN has been typed twice
  await typePin('4821');

  await waitFor(() => expect(changesSent).toEqual([{ currentPin: '1234', newPin: '4821' }]));
  await waitFor(() => expect(onPinChanged).toHaveBeenCalledWith({ ...firstManager, mustChangePin: false }));
});

test('when the two new PINs differ, nothing is sent and it asks for a new PIN again', async () => {
  const onPinChanged = vi.fn();
  render(<ChangePin staff={firstManager} onPinChanged={onPinChanged} onSignOut={vi.fn()} />);

  await typePin('1234');
  await waitFor(() => expect(heading()).toBe('CHOOSE A NEW PIN'));
  await typePin('4821');
  await waitFor(() => expect(heading()).toBe('ENTER THE NEW PIN AGAIN'));
  await typePin('4812');                                            // Two digits swapped

  await waitFor(() => expect(heading()).toBe('CHOOSE A NEW PIN'));
  expect(screen.getByRole('alert')).toHaveTextContent('Those two did not match. Choose a new PIN.');
  expect(changesSent).toEqual([]);
  expect(onPinChanged).not.toHaveBeenCalled();

  // The current PIN does not have to be typed again: choosing and confirming is enough
  await typePin('4821');
  await waitFor(() => expect(heading()).toBe('ENTER THE NEW PIN AGAIN'));
  await typePin('4821');
  await waitFor(() => expect(changesSent).toEqual([{ currentPin: '1234', newPin: '4821' }]));
});

test('a new PIN that is the same as the current one is turned away without asking the backend', async () => {
  render(<ChangePin staff={firstManager} onPinChanged={vi.fn()} onSignOut={vi.fn()} />);

  await typePin('1234');
  await waitFor(() => expect(heading()).toBe('CHOOSE A NEW PIN'));
  await typePin('1234');

  await waitFor(() =>
    expect(screen.getByRole('alert')).toHaveTextContent('The new PIN must be different from the current one.')
  );
  expect(heading()).toBe('CHOOSE A NEW PIN');                       // Still on the same step
  expect(changesSent).toEqual([]);
});

test('when the backend refuses, its message is shown and the screen starts again from the current PIN', async () => {
  answer = { status: 409, body: { message: 'That is not your current PIN.' } };
  const onPinChanged = vi.fn();
  render(<ChangePin staff={firstManager} onPinChanged={onPinChanged} onSignOut={vi.fn()} />);

  await typePin('9999');
  await waitFor(() => expect(heading()).toBe('CHOOSE A NEW PIN'));
  await typePin('4821');
  await waitFor(() => expect(heading()).toBe('ENTER THE NEW PIN AGAIN'));
  await typePin('4821');

  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('That is not your current PIN.'));
  expect(heading()).toBe('ENTER YOUR CURRENT PIN');
  expect(onPinChanged).not.toHaveBeenCalled();

  // Typing again clears the message, and the second try goes through with the right PIN
  answer = { status: 200, body: { ...firstManager, mustChangePin: false } };
  await typePin('1');
  expect(screen.getByRole('alert')).toBeEmptyDOMElement();
  await typePin('234');
  await waitFor(() => expect(heading()).toBe('CHOOSE A NEW PIN'));
  await typePin('4821');
  await waitFor(() => expect(heading()).toBe('ENTER THE NEW PIN AGAIN'));
  await typePin('4821');

  await waitFor(() => expect(onPinChanged).toHaveBeenCalled());
  expect(changesSent).toEqual([
    { currentPin: '9999', newPin: '4821' },
    { currentPin: '1234', newPin: '4821' }
  ]);
});

test('when the backend cannot be reached, the screen says so and can be tried again', async () => {
  vi.spyOn(console, 'error').mockImplementation(() => {});    // The failure is logged on purpose - keep the test output clean
  answer = 'offline';
  render(<ChangePin staff={firstManager} onPinChanged={vi.fn()} onSignOut={vi.fn()} />);

  await typePin('1234');
  await waitFor(() => expect(heading()).toBe('CHOOSE A NEW PIN'));
  await typePin('4821');
  await waitFor(() => expect(heading()).toBe('ENTER THE NEW PIN AGAIN'));
  await typePin('4821');

  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Cannot reach the server'));
  expect(heading()).toBe('ENTER YOUR CURRENT PIN');
  expect(screen.getByRole('button', { name: '1' })).toBeEnabled();  // The pad is usable again
});

test('SIGN OUT is offered, for someone who is not the person signed in', async () => {
  const onSignOut = vi.fn();
  render(<ChangePin staff={firstManager} onPinChanged={vi.fn()} onSignOut={onSignOut} />);

  await userEvent.click(screen.getByRole('button', { name: 'SIGN OUT' }));

  expect(onSignOut).toHaveBeenCalled();
  expect(changesSent).toEqual([]);
});
