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

  global.fetch = jest.fn(async (url) => {
    const path = url.replace('http://localhost:8080', '');
    const json = (data) => ({ ok: true, status: 200, json: async () => data });

    if (path === '/api/tables?quickOrders=false') return json(TABLES);
    if (path === '/api/tables?quickOrders=true') return json(QUICK_ORDER_TABLES);
    if (path === '/api/orders') return json(orders);
    if (path === '/api/order-items') return json(ORDER_ITEMS);
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
