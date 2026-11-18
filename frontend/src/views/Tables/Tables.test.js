import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Tables from './Tables';
import QuickOrders from '../QuickOrders/QuickOrders';

/**
 * Behavior tests for the CLOSED view of the Tables and Quick Orders screens.
 *
 * WHAT: Loads each screen against a fake backend holding one paid check, and
 *       checks the amounts shown on its card and in its pop-up.
 * WHY:  The total used to be subtotal + tip, with the tax left out, so a
 *       closed check showed less than the guest actually paid.
 *
 * The two screens share the same layout (one lists tables, the other Quick
 * Orders), so one fake backend serves both.
 *
 * The fake backend works the same way as in CreateOrder.test.js: global.fetch
 * is replaced with a function that returns canned JSON.
 */

const MENU = [
  { menuItemId: 1, name: 'Omelette of the Day', price: 16.0, category: 'Savory' },
  { menuItemId: 2, name: 'Mushroom Gravy', price: 17.0, category: 'Savory' }
];

// One table and one Quick Order, each with a closed order of the same two items
const TABLES = [{ tableId: 1, tableNumber: 'W1', status: 'available', isQuickOrder: false }];
const QUICK_ORDER_TABLES = [{ tableId: 2, tableNumber: 'QO1', status: 'available', isQuickOrder: true }];

let orders;
let tablesNow;            // what GET /api/tables?quickOrders=false returns
let quickOrderTablesNow;  // what GET /api/tables?quickOrders=true returns
let orderItemsNow;        // what GET /api/order-items returns

const ORDER_ITEMS = [
  { orderItemId: 1, orderId: 10, menuItemId: 1, price: 16.0, quantity: 1, status: 'completed' },
  { orderItemId: 2, orderId: 10, menuItemId: 2, price: 17.0, quantity: 1, status: 'completed' },
  { orderItemId: 3, orderId: 20, menuItemId: 1, price: 16.0, quantity: 1, status: 'completed' },
  { orderItemId: 4, orderId: 20, menuItemId: 2, price: 17.0, quantity: 1, status: 'completed' }
];

// Paid by credit card: the check (33.99 with tax) plus a 6.80 tip
const PAYMENTS = [
  { paymentId: 1, orderId: 10, amount: 33.99, tipAmount: 6.8, paymentMethod: 'credit_card' },
  { paymentId: 2, orderId: 20, amount: 33.99, tipAmount: 6.8, paymentMethod: 'credit_card' }
];

beforeEach(() => {
  const closedAt = new Date().toISOString();
  orders = [
    // Closed after the backend started recording the check on the order
    { orderId: 10, tableId: 1, status: 'closed', serverName: 'CJ', subtotal: 33.0, tax: 0.99, total: 33.99, createdAt: closedAt, closedAt },
    { orderId: 20, tableId: 2, status: 'closed', serverName: 'CJ', subtotal: 33.0, tax: 0.99, total: 33.99, createdAt: closedAt, closedAt }
  ];

  tablesNow = TABLES;
  quickOrderTablesNow = QUICK_ORDER_TABLES;
  orderItemsNow = ORDER_ITEMS;

  global.fetch = jest.fn(async (url) => {
    const path = url.replace('http://localhost:8080', '');
    const json = (data) => ({ ok: true, status: 200, json: async () => data });

    if (path === '/api/tables?quickOrders=false') return json(tablesNow);
    if (path === '/api/tables?quickOrders=true') return json(quickOrderTablesNow);
    if (path === '/api/orders') return json(orders);
    if (path === '/api/order-items') return json(orderItemsNow);
    if (path === '/api/payments') return json(PAYMENTS);
    if (path === '/api/menu-items') return json(MENU);
    throw new Error(`Unexpected request in test: ${path}`);
  });
});

afterEach(() => {
  jest.restoreAllMocks();
});

// Both screens are checked the same way
const SCREENS = [
  ['Tables', Tables, 'W1'],
  ['Quick Orders', QuickOrders, 'QO1']
];

test.each(SCREENS)('%s: a closed check shows what the guest paid, tax and tip included', async (_name, Screen, tableNumber) => {
  render(<Screen setCurrentView={jest.fn()} setSelectedTable={jest.fn()} />);
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

test.each(SCREENS)('%s: a check closed before totals were recorded still shows its tax', async (_name, Screen, tableNumber) => {
  // Older closed orders hold zeros for subtotal, tax, and total
  orders = orders.map(order => ({ ...order, subtotal: 0, tax: 0, total: 0 }));

  render(<Screen setCurrentView={jest.fn()} setSelectedTable={jest.fn()} />);
  await userEvent.click(screen.getByRole('button', { name: 'CLOSED' }));

  // Worked out from the items instead: the same 40.79
  const card = (await screen.findByRole('heading', { name: tableNumber })).closest('.table-card');
  expect(within(card).getByText('$40.79')).toBeInTheDocument();
});

// ============================================================================
// ACTIVE view
// ============================================================================

// Seat the table (or Quick Order) with an open order: one item waiting on the kitchen, one finished, one still in its edit window
const seatWithOpenOrder = () => {
  const openedAt = new Date(Date.now() - 12 * 60 * 1000).toISOString();          // 12 minutes ago
  tablesNow = [{ tableId: 1, tableNumber: 'W1', status: 'occupied', isQuickOrder: false, serverName: 'Sarah' }];
  quickOrderTablesNow = [{ tableId: 2, tableNumber: 'QO1', status: 'occupied', isQuickOrder: true, serverName: 'Sarah' }];
  orders = [
    { orderId: 30, tableId: 1, status: 'open', serverName: 'Sarah', subtotal: 0, tax: 0, total: 0, createdAt: openedAt, closedAt: null },
    { orderId: 40, tableId: 2, status: 'open', serverName: 'Sarah', subtotal: 0, tax: 0, total: 0, createdAt: openedAt, closedAt: null }
  ];
  orderItemsNow = [30, 40].flatMap(orderId => [
    { orderItemId: orderId + 1, orderId, menuItemId: 1, price: 16.0, quantity: 1, status: 'pending' },
    { orderItemId: orderId + 2, orderId, menuItemId: 2, price: 17.0, quantity: 1, status: 'completed' },
    { orderItemId: orderId + 3, orderId, menuItemId: 2, price: 17.0, quantity: 1, status: 'limbo' }
  ]);
};

test.each(SCREENS)('%s: an open check shows its amount, item counts, server, and age', async (_name, Screen, tableNumber) => {
  seatWithOpenOrder();
  render(<Screen setCurrentView={jest.fn()} setSelectedTable={jest.fn()} />);

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
  const setCurrentView = jest.fn();
  const setSelectedTable = jest.fn();
  render(<Screen setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />);

  await userEvent.click((await screen.findByRole('heading', { name: tableNumber })).closest('.table-card'));

  expect(setSelectedTable).toHaveBeenCalledWith(expect.objectContaining({ tableNumber, status: 'occupied' }));
  expect(setCurrentView).toHaveBeenCalledWith('createOrder');
});

test.each(SCREENS)('%s: says so when nothing is open', async (name, Screen) => {
  render(<Screen setCurrentView={jest.fn()} setSelectedTable={jest.fn()} />);

  expect(await screen.findByText(name === 'Tables' ? 'No active tables' : 'No active Quick Orders')).toBeInTheDocument();
});
