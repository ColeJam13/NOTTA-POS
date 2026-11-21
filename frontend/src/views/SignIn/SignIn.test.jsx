import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SignIn from './SignIn';

/**
 * Behavior tests for the PIN pad.
 *
 * WHAT: Taps and types PINs against a fake backend and checks what is sent
 *       and what the pad shows.
 * WHY:  Every person signs in here many times a shift. It has to send the PIN
 *       on the fourth digit, recover cleanly from a wrong one, and never show
 *       the digits.
 *
 * The fake backend works the same way as in CreateOrder.test.jsx: global.fetch
 * is replaced with a function that returns canned JSON.
 */

let pinsSent;         // every PIN the pad sent to the backend
let answer;           // what the fake backend answers: { status, body }, or 'offline' to fail the request

beforeEach(() => {
  pinsSent = [];
  answer = { status: 200, body: { staff: { staffId: 1, name: 'Sarah', role: 'server', isActive: true }, token: 'a-token' } };

  global.fetch = vi.fn(async (url, options = {}) => {
    const path = url.replace('http://localhost:8080', '');
    if (options.method === 'POST' && path === '/api/staff/sign-in') {
      pinsSent.push(JSON.parse(options.body).pin);
      if (answer === 'offline') throw new TypeError('Failed to fetch');
      return { ok: answer.status < 300, status: answer.status, json: async () => answer.body };
    }
    throw new Error(`Unexpected request in test: ${path}`);
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

const tap = (label) => userEvent.click(screen.getByRole('button', { name: label }));
const dots = () => screen.getByRole('status');
const filledDots = () => document.querySelectorAll('.pin-dot.filled').length;

test('the fourth digit sends the PIN and hands back who signed in', async () => {
  const onSignedIn = vi.fn();
  render(<SignIn onSignedIn={onSignedIn} />);

  await tap('1');
  await tap('2');
  await tap('3');
  expect(pinsSent).toEqual([]);                                     // Three digits: nothing sent yet
  expect(filledDots()).toBe(3);

  await tap('4');

  await waitFor(() => expect(onSignedIn).toHaveBeenCalledWith({
    staff: { staffId: 1, name: 'Sarah', role: 'server', isActive: true },
    token: 'a-token'
  }));
  expect(pinsSent).toEqual(['1234']);
});

test('the digits are never shown, only how many have been typed', async () => {
  render(<SignIn onSignedIn={vi.fn()} />);

  await tap('7');
  await tap('0');

  expect(dots()).toHaveAccessibleName('2 of 4 digits entered');
  expect(screen.queryByText('70')).not.toBeInTheDocument();
  expect(document.querySelector('.pin-dots').textContent).toBe('');
});

test('the delete key removes one digit and CLEAR removes them all', async () => {
  render(<SignIn onSignedIn={vi.fn()} />);
  await tap('1');
  await tap('2');
  await tap('3');

  await tap('Delete last digit');
  expect(filledDots()).toBe(2);

  await tap('CLEAR');
  expect(filledDots()).toBe(0);

  // The PIN that ends up being sent has none of the removed digits in it
  await tap('5');
  await tap('6');
  await tap('7');
  await tap('8');
  await waitFor(() => expect(pinsSent).toEqual(['5678']));
});

test('a keyboard works too: digits type and Backspace removes one', async () => {
  const onSignedIn = vi.fn();
  render(<SignIn onSignedIn={onSignedIn} />);

  fireEvent.keyDown(document, { key: '4' });
  fireEvent.keyDown(document, { key: '9' });
  fireEvent.keyDown(document, { key: 'Backspace' });
  fireEvent.keyDown(document, { key: 'x' });                       // Not a digit: ignored
  expect(filledDots()).toBe(1);

  fireEvent.keyDown(document, { key: '3' });
  fireEvent.keyDown(document, { key: '2' });
  fireEvent.keyDown(document, { key: '1' });

  await waitFor(() => expect(pinsSent).toEqual(['4321']));
  await waitFor(() => expect(onSignedIn).toHaveBeenCalled());
});

test('a wrong PIN shows the backend\'s message and empties the pad for another try', async () => {
  answer = { status: 401, body: { message: 'PIN not recognized.' } };
  const onSignedIn = vi.fn();
  render(<SignIn onSignedIn={onSignedIn} />);

  await tap('9');
  await tap('9');
  await tap('9');
  await tap('9');

  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('PIN not recognized.'));
  expect(filledDots()).toBe(0);
  expect(onSignedIn).not.toHaveBeenCalled();

  // Typing again clears the message
  await tap('1');
  expect(screen.getByRole('alert')).toHaveTextContent('');
});

test('after too many wrong PINs the pad shows the wait message', async () => {
  answer = { status: 429, body: { message: 'Too many wrong PINs. Wait 30 seconds and try again.' } };
  render(<SignIn onSignedIn={vi.fn()} />);

  await tap('1');
  await tap('1');
  await tap('1');
  await tap('1');

  await waitFor(() =>
    expect(screen.getByRole('alert')).toHaveTextContent('Too many wrong PINs. Wait 30 seconds and try again.')
  );
});

test('when the backend cannot be reached, the pad says so instead of doing nothing', async () => {
  vi.spyOn(console, 'error').mockImplementation(() => {});    // The failure is logged on purpose - keep the test output clean
  answer = 'offline';
  render(<SignIn onSignedIn={vi.fn()} />);

  await tap('1');
  await tap('2');
  await tap('3');
  await tap('4');

  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Cannot reach the server'));
  expect(filledDots()).toBe(0);
  expect(screen.getByRole('button', { name: '1' })).toBeEnabled();  // The pad is usable again
});
