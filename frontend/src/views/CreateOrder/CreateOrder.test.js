import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CreateOrder from './CreateOrder';

/**
 * Behavior tests for the Create Order screen.
 *
 * WHAT: Drives the screen the way a server would (tap items, Send, Send Now,
 *       Save Draft, remove) against a fake backend, and checks both what the
 *       screen shows and which requests were sent.
 * WHY:  This screen holds the signature feature - the edit-window timer. These
 *       tests pin down how it behaves so the code can be reorganized without
 *       changing what the server on the floor experiences.
 *
 * HOW THE FAKE BACKEND WORKS:
 *   global.fetch is replaced with a function that looks at the method + URL
 *   and returns canned JSON. Every call is recorded in `calls` so a test can
 *   assert "these requests happened, in this order".
 */

const MENU = [
  { menuItemId: 1, name: 'Chicken Cutty', price: 17.0, category: 'Savory' },
  { menuItemId: 2, name: 'Fries', price: 6.0, category: 'Savory' },
  { menuItemId: 3, name: 'Latte', price: 5.0, category: 'Coffee' }
];

let calls;            // e.g. ['GET /api/menu-items', 'POST /api/orders']
let nextOrderItemId;  // fake auto-increment ID for created order items
let existingOrders;   // what GET /api/orders?tableId=... returns
let existingItems;    // what GET /api/order-items/order/{id} returns
let editWindowMs;     // how far in the future the fake backend says the edit window closes

const jsonResponse = (data, status = 200) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => data
});

beforeEach(() => {
  calls = [];
  nextOrderItemId = 100;
  existingOrders = [];
  existingItems = [];
  editWindowMs = 16000;

  global.fetch = jest.fn(async (url, options = {}) => {
    const method = options.method || 'GET';
    const path = url.replace('http://localhost:8080', '');
    const body = options.body ? JSON.parse(options.body) : null;
    calls.push(`${method} ${path}`);

    if (method === 'GET' && path === '/api/menu-items') return jsonResponse(MENU);
    if (method === 'GET' && path === '/api/tables') return jsonResponse([]);
    if (method === 'POST' && path === '/api/tables') {
      return jsonResponse({ tableId: 9, status: 'available', ...body }, 201);
    }
    if (method === 'PUT' && path.startsWith('/api/tables/')) return jsonResponse({});
    if (method === 'GET' && path.startsWith('/api/orders?tableId=')) return jsonResponse(existingOrders);
    if (method === 'POST' && path === '/api/orders') return jsonResponse({ orderId: 77, ...body }, 201);
    if (method === 'GET' && path.startsWith('/api/order-items/order/')) return jsonResponse(existingItems);
    if (method === 'POST' && path === '/api/order-items') {
      return jsonResponse({ orderItemId: nextOrderItemId++, ...body }, 201);
    }
    if (method === 'POST' && path === '/api/order-items/order/77/send') {
      // Backend answers with the items it moved to limbo and when the edit window closes
      const expires = new Date(Date.now() + editWindowMs).toISOString();
      return jsonResponse([{ orderItemId: 100, menuItemId: 1, status: 'limbo', delayExpiresAt: expires }]);
    }
    if (method === 'POST' && path === '/api/order-items/order/77/send-now') return jsonResponse([]);
    if (method === 'DELETE' && path.startsWith('/api/order-items/')) return jsonResponse(null, 204);

    throw new Error(`Unexpected request in test: ${method} ${path}`);
  });
});

afterEach(() => {
  jest.restoreAllMocks();
});

// Render the screen and wait for the menu to load
const renderScreen = async (selectedTable = null) => {
  const view = render(
    <CreateOrder setCurrentView={jest.fn()} selectedTable={selectedTable} setSelectedTable={jest.fn()} />
  );
  await screen.findByRole('heading', { name: 'Chicken Cutty' });
  return view;
};

// A card in the menu grid. Menu cards show the name as a heading, which tells them
// apart from the same name appearing in the order list.
const menuCard = (name) => screen.getByRole('heading', { name });

// The left-hand list of items on the order (as opposed to the menu grid)
const orderList = (container) => container.querySelector('.order-items-list');

test('tapping a menu item adds it to the order as a draft and updates the totals', async () => {
  const { container } = await renderScreen();
  expect(screen.getByText('Add items to order')).toBeInTheDocument();

  await userEvent.click(menuCard('Chicken Cutty'));
  await userEvent.click(menuCard('Fries'));

  expect(within(orderList(container)).getByText('Chicken Cutty')).toBeInTheDocument();
  expect(within(orderList(container)).getByText('Fries')).toBeInTheDocument();
  expect(screen.getByText('Ready to send or save as draft')).toBeInTheDocument();

  // 17.00 + 6.00 = 23.00, 3% tax = 0.69, total = 23.69
  const totals = container.querySelector('.order-totals');
  expect(within(totals).getByText('$23.00')).toBeInTheDocument();
  expect(within(totals).getByText('$0.69')).toBeInTheDocument();
  expect(within(totals).getByText('$23.69')).toBeInTheDocument();

  // Nothing is saved to the backend until Send or Save Draft
  expect(calls).toEqual(['GET /api/menu-items']);
});

test('category tabs filter the menu and the selected tab is highlighted', async () => {
  await renderScreen();
  expect(screen.queryByText('Latte')).not.toBeInTheDocument();

  const coffeeTab = screen.getByRole('button', { name: 'COFFEE' });
  await userEvent.click(coffeeTab);

  expect(screen.getByText('Latte')).toBeInTheDocument();
  expect(screen.queryByText('Chicken Cutty')).not.toBeInTheDocument();
  expect(coffeeTab).toHaveClass('active');
  expect(screen.getByRole('button', { name: 'SAVORY' })).not.toHaveClass('active');
});

test('removing an unsaved draft item does not call the backend', async () => {
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Chicken Cutty'));

  await userEvent.click(within(orderList(container)).getByRole('button', { name: 'x' }));

  expect(within(orderList(container)).queryByText('Chicken Cutty')).not.toBeInTheDocument();
  expect(calls.filter(c => c.startsWith('DELETE'))).toEqual([]);
});

test('SEND ORDER with no table creates a Quick Order, saves items, and starts the edit timer', async () => {
  await renderScreen();
  await userEvent.click(menuCard('Chicken Cutty'));

  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));

  // Edit window is open
  expect(await screen.findByText('15 seconds to edit')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'SEND NOW?' })).toBeInTheDocument();
  expect(screen.getByText('Current Order - Quick Order QO1')).toBeInTheDocument();

  // Requests, in order: find next QO number, create table, open order, seat table, save item, send
  expect(calls).toEqual([
    'GET /api/menu-items',
    'GET /api/tables',
    'POST /api/tables',
    'POST /api/orders',
    'PUT /api/tables/9',
    'POST /api/order-items',
    'POST /api/order-items/order/77/send'
  ]);
});

test('SEND NOW? during the edit window locks the items immediately', async () => {
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Chicken Cutty'));
  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));
  await screen.findByText('15 seconds to edit');

  await userEvent.click(screen.getByRole('button', { name: 'SEND NOW?' }));

  expect(await screen.findByText('Items locked and sent to prep station')).toBeInTheDocument();
  expect(calls).toContain('POST /api/order-items/order/77/send-now');
  // Locked items can no longer be removed, and the button is back to its normal label
  expect(within(orderList(container)).queryByRole('button', { name: 'x' })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'SEND ORDER' })).toBeInTheDocument();
});

test('adding an item during the edit window saves and sends it right away', async () => {
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Chicken Cutty'));
  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));
  await screen.findByText('15 seconds to edit');
  const callsBefore = calls.length;

  await userEvent.click(menuCard('Fries'));

  await waitFor(() => expect(within(orderList(container)).getByText('Fries')).toBeInTheDocument());
  expect(calls.slice(callsBefore)).toEqual([
    'POST /api/order-items',
    'POST /api/order-items/order/77/send'
  ]);
});

test('removing an item during the edit window deletes it from the backend', async () => {
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Chicken Cutty'));
  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));
  await screen.findByText('15 seconds to edit');

  await userEvent.click(within(orderList(container)).getByRole('button', { name: 'x' }));

  await waitFor(() =>
    expect(within(orderList(container)).queryByText('Chicken Cutty')).not.toBeInTheDocument()
  );
  expect(calls).toContain('DELETE /api/order-items/100');
});

test('when the edit window runs out, items lock on their own', async () => {
  editWindowMs = 1200;  // Shrink the window so the test does not wait 15 real seconds
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Chicken Cutty'));

  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));

  expect(
    await screen.findByText('Items locked and sent to prep station', {}, { timeout: 4000 })
  ).toBeInTheDocument();
  expect(orderList(container).querySelectorAll('.order-item.locked')).toHaveLength(1);
  expect(within(orderList(container)).queryByRole('button', { name: 'x' })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'SEND ORDER' })).toBeInTheDocument();
  // The screen locked by itself - Send Now was never called
  expect(calls).not.toContain('POST /api/order-items/order/77/send-now');
});

test('two of the same item are saved separately and can each be removed', async () => {
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Fries'));
  await userEvent.click(menuCard('Fries'));
  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));
  await screen.findByText('15 seconds to edit');

  // Remove both, one after the other
  await userEvent.click(within(orderList(container)).getAllByRole('button', { name: 'x' })[0]);
  await waitFor(() => expect(within(orderList(container)).getAllByText('Fries')).toHaveLength(1));
  await userEvent.click(within(orderList(container)).getByRole('button', { name: 'x' }));
  await waitFor(() => expect(within(orderList(container)).queryByText('Fries')).not.toBeInTheDocument());

  // Each had its own ID, so each delete targeted a different saved item
  expect(calls.filter(c => c.startsWith('DELETE'))).toEqual([
    'DELETE /api/order-items/100',
    'DELETE /api/order-items/101'
  ]);
});

test('SAVE DRAFT stores the items without sending them', async () => {
  await renderScreen();
  await userEvent.click(menuCard('Chicken Cutty'));

  await userEvent.click(screen.getByRole('button', { name: 'SAVE DRAFT' }));

  expect(await screen.findByText('Draft saved! Items remain editable')).toBeInTheDocument();
  expect(calls).toContain('POST /api/order-items');
  expect(calls.filter(c => c.endsWith('/send'))).toEqual([]);
});

test('opening an occupied table loads its existing items and shows sent ones as locked', async () => {
  existingOrders = [{ orderId: 77, tableId: 3, status: 'open' }];
  existingItems = [
    { orderItemId: 50, menuItemId: 1, price: 17.0, quantity: 1, status: 'fired' },
    { orderItemId: 51, menuItemId: 2, price: 6.0, quantity: 1, status: 'draft' }
  ];

  const { container } = await renderScreen({ tableId: 3, tableNumber: 'W3', status: 'occupied' });

  const list = orderList(container);
  expect(await within(list).findByText('Chicken Cutty')).toBeInTheDocument();
  expect(within(list).getByText('Fries')).toBeInTheDocument();
  expect(screen.getByText('Current Order - Table W3')).toBeInTheDocument();

  // The fired item is locked (no remove button); the draft is still removable
  expect(list.querySelectorAll('.order-item.locked')).toHaveLength(1);
  expect(within(list).getAllByRole('button', { name: 'x' })).toHaveLength(1);
});

test('CLOSE ORDER is disabled until the order has items', async () => {
  await renderScreen();
  expect(screen.getByRole('button', { name: 'CLOSE ORDER' })).toBeDisabled();

  await userEvent.click(menuCard('Chicken Cutty'));

  expect(screen.getByRole('button', { name: 'CLOSE ORDER' })).toBeEnabled();
});
