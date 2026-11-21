import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Tables from './Tables';
import QuickOrders from '../QuickOrders/QuickOrders';

/**
 * Behavior tests for the Tables and Quick Orders screens.
 *
 * WHAT: Loads each screen against a fake backend holding one open check and
 *       one paid check, and checks what the ACTIVE and CLOSED views show.
 * WHY:  These are the screens a server reads a table's state from, and a
 *       manager reads a paid check from. The total once left the tax out, so
 *       a closed check showed less than the guest actually paid.
 *
 * The two screens share one component (TableChecks); one screen lists tables,
 * the other Quick Orders. Every test runs against both.
 *
 * The fake backend works the same way as in CreateOrder.test.jsx: global.fetch
 * is replaced with a function that returns canned JSON. Both screens read one
 * endpoint, GET /api/tables/overview, which returns the open checks and the
 * recent paid checks already matched up.
 */

let overviews;        // what GET /api/tables/overview returns, keyed by the quickOrders parameter
let calls;            // every request path, in order

// A paid check: two items, 33.00 + 0.99 tax, paid by credit card with a 6.80 tip
const closedCheck = (orderId, tableId, tableNumber, isQuickOrder) => ({
  orderId, tableId, tableNumber, isQuickOrder,
  serverName: 'CJ',
  createdAt: new Date().toISOString(),
  closedAt: new Date().toISOString(),
  subtotal: 33.0, tax: 0.99, total: 33.99,
  tipAmount: 6.8,
  paymentMethod: 'credit_card',
  items: [
    { orderItemId: orderId * 10 + 1, name: 'Omelette of the Day', quantity: 1, price: 16.0, specialInstructions: null, modifiers: [] },
    {
      orderItemId: orderId * 10 + 2, name: 'Combo Plate', quantity: 1, price: 17.0, specialInstructions: 'nut allergy',
      modifiers: [
        { orderItemId: orderId * 10 + 2, modifierId: 14, name: 'French Toast', priceAdjustment: 2.0 },
        { orderItemId: orderId * 10 + 2, modifierId: 16, name: 'Hashbrowns', priceAdjustment: 0 }
      ]
    }
  ]
});

// An open check: one item waiting on the kitchen, one finished, one still in its edit window. Opened 12 minutes ago.
const activeTable = (tableId, tableNumber, isQuickOrder) => ({
  tableId, tableNumber, isQuickOrder,
  status: 'occupied',
  serverName: 'Sarah',
  itemCount: 3, limboCount: 1, pendingCount: 1, firedCount: 0, completedCount: 1,
  subtotal: 50.0,                                                                 // 16 + 17 + 17
  openedAt: new Date(Date.now() - 12 * 60 * 1000).toISOString()
});

beforeEach(() => {
  calls = [];
  // Nothing is open to begin with; one check has been paid on the table and one on the Quick Order
  overviews = {
    false: { activeTables: [], closedChecks: [closedCheck(10, 1, 'W1', false)] },
    true: { activeTables: [], closedChecks: [closedCheck(20, 2, 'QO1', true)] }
  };

  global.fetch = vi.fn(async (url) => {
    const path = url.replace('http://localhost:8080', '');
    calls.push(path);
    const json = (data) => ({ ok: true, status: 200, json: async () => data });

    if (path === '/api/tables/overview?quickOrders=false') return json(overviews.false);
    if (path === '/api/tables/overview?quickOrders=true') return json(overviews.true);
    throw new Error(`Unexpected request in test: ${path}`);
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

// Both screens are checked the same way
const SCREENS = [
  ['Tables', Tables, 'W1'],
  ['Quick Orders', QuickOrders, 'QO1']
];

test.each(SCREENS)('%s: a closed check shows what the guest paid, tax and tip included', async (_name, Screen, tableNumber) => {
  render(<Screen setCurrentView={vi.fn()} setSelectedTable={vi.fn()} />);
  await userEvent.click(screen.getByRole('button', { name: 'CLOSED' }));

  // 33.00 + 0.99 tax + 6.80 tip = 40.79 (this used to show 39.80)
  const card = (await screen.findByRole('heading', { name: tableNumber })).closest('.table-card');
  expect(within(card).getByText('$40.79')).toBeInTheDocument();

  // The pop-up breaks it down, with a line for the tax
  await userEvent.click(card);
  const totals = document.querySelector('.modal-totals');
  const row = (label) => within(totals).getByText(label).closest('.modal-total-row');
  expect(within(row('Subtotal:')).getByText('$33.00')).toBeInTheDocument();
  expect(within(row('Tax:')).getByText('$0.99')).toBeInTheDocument();
  expect(within(row('Tip:')).getByText('$6.80')).toBeInTheDocument();
  expect(within(row('Total:')).getByText('$40.79')).toBeInTheDocument();
});

test.each(SCREENS)('%s: a closed check lists each item with its modifiers and kitchen note', async (_name, Screen, tableNumber) => {
  render(<Screen setCurrentView={vi.fn()} setSelectedTable={vi.fn()} />);
  await userEvent.click(screen.getByRole('button', { name: 'CLOSED' }));
  await userEvent.click((await screen.findByRole('heading', { name: tableNumber })).closest('.table-card'));

  const items = document.querySelector('.modal-items');
  const line = (name) => within(items).getByText(name).closest('.check-line');

  // The Combo Plate: its price, both modifiers (the upcharge beside the one that costs extra), and the note
  expect(within(line('Combo Plate')).getByText('$17.00')).toBeInTheDocument();
  expect(within(line('Combo Plate')).getByText('French Toast')).toBeInTheDocument();
  expect(within(line('Combo Plate')).getByText('+$2.00')).toBeInTheDocument();
  expect(within(line('Combo Plate')).getByText('Hashbrowns')).toBeInTheDocument();
  expect(within(line('Combo Plate')).getByText('NOTE: nut allergy')).toBeInTheDocument();

  // The plain item has neither
  expect(within(line('Omelette of the Day')).queryByText(/NOTE:/)).not.toBeInTheDocument();
  expect(line('Omelette of the Day').querySelector('.check-line-modifiers')).toBeNull();
});

test.each(SCREENS)('%s: a check with no payment on record shows an unknown method and no tip', async (name, Screen, tableNumber) => {
  const key = name === 'Quick Orders';
  overviews[key].closedChecks = [{ ...overviews[key].closedChecks[0], paymentMethod: null, tipAmount: 0 }];

  render(<Screen setCurrentView={vi.fn()} setSelectedTable={vi.fn()} />);
  await userEvent.click(screen.getByRole('button', { name: 'CLOSED' }));

  const card = (await screen.findByRole('heading', { name: tableNumber })).closest('.table-card');
  expect(within(card).getByText('$33.99')).toBeInTheDocument();
  expect(within(card).getByText('UNKNOWN')).toBeInTheDocument();
});

// ============================================================================
// ACTIVE view
// ============================================================================

// Seat the table (or Quick Order) with an open order
const seatWithOpenOrder = () => {
  overviews.false.activeTables = [activeTable(1, 'W1', false)];
  overviews.true.activeTables = [activeTable(2, 'QO1', true)];
};

test.each(SCREENS)('%s: an open check shows its amount, item counts, server, and age', async (_name, Screen, tableNumber) => {
  seatWithOpenOrder();
  render(<Screen setCurrentView={vi.fn()} setSelectedTable={vi.fn()} />);

  const card = (await screen.findByRole('heading', { name: tableNumber })).closest('.table-card');
  expect(within(card).getByText('$50.00')).toBeInTheDocument();                   // 16 + 17 + 17
  expect(within(card).getByText('3 items')).toBeInTheDocument();
  expect(within(card).getByText('1L 1P 1C')).toBeInTheDocument();                 // 1 in limbo, 1 pending, 1 completed
  expect(within(card).getByText('Server: Sarah')).toBeInTheDocument();
  expect(within(card).getByText('12m ago')).toBeInTheDocument();
  expect(card).toHaveClass('border-purple');                                      // Something is still in its edit window
});

test.each(SCREENS)('%s: tapping an open check opens that table on the order screen', async (_name, Screen, tableNumber) => {
  seatWithOpenOrder();
  const setCurrentView = vi.fn();
  const setSelectedTable = vi.fn();
  render(<Screen setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />);

  await userEvent.click((await screen.findByRole('heading', { name: tableNumber })).closest('.table-card'));

  expect(setSelectedTable).toHaveBeenCalledWith(expect.objectContaining({ tableNumber, status: 'occupied' }));
  expect(setCurrentView).toHaveBeenCalledWith('createOrder');
});

test.each(SCREENS)('%s: says so when nothing is open', async (name, Screen) => {
  render(<Screen setCurrentView={vi.fn()} setSelectedTable={vi.fn()} />);

  expect(await screen.findByText(name === 'Tables' ? 'No active tables' : 'No active Quick Orders')).toBeInTheDocument();
});

test.each(SCREENS)('%s: the card border follows the state of the kitchen', async (name, Screen, tableNumber) => {
  const key = name === 'Quick Orders';
  seatWithOpenOrder();
  // Everything on the check has been finished by the kitchen
  overviews[key].activeTables = [{ ...overviews[key].activeTables[0], limboCount: 0, pendingCount: 0, firedCount: 0, completedCount: 3 }];

  render(<Screen setCurrentView={vi.fn()} setSelectedTable={vi.fn()} />);

  const card = (await screen.findByRole('heading', { name: tableNumber })).closest('.table-card');
  expect(card).toHaveClass('border-green');
  expect(within(card).getByText('3C')).toBeInTheDocument();
});

test.each(SCREENS)('%s: the screen loads everything with one request', async (name, Screen, tableNumber) => {
  seatWithOpenOrder();
  render(<Screen setCurrentView={vi.fn()} setSelectedTable={vi.fn()} />);
  await screen.findByRole('heading', { name: tableNumber });

  // One request, for this screen's kind of table only.
  // (It used to be five: tables, orders, order items, payments, and menu items.)
  expect(calls).toEqual([`/api/tables/overview?quickOrders=${name === 'Quick Orders'}`]);
});
