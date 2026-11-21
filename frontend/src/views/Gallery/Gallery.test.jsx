import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Gallery from './Gallery';
import { TEMPLATES, forgetTemplates, showChosenTemplate } from '../../templates';
import { TEST_LOOK } from '../../templates/testSupport';

/**
 * Behavior tests for the template gallery (managers only).
 *
 * WHAT: Draws the gallery and checks that every state it promises is really on the
 *       page, drawn by the app's own components, and that its switcher previews a
 *       template on this device only.
 * WHY:  The gallery is how thirteen templates get judged. A state that is missing
 *       from it is a state nobody looks at before a template ships.
 *
 * The gallery reads nothing from the backend and sends nothing to it, so fetch is
 * replaced with one that fails the test if it is ever called.
 *
 * What the pieces LOOK like cannot be tested here (the test browser applies no
 * stylesheets). These tests check that each piece is there in each state, by the class
 * or the words that state has on the real screens.
 */

const onThePage = () => document.documentElement.getAttribute('data-template');
const piece = (title) => screen.getByRole('region', { name: title });

beforeEach(() => {
  TEMPLATES.push(TEST_LOOK);
  window.localStorage.clear();
  forgetTemplates();
  showChosenTemplate('8-bit');
  global.fetch = vi.fn(async (url) => { throw new Error(`Unexpected request in test: ${url}`); });
});

afterEach(() => {
  TEMPLATES.pop();
  forgetTemplates();
  vi.restoreAllMocks();
});

const renderGallery = (setCurrentView = vi.fn()) =>
  render(<Gallery setCurrentView={setCurrentView} setSelectedTable={vi.fn()} />);

test('an order line is shown in each of its states', () => {
  renderGallery();
  const lines = [...piece('AN ORDER').querySelectorAll('.order-item')];

  expect(lines).toHaveLength(5);
  // New and in-the-edit-window lines can still be changed; a saved draft is marked; the two
  // sent ones are locked, with a lock and no way to edit or remove them
  expect(lines.map(line => line.className.replace('order-item', '').trim())).toEqual(['', 'saved-draft', '', 'locked', 'locked']);
  expect(within(lines[0]).getByRole('button', { name: /^Edit/ })).toBeInTheDocument();
  expect(within(lines[3]).queryByRole('button')).not.toBeInTheDocument();
  expect(lines[3].querySelector('.lock-icon')).not.toBeNull();
  // Modifiers and a kitchen note are on the page too
  expect(within(lines[1]).getByText('NOTE: no onions')).toBeInTheDocument();
  expect(within(lines[1]).getByText('French Toast')).toBeInTheDocument();

  // And the message bar in its four kinds
  for (const kind of ['default', 'success', 'timer', 'locked']) {
    expect(piece('AN ORDER').querySelector(`.notification-bar.notification-${kind}`)).not.toBeNull();
  }
});

test('the menu shows items that can be rung in and one that is 86\'d', () => {
  renderGallery();
  const menu = piece('THE MENU');

  const garden = within(menu).getByRole('heading', { name: 'Garden Toast' }).closest('.menu-item-card');
  expect(garden).toHaveClass('is-86d');
  expect(within(garden).getByText("86'D")).toBeInTheDocument();
  expect(within(menu).getByRole('heading', { name: 'Omelette of the Day' }).closest('.menu-item-card')).not.toHaveClass('is-86d');
});

test('kitchen tickets are shown on time, at the warning, late, and finished', () => {
  renderGallery();
  const tickets = piece('KITCHEN TICKETS');
  const wait = (text) => within(tickets).getByText(text);

  expect(wait('3 MIN')).not.toHaveClass('warning');
  expect(wait('3 MIN')).not.toHaveClass('late');
  expect(wait('12 MIN')).toHaveClass('warning');
  expect(wait('20 MIN')).toHaveClass('late');
  expect(within(tickets).getByText('DONE').closest('.order-card')).toHaveClass('done');
  // START and COMPLETE are there to be looked at
  expect(within(tickets).getAllByRole('button', { name: 'START' }).length).toBeGreaterThan(0);
  expect(within(tickets).getAllByRole('button', { name: 'COMPLETE' }).length).toBeGreaterThan(0);
});

test('an open check\'s card is shown with each border it can have', () => {
  renderGallery();
  const cards = [...piece('OPEN CHECKS').querySelectorAll('.table-card')];

  expect(cards.map(card => card.className.replace('table-card', '').trim()))
    .toEqual(['border-purple', 'border-yellow', 'border-green', 'border-default']);
  expect(within(cards[0]).getByText('12m ago')).toBeInTheDocument();
});

test('the floor shows tables available and occupied, square and round, and one selected', () => {
  renderGallery();
  const tables = [...piece('THE FLOOR').querySelectorAll('.table-visual')];

  expect(tables).toHaveLength(4);
  expect(tables.filter(table => table.classList.contains('available'))).toHaveLength(2);
  expect(tables.filter(table => table.classList.contains('occupied'))).toHaveLength(2);
  expect(tables.filter(table => table.classList.contains('circle'))).toHaveLength(2);
  expect(tables.filter(table => table.classList.contains('selected'))).toHaveLength(1);
});

test('the 86 board, the PIN pad, payment, messages, and paused terminals are all there', () => {
  renderGallery();

  const board = piece('THE 86 BOARD');
  expect(within(board).getByRole('button', { name: 'Put Garden Toast back on' })).toBeInTheDocument();
  expect(board.querySelector('.eighty-six-item.is-86d')).not.toBeNull();
  expect(board.querySelector('.eighty-six-item:not(.is-86d)')).not.toBeNull();

  expect(within(piece('THE PIN PAD')).getByRole('alert')).toHaveTextContent('PIN not recognized.');
  expect(within(piece('THE PIN PAD')).getByRole('button', { name: '5' })).toBeInTheDocument();

  const payment = piece('TAKING PAYMENT');
  expect(within(payment).getByText('GRAND TOTAL:')).toBeInTheDocument();
  expect(within(payment).getByRole('button', { name: 'PROCESS PAYMENT' })).toBeInTheDocument();
  expect(within(payment).getByRole('button', { name: /CREDIT/ })).toHaveClass('active');

  const messages = piece('MESSAGES AND PAUSED TERMINALS');
  expect(messages.querySelector('.floor-error')).not.toBeNull();
  expect(within(messages).getByText('THIS TERMINAL')).toBeInTheDocument();
  expect(within(messages).getByText('5 minutes left')).toBeInTheDocument();

  expect(within(piece('LOGO AND TEXT')).getByRole('img', { name: 'NOTTA-POS Logo' })).toBeInTheDocument();
  expect(piece('BUTTONS').querySelector('.btn-send')).not.toBeNull();
  expect(piece('BUTTONS').querySelector('.btn-close-order:disabled')).not.toBeNull();
});

test('pressing things in the gallery touches no order and asks nothing of the backend', async () => {
  renderGallery();

  await userEvent.click(within(piece('KITCHEN TICKETS')).getAllByRole('button', { name: 'START' })[0]);
  await userEvent.click(within(piece('TAKING PAYMENT')).getByRole('button', { name: 'PROCESS PAYMENT' }));
  await userEvent.click(within(piece('MESSAGES AND PAUSED TERMINALS')).getAllByRole('button', { name: /^Release/ })[0]);
  await userEvent.click(piece('BUTTONS').querySelector('.btn-send'));
  // The things that only change how the gallery itself looks do work
  await userEvent.click(within(piece('TAKING PAYMENT')).getByRole('button', { name: /CASH/ }));
  expect(within(piece('TAKING PAYMENT')).getByRole('button', { name: /CASH/ })).toHaveClass('active');

  expect(global.fetch).not.toHaveBeenCalled();
});

test('the switcher previews a template on this device, and the restaurant\'s own needs no preview', async () => {
  renderGallery();
  const switcher = screen.getByRole('group', { name: 'Template on screen' });

  expect(within(switcher).getByRole('button', { name: '8-BIT (IN USE)' })).toHaveAttribute('aria-pressed', 'true');

  await userEvent.click(within(switcher).getByRole('button', { name: 'TEST LOOK' }));
  expect(onThePage()).toBe('test-look');
  expect(within(switcher).getByRole('button', { name: 'TEST LOOK' })).toHaveAttribute('aria-pressed', 'true');
  expect(window.localStorage.getItem('notta.template')).toBe('8-bit');        // A preview: nothing is remembered or sent
  // The logo is the name as text in this template
  expect(within(piece('LOGO AND TEXT')).getByRole('img', { name: 'NOTTA-POS Logo' }).tagName).toBe('DIV');

  await userEvent.click(within(switcher).getByRole('button', { name: '8-BIT (IN USE)' }));
  expect(onThePage()).toBe('8-bit');
  expect(global.fetch).not.toHaveBeenCalled();
});

test('BACK TO SETTINGS goes back to Settings', async () => {
  const setCurrentView = vi.fn();
  renderGallery(setCurrentView);

  await userEvent.click(screen.getByRole('button', { name: 'BACK TO SETTINGS' }));

  expect(setCurrentView).toHaveBeenCalledWith('settings');
});
