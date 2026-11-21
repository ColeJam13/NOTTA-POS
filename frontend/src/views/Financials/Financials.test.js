import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Financials from './Financials';
import { apiFetch } from '../../services/api';

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
 * is replaced with a function that returns canned JSON. Like the real backend,
 * it answers GET /api/reports/financials?from=...&to=... with a report covering
 * only the checks closed between those two moments, so these tests also prove
 * the screen asks for the right period.
 */

// "2026-11-18" for a Date, in this device's own time zone (what a date box holds)
const dateBoxValue = (date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

let closedChecks;     // the checks the fake backend "has": [{ ..., closedAt: Date }]
let requests;         // every request path, in order
let backendClockAheadMs;    // how far the fake backend's clock is ahead of this device's (negative = behind)

// What time the fake backend thinks it is
const backendNow = () => Date.now() + backendClockAheadMs;

// A moment written the way the real backend writes one: local date and time with no
// time zone, e.g. "2026-11-21T19:15:31.250"
const asBackendTime = (ms) => {
  const date = new Date(ms);
  const pad = (number, length = 2) => String(number).padStart(length, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${pad(date.getMilliseconds(), 3)}`;
};

// Like the real backend, every answer carries the backend's own time in a header
const answer = (data) => ({
  ok: true,
  status: 200,
  headers: { get: (name) => (name.toLowerCase() === 'x-server-time' ? asBackendTime(backendNow()) : null) },
  json: async () => data
});

// In the running app nobody reaches this screen without signing in first, so the app
// has already had an answer from the backend (and with it, the backend's time) before
// the screen works out what "today" is. This stands for that earlier answer.
const someoneSignedInEarlier = async () => {
  await apiFetch('/api/staff/me');
  requests = [];
};

// Build the report the way the real backend does, for the checks closed between from and to
const reportFor = (from, to) => {
  const checks = closedChecks.filter(check => check.closedAt >= from && check.closedAt <= to);
  const sum = (field) => Math.round(checks.reduce((total, check) => total + check[field], 0) * 100) / 100;
  const revenue = sum('subtotal');
  const paid = sum('total');

  const methods = [...new Set(checks.map(check => check.paymentMethod))].sort();        // "cash" sorts before "credit_card"
  return {
    orderCount: checks.length,
    revenue,
    tax: sum('tax'),
    tips: sum('tip'),
    totalCollected: Math.round((revenue + sum('tax') + sum('tip')) * 100) / 100,
    avgOrderValue: checks.length ? revenue / checks.length : 0,
    avgTipPercent: revenue ? Math.round((sum('tip') / revenue) * 1000) / 10 : 0,
    paymentBreakdown: methods.map(method => {
      const amount = checks.filter(check => check.paymentMethod === method).reduce((total, check) => total + check.total, 0);
      return { paymentMethod: method, amount, percent: Math.round((amount / paid) * 1000) / 10 };
    }),
    recentTransactions: [...checks]
      .sort((a, b) => b.closedAt - a.closedAt)
      .map(check => ({
        orderId: check.orderId, tableNumber: 'W1', isQuickOrder: false, serverName: 'CJ',
        closedAt: check.closedAt.toISOString(), total: check.total, tip: check.tip, paymentMethod: check.paymentMethod
      }))
  };
};

beforeEach(async () => {
  const now = new Date();
  requests = [];
  backendClockAheadMs = 0;
  closedChecks = [
    // Paid a moment ago: 33.00 + 0.99 tax, with a 6.80 tip, by credit card
    { orderId: 10, subtotal: 33.0, tax: 0.99, total: 33.99, tip: 6.8, paymentMethod: 'credit_card', closedAt: now },
    // Paid last year: 10.00 + 0.30 tax, no tip, in cash
    { orderId: 11, subtotal: 10.0, tax: 0.3, total: 10.3, tip: 0, paymentMethod: 'cash', closedAt: new Date(now.getFullYear() - 1, 5, 15, 12, 0, 0) }
  ];

  global.fetch = jest.fn(async (url) => {
    const path = url.replace('http://localhost:8080', '');
    requests.push(path);

    if (path === '/api/staff/me') return answer({ staffId: 1, name: 'CJ', role: 'manager' });

    const match = path.match(/^\/api\/reports\/financials\?from=([^&]+)&to=(.+)$/);
    if (match) {
      // The screen sends local date-times with no zone ("2026-11-18T00:00:00"); new Date() reads those as local time
      return answer(reportFor(new Date(match[1]), new Date(match[2])));
    }
    throw new Error(`Unexpected request in test: ${path}`);
  });

  await someoneSignedInEarlier();
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
  expect(within(statCard('Avg Tip %')).getByText('20.6%')).toBeInTheDocument();

  // 33.00 + 0.99 tax + 6.80 tips = 40.79 collected (this used to show 39.80)
  expect(within(statCard('Total w/ Tips')).getByText('$40.79')).toBeInTheDocument();
  expect(within(statCard('Total w/ Tips')).getByText('includes $0.99 tax')).toBeInTheDocument();

  // The payment breakdown and the transaction list show the same check amount as the payment
  expect(within(document.querySelector('.payment-breakdown')).getByText('$33.99')).toBeInTheDocument();
  expect(within(document.querySelector('.payment-breakdown')).getByText('100.0%')).toBeInTheDocument();
  expect(within(document.querySelector('.transaction-list')).getByText('$33.99')).toBeInTheDocument();
  expect(within(document.querySelector('.transaction-list')).getByText('Table W1')).toBeInTheDocument();
});

test('today asks the backend for midnight to the last second of today, in local time', async () => {
  render(<Financials setCurrentView={jest.fn()} setSelectedTable={jest.fn()} />);
  await within(statCard('Orders Closed')).findByText('1');

  const today = dateBoxValue(new Date());
  expect(requests).toEqual([`/api/reports/financials?from=${today}T00:00:00&to=${today}T23:59:59`]);
});

test('today is the backend\'s today, even on a device whose own date is wrong', async () => {
  // The office laptop's clock is two days behind. Checks are stamped with the backend's
  // clock, so "today" has to mean the backend's date: the laptop's own date would ask
  // for a day whose checks are not the ones closed this shift.
  backendClockAheadMs = 2 * 24 * 60 * 60 * 1000;
  await someoneSignedInEarlier();

  render(<Financials setCurrentView={jest.fn()} setSelectedTable={jest.fn()} />);

  const backendToday = dateBoxValue(new Date(backendNow()));
  await waitFor(() =>
    expect(requests).toEqual([`/api/reports/financials?from=${backendToday}T00:00:00&to=${backendToday}T23:59:59`])
  );
});

test('custom period: picking today as both dates includes checks closed today', async () => {
  render(<Financials setCurrentView={jest.fn()} setSelectedTable={jest.fn()} />);
  await within(statCard('Orders Closed')).findByText('1');

  await userEvent.click(screen.getByRole('button', { name: 'CUSTOM' }));

  // With no dates picked, the custom period covers everything: both closed orders
  expect(await within(statCard('Orders Closed')).findByText('2')).toBeInTheDocument();
  // Two payment methods, cash listed first
  const methods = [...document.querySelectorAll('.payment-label')].map(label => label.textContent);
  expect(methods).toEqual(['CASH:', 'CREDIT CARD:']);

  const today = dateBoxValue(new Date());
  const [startBox, endBox] = document.querySelectorAll('input[type="date"]');
  fireEvent.change(startBox, { target: { value: today } });
  fireEvent.change(endBox, { target: { value: today } });

  // Only today's order (this used to show 0 orders and $0.00)
  expect(await within(statCard('Orders Closed')).findByText('1')).toBeInTheDocument();
  expect(within(statCard('Total Revenue')).getByText('$33.00')).toBeInTheDocument();
});

test('a period with nothing closed shows zeros and says there is no data', async () => {
  closedChecks = [];
  render(<Financials setCurrentView={jest.fn()} setSelectedTable={jest.fn()} />);
  await waitFor(() => expect(requests).toHaveLength(1));

  expect(within(statCard('Orders Closed')).getByText('0')).toBeInTheDocument();
  expect(within(statCard('Total Revenue')).getByText('$0.00')).toBeInTheDocument();
  expect(within(statCard('Avg Tip %')).getByText('0.0%')).toBeInTheDocument();
  expect(screen.getByText('No payment data for selected period')).toBeInTheDocument();
  expect(screen.getByText('No transactions for selected period')).toBeInTheDocument();
});

test('this month asks for the first day of the month through its last day', async () => {
  render(<Financials setCurrentView={jest.fn()} setSelectedTable={jest.fn()} />);
  await within(statCard('Orders Closed')).findByText('1');

  await userEvent.click(screen.getByRole('button', { name: 'THIS MONTH' }));

  const now = new Date();
  const firstDay = dateBoxValue(new Date(now.getFullYear(), now.getMonth(), 1));
  const lastDay = dateBoxValue(new Date(now.getFullYear(), now.getMonth() + 1, 0));
  await waitFor(() => expect(requests).toContain(`/api/reports/financials?from=${firstDay}T00:00:00&to=${lastDay}T23:59:59`));
});
