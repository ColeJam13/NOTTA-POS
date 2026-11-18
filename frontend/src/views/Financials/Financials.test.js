import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Financials from './Financials';

/**
 * Behavior tests for the Financials screen.
 *
 * WHAT: Loads the screen against a fake backend holding one check paid today
 *       and one paid long ago, and checks the numbers for each time period.
 * WHY:  - "Total w/ Tips" left the tax out, so it never matched the payment
 *         amounts listed right below it.
 *       - The CUSTOM period found nothing when today was picked as both dates.
 *
 * The fake backend works the same way as in CreateOrder.test.js: global.fetch
 * is replaced with a function that returns canned JSON.
 */

// "2026-11-18" for a Date, in this device's own time zone (what a date box holds)
const dateBoxValue = (date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

beforeEach(() => {
  const now = new Date();
  const lastYear = new Date(now.getFullYear() - 1, 5, 15, 12, 0, 0);

  const orders = [
    // Paid a moment ago: 33.00 + 0.99 tax, with a 6.80 tip
    { orderId: 10, tableId: 1, status: 'closed', serverName: 'CJ', subtotal: 33.0, tax: 0.99, total: 33.99, closedAt: now.toISOString() },
    // Paid last year: 10.00 + 0.30 tax, no tip
    { orderId: 11, tableId: 1, status: 'closed', serverName: 'CJ', subtotal: 10.0, tax: 0.3, total: 10.3, closedAt: lastYear.toISOString() },
    // Still open: never counted
    { orderId: 12, tableId: 1, status: 'open', serverName: 'CJ', subtotal: 0, tax: 0, total: 0, closedAt: null }
  ];
  const payments = [
    { paymentId: 1, orderId: 10, amount: 33.99, tipAmount: 6.8, paymentMethod: 'credit_card' },
    { paymentId: 2, orderId: 11, amount: 10.3, tipAmount: 0, paymentMethod: 'cash' }
  ];
  const orderItems = [
    { orderItemId: 1, orderId: 10, menuItemId: 1, price: 16.0, quantity: 1, status: 'completed' },
    { orderItemId: 2, orderId: 10, menuItemId: 2, price: 17.0, quantity: 1, status: 'completed' },
    { orderItemId: 3, orderId: 11, menuItemId: 1, price: 10.0, quantity: 1, status: 'completed' }
  ];

  global.fetch = jest.fn(async (url) => {
    const path = url.replace('http://localhost:8080', '');
    const json = (data) => ({ ok: true, status: 200, json: async () => data });

    if (path === '/api/orders') return json(orders);
    if (path === '/api/payments') return json(payments);
    if (path === '/api/order-items') return json(orderItems);
    if (path === '/api/tables') return json([{ tableId: 1, tableNumber: 'W1', isQuickOrder: false }]);
    throw new Error(`Unexpected request in test: ${path}`);
  });
});

afterEach(() => {
  jest.restoreAllMocks();
});

// The stat card with the given label, e.g. statCard('Total Revenue')
const statCard = (label) => screen.getByText(label).closest('.stat-card');

test('today: revenue is sales before tax, and the total with tips includes the tax', async () => {
  render(<Financials setCurrentView={jest.fn()} setSelectedTable={jest.fn()} />);

  // Wait for the data: one order was closed today
  expect(await within(statCard('Orders Closed')).findByText('1')).toBeInTheDocument();

  expect(within(statCard('Total Revenue')).getByText('$33.00')).toBeInTheDocument();
  expect(within(statCard('Total Tips')).getByText('$6.80')).toBeInTheDocument();

  // 33.00 + 0.99 tax + 6.80 tips = 40.79 collected (this used to show 39.80)
  expect(within(statCard('Total w/ Tips')).getByText('$40.79')).toBeInTheDocument();
  expect(within(statCard('Total w/ Tips')).getByText('includes $0.99 tax')).toBeInTheDocument();

  // The payment breakdown and the transaction list show the same check amount as the payment
  expect(within(document.querySelector('.payment-breakdown')).getByText('$33.99')).toBeInTheDocument();
  expect(within(document.querySelector('.transaction-list')).getByText('$33.99')).toBeInTheDocument();
});

test('custom period: picking today as both dates includes checks closed today', async () => {
  render(<Financials setCurrentView={jest.fn()} setSelectedTable={jest.fn()} />);
  await within(statCard('Orders Closed')).findByText('1');

  await userEvent.click(screen.getByRole('button', { name: 'CUSTOM' }));

  // With no dates picked, the custom period covers everything: both closed orders
  expect(within(statCard('Orders Closed')).getByText('2')).toBeInTheDocument();

  const today = dateBoxValue(new Date());
  const [startBox, endBox] = document.querySelectorAll('input[type="date"]');
  fireEvent.change(startBox, { target: { value: today } });
  fireEvent.change(endBox, { target: { value: today } });

  // Only today's order (this used to show 0 orders and $0.00)
  expect(within(statCard('Orders Closed')).getByText('1')).toBeInTheDocument();
  expect(within(statCard('Total Revenue')).getByText('$33.00')).toBeInTheDocument();
});
