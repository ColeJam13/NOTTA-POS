import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
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
  { menuItemId: 3, name: 'Latte', price: 5.0, category: 'Coffee' },
  { menuItemId: 4, name: 'Combo Plate', price: 15.0, category: 'Savory' }
];

// Only the Combo Plate has modifiers: a required "choose 1" group and an optional "up to 2" group.
// Same shape as GET /api/menu-items/modifier-groups.
const MODIFIER_GROUPS = [
  {
    menuItemId: 4,
    groups: [
      {
        modifierGroupId: 2, name: 'Bread Choice', description: 'Choose your bread', isRequired: true, maxSelections: 1,
        modifiers: [
          { modifierId: 8, name: 'White Toast', priceAdjustment: 0 },
          { modifierId: 14, name: 'French Toast', priceAdjustment: 2.0 }
        ]
      },
      {
        modifierGroupId: 5, name: 'Add-Ons', description: 'Optional add-ons', isRequired: false, maxSelections: 2,
        modifiers: [
          { modifierId: 22, name: 'Add Egg', priceAdjustment: 2.0 },
          { modifierId: 23, name: 'Add Avocado', priceAdjustment: 2.0 },
          { modifierId: 24, name: 'Add Chicken', priceAdjustment: 4.0 }
        ]
      }
    ]
  }
];

let calls;            // e.g. ['GET /api/menu-items', 'POST /api/orders']
let requests;         // the same calls with their JSON bodies: [{ method, path, body }]
let nextOrderItemId;  // fake auto-increment ID for created order items
let existingOrders;   // what GET /api/orders?tableId=... returns
let existingItems;    // what GET /api/order-items/order/{id} returns
let existingItemModifiers;  // what GET /api/order-item-modifiers/order/{id} returns
let editWindowMs;     // how far in the future the fake backend says the edit window closes
let savedMenuItemIds; // orderItemId -> menuItemId for items the fake backend has "saved"
let refuseItemSaves;  // true = the fake backend answers 400 to saving an item with modifiers
let checkClosedElsewhere;   // true = the order was closed on another terminal: the fake backend answers 409 with code ORDER_NOT_OPEN
let itemsAreLocked;   // true = the fake backend answers 409 (already sent to kitchen) to modifier changes and removals
let menuNow;          // what GET /api/menu-items returns right now (a test can 86 an item by replacing this)
let orderCheck;       // what GET /api/orders/77/check returns (the check as the backend works it out)
let paymentRefusal;   // a message = the fake backend answers 409 with that message to a payment
let holdPayments;     // a promise = the fake backend does not answer a payment until it resolves (a slow network)
let taxRate;          // what GET /api/settings says the tax rate is (0.03 = 3%)
let settingsFail;     // true = the fake backend answers 500 to GET /api/settings
let backendClockAheadMs;    // how far the fake backend's clock is ahead of this device's (negative = behind)

// What the real backend does when it saves modifiers: menu price + the chosen upcharges
const priceWithUpcharges = (menuItemId, modifierIds) => {
  const basePrice = MENU.find(m => m.menuItemId === menuItemId).price;
  const allModifiers = MODIFIER_GROUPS.flatMap(entry => entry.groups).flatMap(group => group.modifiers);
  return modifierIds.reduce(
    (sum, id) => sum + allModifiers.find(modifier => modifier.modifierId === id).priceAdjustment,
    basePrice
  );
};

// What time the fake backend thinks it is. A test moves it away from this device's
// clock with backendClockAheadMs, to stand for a tablet whose own clock is wrong.
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
const jsonResponse = (data, status = 200) => ({
  ok: status >= 200 && status < 300,
  status,
  headers: { get: (name) => (name.toLowerCase() === 'x-server-time' ? asBackendTime(backendNow()) : null) },
  json: async () => data
});

beforeEach(() => {
  calls = [];
  nextOrderItemId = 100;
  requests = [];
  existingOrders = [];
  existingItems = [];
  existingItemModifiers = [];
  editWindowMs = 16000;
  savedMenuItemIds = {};
  refuseItemSaves = false;
  checkClosedElsewhere = false;
  itemsAreLocked = false;
  menuNow = MENU;
  orderCheck = { orderId: 77, status: 'open', itemCount: 1, unsentItemCount: 0, subtotal: 17.0, tax: 0.51, total: 17.51, taxRate: 0.03 };
  paymentRefusal = null;
  holdPayments = null;
  taxRate = 0.03;
  settingsFail = false;
  backendClockAheadMs = 0;

  global.fetch = vi.fn(async (url, options = {}) => {
    const method = options.method || 'GET';
    const path = url.replace('http://localhost:8080', '');
    const body = options.body ? JSON.parse(options.body) : null;
    calls.push(`${method} ${path}`);
    requests.push({ method, path, body });

    if (method === 'GET' && path === '/api/menu-items') return jsonResponse(menuNow);
    if (method === 'GET' && path === '/api/settings') {
      return settingsFail ? jsonResponse({ message: 'Something went wrong' }, 500) : jsonResponse({ taxRate });
    }
    if (method === 'GET' && path === '/api/menu-items/modifier-groups') return jsonResponse(MODIFIER_GROUPS);
    if (method === 'GET' && path === '/api/tables') return jsonResponse([]);
    if (method === 'POST' && path === '/api/tables') {
      return jsonResponse({ tableId: 9, status: 'available', ...body }, 201);
    }
    if (method === 'PUT' && path.startsWith('/api/tables/')) return jsonResponse({});
    if (method === 'GET' && path.startsWith('/api/orders?tableId=')) return jsonResponse(existingOrders);
    if (method === 'POST' && path === '/api/orders') return jsonResponse({ orderId: 77, ...body }, 201);
    if (method === 'GET' && path.startsWith('/api/order-items/order/')) return jsonResponse(existingItems);
    if (method === 'GET' && path.startsWith('/api/order-item-modifiers/order/')) {
      return jsonResponse(existingItemModifiers);
    }
    // Like the real backend, a closed check refuses everything that would add to it, send it, or change it
    const addsToOrChangesTheOrder =
      (method === 'POST' && path === '/api/order-items') ||
      (method === 'POST' && /^\/api\/order-items\/order\/77\/send(-now)?$/.test(path)) ||
      (method === 'PUT' && /^\/api\/order-items\/\d+\/modifiers$/.test(path)) ||
      (method === 'DELETE' && /^\/api\/order-items\/\d+$/.test(path));
    if (checkClosedElsewhere && addsToOrChangesTheOrder) {
      return jsonResponse({
        message: 'This check was already closed, most likely on another terminal. Nothing was added or sent.',
        code: 'ORDER_NOT_OPEN'
      }, 409);
    }

    // Every item, with or without modifiers, is rung in through this one endpoint
    if (method === 'POST' && path === '/api/order-items') {
      if (refuseItemSaves && body.modifierIds.length > 0) {
        return jsonResponse({ message: '"Bread Choice" allows at most 1 selection(s), but 2 were chosen' }, 400);
      }
      // Like the real backend: the request has no price, the answer does
      const orderItemId = nextOrderItemId++;
      savedMenuItemIds[orderItemId] = body.menuItemId;
      return jsonResponse({
        orderItemId,
        orderId: body.orderId,
        menuItemId: body.menuItemId,
        quantity: body.quantity,
        specialInstructions: body.specialInstructions,
        price: priceWithUpcharges(body.menuItemId, body.modifierIds),
        ringInPosition: body.ringInPosition,
        status: 'draft'
      }, 201);
    }
    if (method === 'PUT' && /^\/api\/order-items\/\d+\/modifiers$/.test(path)) {
      if (itemsAreLocked) return jsonResponse({ message: 'Cannot change modifiers on locked item' }, 409);
      const orderItemId = Number(path.split('/')[3]);
      return jsonResponse({
        orderItemId,
        specialInstructions: body.specialInstructions,
        price: priceWithUpcharges(savedMenuItemIds[orderItemId], body.modifierIds)
      });
    }
    if (method === 'POST' && path === '/api/order-items/order/77/send') {
      // Backend answers with the items it moved to limbo and when the edit window closes,
      // by its own clock
      const expires = asBackendTime(backendNow() + editWindowMs);
      return jsonResponse([{ orderItemId: 100, menuItemId: 1, status: 'limbo', delayExpiresAt: expires }]);
    }
    if (method === 'POST' && path === '/api/order-items/order/77/send-now') return jsonResponse([]);
    if (method === 'DELETE' && path.startsWith('/api/order-items/')) {
      if (itemsAreLocked) return jsonResponse({ message: 'Cannot delete locked item' }, 409);
      return jsonResponse(null, 204);
    }
    if (method === 'GET' && path === '/api/orders/77/check') return jsonResponse(orderCheck);
    if (method === 'POST' && path === '/api/payments') {
      if (holdPayments) await holdPayments;
      // Like the real backend: a check that was already paid answers with the closed-check code
      if (checkClosedElsewhere) {
        return jsonResponse({
          message: 'This check was already closed, most likely on another terminal. Nothing was charged.',
          code: 'ORDER_NOT_OPEN'
        }, 409);
      }
      if (paymentRefusal) return jsonResponse({ message: paymentRefusal }, 409);
      return jsonResponse({ paymentId: 1, ...body }, 201);
    }
    if (method === 'DELETE' && path === '/api/orders/77') return jsonResponse(null, 204);

    throw new Error(`Unexpected request in test: ${method} ${path}`);
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

// Render the screen and wait for the menu to load
const renderScreen = async (selectedTable = null) => {
  const view = render(
    <CreateOrder setCurrentView={vi.fn()} selectedTable={selectedTable} setSelectedTable={vi.fn()} />
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
  // (the screen loads the menu, its modifier groups, and the tax rate when it opens, and that is all)
  expect(calls).toEqual(['GET /api/menu-items', 'GET /api/menu-items/modifier-groups', 'GET /api/settings']);
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

  // Requests, in order: load menu + modifiers + tax rate, find next QO number, create table, open order, save item, send
  // (the backend marks the table occupied when it opens the order - there is no separate "seat table" request)
  expect(calls).toEqual([
    'GET /api/menu-items',
    'GET /api/menu-items/modifier-groups',
    'GET /api/settings',
    'GET /api/tables',
    'POST /api/tables',
    'POST /api/orders',
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

// ============================================================================
// Modifiers and kitchen notes
// ============================================================================

// The modifier picker pop-up for a menu item
const pickerFor = (name) => screen.getByRole('dialog', { name: `Customize ${name}` });

// A choice button inside the picker (matched by the start of its text, so "French Toast" finds "French Toast +$2.00")
const choice = (picker, name) => within(picker).getByRole('button', { name: new RegExp(`^${name}`) });

test('tapping an item with modifiers opens the picker, and a required group must be answered before it can be added', async () => {
  const { container } = await renderScreen();

  await userEvent.click(menuCard('Combo Plate'));

  // The picker is open and nothing is on the order yet
  const picker = pickerFor('Combo Plate');
  expect(within(orderList(container)).queryByText('Combo Plate')).not.toBeInTheDocument();
  expect(within(picker).getByText('REQUIRED - CHOOSE 1')).toBeInTheDocument();
  expect(within(picker).getByText('OPTIONAL - UP TO 2')).toBeInTheDocument();

  // Bread Choice is required: the confirm button is locked and the picker says what is missing
  const confirm = within(picker).getByRole('button', { name: 'ADD TO ORDER' });
  expect(confirm).toBeDisabled();
  expect(within(picker).getByText('Still needed: Bread Choice')).toBeInTheDocument();

  // Answer it (French Toast is a $2.00 upcharge) and skip the optional group
  await userEvent.click(choice(picker, 'French Toast'));
  expect(confirm).toBeEnabled();
  expect(within(picker).queryByText(/Still needed/)).not.toBeInTheDocument();
  await userEvent.click(confirm);

  // Picker closed; the item is on the order with its modifier and the upcharge in its price
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  const list = orderList(container);
  expect(within(list).getByText('Combo Plate')).toBeInTheDocument();
  expect(within(list).getByText('French Toast')).toBeInTheDocument();
  expect(within(list).getByText('+$2.00')).toBeInTheDocument();
  expect(within(list).getByText('$17.00')).toBeInTheDocument();

  // 15.00 + 2.00 = 17.00, 3% tax = 0.51, total = 17.51
  const totals = container.querySelector('.order-totals');
  expect(within(totals).getByText('$17.00')).toBeInTheDocument();
  expect(within(totals).getByText('$0.51')).toBeInTheDocument();
  expect(within(totals).getByText('$17.51')).toBeInTheDocument();

  // Still nothing saved to the backend until Send or Save Draft
  expect(calls).toEqual(['GET /api/menu-items', 'GET /api/menu-items/modifier-groups', 'GET /api/settings']);
});

test('a "choose 1" group swaps to the new choice, and an "up to N" group stops at N', async () => {
  await renderScreen();
  await userEvent.click(menuCard('Combo Plate'));
  const picker = pickerFor('Combo Plate');

  // Choose 1: tapping a second bread replaces the first
  await userEvent.click(choice(picker, 'White Toast'));
  expect(choice(picker, 'White Toast')).toHaveAttribute('aria-pressed', 'true');
  await userEvent.click(choice(picker, 'French Toast'));
  expect(choice(picker, 'French Toast')).toHaveAttribute('aria-pressed', 'true');
  expect(choice(picker, 'White Toast')).toHaveAttribute('aria-pressed', 'false');

  // Up to 2: after two add-ons the third is greyed out
  await userEvent.click(choice(picker, 'Add Egg'));
  await userEvent.click(choice(picker, 'Add Avocado'));
  expect(choice(picker, 'Add Chicken')).toBeDisabled();

  // The price follows along: 15.00 + 2.00 + 2.00 + 2.00
  expect(within(picker).getByText('$21.00')).toBeInTheDocument();

  // Un-picking one frees up a slot again
  await userEvent.click(choice(picker, 'Add Egg'));
  expect(choice(picker, 'Add Chicken')).toBeEnabled();
  expect(within(picker).getByText('$19.00')).toBeInTheDocument();
});

test('closing the picker with CANCEL or Escape adds nothing to the order', async () => {
  const { container } = await renderScreen();

  await userEvent.click(menuCard('Combo Plate'));
  await userEvent.click(choice(pickerFor('Combo Plate'), 'White Toast'));
  await userEvent.click(within(pickerFor('Combo Plate')).getByRole('button', { name: 'CANCEL' }));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

  await userEvent.click(menuCard('Combo Plate'));
  fireEvent.keyDown(document, { key: 'Escape' });
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

  expect(within(orderList(container)).queryByText('Combo Plate')).not.toBeInTheDocument();
  expect(screen.getByText('Add items to order')).toBeInTheDocument();
});

test('SEND ORDER saves an item together with its modifiers and kitchen note', async () => {
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Combo Plate'));
  const picker = pickerFor('Combo Plate');
  await userEvent.click(choice(picker, 'Add Chicken'));
  await userEvent.click(choice(picker, 'French Toast'));
  await userEvent.type(within(picker).getByLabelText('KITCHEN NOTE'), 'no butter');
  await userEvent.click(within(picker).getByRole('button', { name: 'ADD TO ORDER' }));
  expect(within(orderList(container)).getByText('NOTE: no butter')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));
  expect(await screen.findByText('15 seconds to edit')).toBeInTheDocument();

  // One request carried the item, its modifiers (in picker order, not tap order), and its note - and no price
  const saves = requests.filter(r => r.method === 'POST' && r.path === '/api/order-items');
  expect(saves).toHaveLength(1);
  expect(saves[0].body).toEqual({
    orderId: 77,
    menuItemId: 4,
    quantity: 1,
    specialInstructions: 'no butter',
    modifierIds: [14, 24],
    ringInPosition: 0           // The first item on the order
  });
  expect(saves[0].body).not.toHaveProperty('price');
  expect(calls).toContain('POST /api/order-items/order/77/send');
});

test('the pencil adds a kitchen note to an item that has no modifiers', async () => {
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Fries'));           // Plain item: added with one tap, no picker
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

  await userEvent.click(within(orderList(container)).getByRole('button', { name: 'Edit Fries' }));
  const picker = pickerFor('Fries');
  expect(within(picker).queryByRole('group')).not.toBeInTheDocument();    // No modifier groups, just the note
  await userEvent.type(within(picker).getByLabelText('KITCHEN NOTE'), 'extra crispy');
  await userEvent.click(within(picker).getByRole('button', { name: 'SAVE CHANGES' }));

  // The note shows on the line; the item is not saved yet, so the backend has not been told
  expect(within(orderList(container)).getByText('NOTE: extra crispy')).toBeInTheDocument();
  expect(calls).toEqual(['GET /api/menu-items', 'GET /api/menu-items/modifier-groups', 'GET /api/settings']);

  // Saving the draft sends the note along
  await userEvent.click(screen.getByRole('button', { name: 'SAVE DRAFT' }));
  await screen.findByText('Draft saved! Items remain editable');
  const save = requests.find(r => r.method === 'POST' && r.path === '/api/order-items');
  expect(save.body).toEqual({
    orderId: 77,
    menuItemId: 2,
    quantity: 1,
    specialInstructions: 'extra crispy',
    modifierIds: [],
    ringInPosition: 0           // The first item on the order
  });
});

test('changing modifiers during the edit window saves the change and keeps the item editable', async () => {
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Combo Plate'));
  await userEvent.click(choice(pickerFor('Combo Plate'), 'White Toast'));
  await userEvent.click(within(pickerFor('Combo Plate')).getByRole('button', { name: 'ADD TO ORDER' }));
  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));
  await screen.findByText('15 seconds to edit');

  // Reopen the item: the picker remembers what was chosen
  await userEvent.click(within(orderList(container)).getByRole('button', { name: 'Edit Combo Plate' }));
  const picker = pickerFor('Combo Plate');
  expect(choice(picker, 'White Toast')).toHaveAttribute('aria-pressed', 'true');

  // Fix the bread and add an allergy note
  await userEvent.click(choice(picker, 'French Toast'));
  await userEvent.type(within(picker).getByLabelText('KITCHEN NOTE'), 'nut allergy');
  await userEvent.click(within(picker).getByRole('button', { name: 'SAVE CHANGES' }));

  // The line updates, with the price the backend answered (15.00 + 2.00)
  const list = orderList(container);
  expect(await within(list).findByText('French Toast')).toBeInTheDocument();
  expect(within(list).queryByText('White Toast')).not.toBeInTheDocument();
  expect(within(list).getByText('NOTE: nut allergy')).toBeInTheDocument();
  expect(within(list).getByText('$17.00')).toBeInTheDocument();

  // The saved item (ID 100) was changed in place - not deleted and re-created
  const update = requests.find(r => r.method === 'PUT' && r.path === '/api/order-items/100/modifiers');
  expect(update.body).toEqual({ specialInstructions: 'nut allergy', modifierIds: [14] });
  expect(calls.filter(c => c.startsWith('DELETE'))).toEqual([]);

  // Still inside the edit window
  expect(screen.getByText('15 seconds to edit')).toBeInTheDocument();
  expect(within(list).getByRole('button', { name: 'x' })).toBeInTheDocument();
});

test('a change that arrives after the item locked is refused and the item shows as locked', async () => {
  const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Combo Plate'));
  await userEvent.click(choice(pickerFor('Combo Plate'), 'White Toast'));
  await userEvent.click(within(pickerFor('Combo Plate')).getByRole('button', { name: 'ADD TO ORDER' }));
  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));
  await screen.findByText('15 seconds to edit');

  // The backend locks the item while the picker is open
  await userEvent.click(within(orderList(container)).getByRole('button', { name: 'Edit Combo Plate' }));
  itemsAreLocked = true;
  await userEvent.click(choice(pickerFor('Combo Plate'), 'French Toast'));
  await userEvent.click(within(pickerFor('Combo Plate')).getByRole('button', { name: 'SAVE CHANGES' }));

  // The server is told, the old modifier stays, and the line can no longer be edited or removed
  await waitFor(() => expect(alertSpy).toHaveBeenCalledWith(
    'Too late to change this item - it was already sent to the prep station'
  ));
  const list = orderList(container);
  expect(within(list).getByText('White Toast')).toBeInTheDocument();
  expect(within(list).queryByText('French Toast')).not.toBeInTheDocument();
  expect(list.querySelectorAll('.order-item.locked')).toHaveLength(1);
  expect(within(list).queryByRole('button', { name: 'Edit Combo Plate' })).not.toBeInTheDocument();
  expect(within(list).queryByRole('button', { name: 'x' })).not.toBeInTheDocument();
});

test('when the backend refuses an item, nothing is sent and the server is told', async () => {
  const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
  vi.spyOn(console, 'error').mockImplementation(() => {});    // The failure is logged on purpose - keep the test output clean
  refuseItemSaves = true;
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Fries'));
  await userEvent.click(menuCard('Combo Plate'));
  await userEvent.click(choice(pickerFor('Combo Plate'), 'White Toast'));
  await userEvent.click(within(pickerFor('Combo Plate')).getByRole('button', { name: 'ADD TO ORDER' }));

  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));

  // No edit window, no send request - the kitchen got nothing, and the screen does not pretend otherwise
  // The server is shown the backend's own reason, not just "failed"
  await waitFor(() => expect(alertSpy).toHaveBeenCalledWith('"Bread Choice" allows at most 1 selection(s), but 2 were chosen'));
  expect(calls.filter(c => c.endsWith('/send'))).toEqual([]);
  expect(screen.queryByText('15 seconds to edit')).not.toBeInTheDocument();

  // The Fries did save, so they keep their ID (shown as a saved draft); the Combo Plate is still unsaved.
  // Pressing Send again will retry only the Combo Plate instead of saving the Fries twice.
  const list = orderList(container);
  expect(list.querySelectorAll('.order-item.saved-draft')).toHaveLength(1);
  expect(within(list).getByText('Combo Plate')).toBeInTheDocument();
});

test('opening an occupied table shows the modifiers and notes already on its items', async () => {
  existingOrders = [{ orderId: 77, tableId: 3, status: 'open' }];
  existingItems = [
    { orderItemId: 50, menuItemId: 4, price: 17.0, quantity: 1, status: 'fired', specialInstructions: 'nut allergy' },
    { orderItemId: 51, menuItemId: 2, price: 6.0, quantity: 1, status: 'draft', specialInstructions: null }
  ];
  existingItemModifiers = [
    { orderItemId: 50, modifierId: 14, name: 'French Toast', priceAdjustment: 2.0 }
  ];

  const { container } = await renderScreen({ tableId: 3, tableNumber: 'W3', status: 'occupied' });

  const list = orderList(container);
  expect(await within(list).findByText('Combo Plate')).toBeInTheDocument();
  expect(within(list).getByText('French Toast')).toBeInTheDocument();
  expect(within(list).getByText('NOTE: nut allergy')).toBeInTheDocument();
  expect(calls).toContain('GET /api/order-item-modifiers/order/77');

  // The fired Combo Plate is locked (no pencil); the draft Fries can still be edited
  expect(within(list).queryByRole('button', { name: 'Edit Combo Plate' })).not.toBeInTheDocument();
  expect(within(list).getByRole('button', { name: 'Edit Fries' })).toBeInTheDocument();
});

// ============================================================================
// Sending, closing, and clearing an order
// ============================================================================

test('SEND ORDER and SAVE DRAFT are disabled when there is nothing to send, so no empty order is created', async () => {
  await renderScreen();

  expect(screen.getByRole('button', { name: 'SEND ORDER' })).toBeDisabled();
  expect(screen.getByRole('button', { name: 'SAVE DRAFT' })).toBeDisabled();

  await userEvent.click(menuCard('Chicken Cutty'));

  expect(screen.getByRole('button', { name: 'SEND ORDER' })).toBeEnabled();
  expect(screen.getByRole('button', { name: 'SAVE DRAFT' })).toBeEnabled();
  // Still nothing but the two menu requests: no table and no order were created
  expect(calls).toEqual(['GET /api/menu-items', 'GET /api/menu-items/modifier-groups', 'GET /api/settings']);
});

test('opening a table asks the backend for its open order even when the table looked available', async () => {
  // Another server opened W3 a moment ago; the table object this screen was given is a few seconds old
  existingOrders = [{ orderId: 77, tableId: 3, status: 'open' }];
  existingItems = [{ orderItemId: 50, menuItemId: 1, price: 17.0, quantity: 1, status: 'fired' }];

  const { container } = await renderScreen({ tableId: 3, tableNumber: 'W3', status: 'available' });

  expect(await within(orderList(container)).findByText('Chicken Cutty')).toBeInTheDocument();
  expect(screen.getByText('All items sent to prep station')).toBeInTheDocument();
});

test('reopening a table during its edit window picks the countdown back up', async () => {
  existingOrders = [{ orderId: 77, tableId: 3, status: 'open' }];
  existingItems = [{
    orderItemId: 50, menuItemId: 1, price: 17.0, quantity: 1, status: 'limbo',
    delayExpiresAt: new Date(Date.now() + 9500).toISOString()             // 9 and a half seconds left
  }];

  const { container } = await renderScreen({ tableId: 3, tableNumber: 'W3', status: 'occupied' });

  // The countdown shows the seconds actually left (not a fresh 15), the button offers
  // Send Now, and the item can still be removed
  expect(await screen.findByText(/^[89] seconds to edit$/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'SEND NOW?' })).toBeInTheDocument();
  expect(within(orderList(container)).getByRole('button', { name: 'x' })).toBeInTheDocument();
});

test('reopening a table whose edit window just ran out shows its items as locked', async () => {
  existingOrders = [{ orderId: 77, tableId: 3, status: 'open' }];
  existingItems = [{
    orderItemId: 50, menuItemId: 1, price: 17.0, quantity: 1, status: 'limbo',
    delayExpiresAt: new Date(Date.now() - 500).toISOString()              // Expired half a second ago
  }];

  const { container } = await renderScreen({ tableId: 3, tableNumber: 'W3', status: 'occupied' });

  await within(orderList(container)).findByText('Chicken Cutty');
  expect(orderList(container).querySelectorAll('.order-item.locked')).toHaveLength(1);
  expect(within(orderList(container)).queryByRole('button', { name: 'x' })).not.toBeInTheDocument();
});

test('removing an item the backend already locked shows it as locked and tells the server', async () => {
  const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Chicken Cutty'));
  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));
  await screen.findByText('15 seconds to edit');

  // The edit window closes on the backend just before the "x" is pressed
  itemsAreLocked = true;
  await userEvent.click(within(orderList(container)).getByRole('button', { name: 'x' }));

  await waitFor(() => expect(alertSpy).toHaveBeenCalledWith(
    'Too late to remove this item - it was already sent to the prep station'
  ));
  const list = orderList(container);
  expect(within(list).getByText('Chicken Cutty')).toBeInTheDocument();
  expect(list.querySelectorAll('.order-item.locked')).toHaveLength(1);
  expect(within(list).queryByRole('button', { name: 'x' })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'SEND ORDER' })).toBeInTheDocument();     // No longer "SEND NOW?"
});

test('CLOSE ORDER is refused while the order has unsent items', async () => {
  const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
  await renderScreen();
  await userEvent.click(menuCard('Chicken Cutty'));

  await userEvent.click(screen.getByRole('button', { name: 'CLOSE ORDER' }));

  expect(alertSpy).toHaveBeenCalledWith('Send or remove the unsent items before closing the check.');
  expect(screen.queryByRole('button', { name: 'PROCESS PAYMENT' })).not.toBeInTheDocument();
  expect(calls).not.toContain('POST /api/payments');
});

test('CLOSE ORDER shows the check the backend worked out and pays exactly that amount', async () => {
  existingOrders = [{ orderId: 77, tableId: 3, status: 'open' }];
  existingItems = [{ orderItemId: 50, menuItemId: 1, price: 17.0, quantity: 1, status: 'fired' }];
  // Another server added Fries to this table after this screen loaded: the screen shows
  // one item ($17.00) but the real check is $23.00 + tax
  orderCheck = { orderId: 77, status: 'open', itemCount: 2, unsentItemCount: 0, subtotal: 23.0, tax: 0.69, total: 23.69, taxRate: 0.03 };
  const { container } = await renderScreen({ tableId: 3, tableNumber: 'W3', status: 'occupied' });
  await within(orderList(container)).findByText('Chicken Cutty');

  await userEvent.click(screen.getByRole('button', { name: 'CLOSE ORDER' }));

  // The payment window shows the backend's numbers, not the screen's
  const summary = await waitFor(() => {
    const element = document.querySelector('.order-summary');
    expect(element).not.toBeNull();
    return element;
  });
  expect(within(summary).getByText('$23.00')).toBeInTheDocument();
  expect(within(summary).getByText('Tax (3%):')).toBeInTheDocument();
  expect(within(summary).getByText('$0.69')).toBeInTheDocument();
  expect(within(summary).getByText('$23.69')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: 'PROCESS PAYMENT' }));

  expect(await screen.findByText('Payment Processed Successfully!')).toBeInTheDocument();
  const payment = requests.find(r => r.method === 'POST' && r.path === '/api/payments');
  expect(payment.body).toEqual({ orderId: 77, amount: 23.69, tipAmount: 0, paymentMethod: 'credit_card' });
});

test('a tip percentage is worked out from the backend check total', async () => {
  existingOrders = [{ orderId: 77, tableId: 3, status: 'open' }];
  existingItems = [{ orderItemId: 50, menuItemId: 1, price: 17.0, quantity: 1, status: 'fired' }];
  orderCheck = { orderId: 77, status: 'open', itemCount: 1, unsentItemCount: 0, subtotal: 50.0, tax: 1.5, total: 51.5, taxRate: 0.03 };
  const { container } = await renderScreen({ tableId: 3, tableNumber: 'W3', status: 'occupied' });
  await within(orderList(container)).findByText('Chicken Cutty');
  await userEvent.click(screen.getByRole('button', { name: 'CLOSE ORDER' }));
  await screen.findByRole('button', { name: 'PROCESS PAYMENT' });

  await userEvent.click(screen.getByRole('button', { name: '20%' }));

  // 20% of 51.50 = 10.30, grand total 61.80
  expect(screen.getByPlaceholderText('Custom tip amount')).toHaveValue(10.3);
  expect(within(document.querySelector('.grand-total')).getByText('$61.80')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: 'PROCESS PAYMENT' }));
  await screen.findByText('Payment Processed Successfully!');
  const payment = requests.find(r => r.method === 'POST' && r.path === '/api/payments');
  expect(payment.body).toEqual({ orderId: 77, amount: 51.5, tipAmount: 10.3, paymentMethod: 'credit_card' });
});

test('a refused payment shows the reason the backend gave and leaves the order open', async () => {
  const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
  existingOrders = [{ orderId: 77, tableId: 3, status: 'open' }];
  existingItems = [{ orderItemId: 50, menuItemId: 1, price: 17.0, quantity: 1, status: 'fired' }];
  paymentRefusal = 'This order has 1 unsent item(s). Send or remove them before closing the check.';
  const { container } = await renderScreen({ tableId: 3, tableNumber: 'W3', status: 'occupied' });
  await within(orderList(container)).findByText('Chicken Cutty');
  await userEvent.click(screen.getByRole('button', { name: 'CLOSE ORDER' }));

  await userEvent.click(await screen.findByRole('button', { name: 'PROCESS PAYMENT' }));

  await waitFor(() => expect(alertSpy).toHaveBeenCalledWith('This order has 1 unsent item(s). Send or remove them before closing the check.'));
  expect(screen.queryByText('Payment Processed Successfully!')).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'PROCESS PAYMENT' })).toBeEnabled();
});

test('the payment window says so when the check cannot be loaded', async () => {
  vi.spyOn(console, 'error').mockImplementation(() => {});    // The failure is logged on purpose - keep the test output clean
  existingOrders = [{ orderId: 77, tableId: 3, status: 'open' }];
  existingItems = [{ orderItemId: 50, menuItemId: 1, price: 17.0, quantity: 1, status: 'fired' }];
  orderCheck = null;
  const realFetch = global.fetch;
  global.fetch = vi.fn(async (url, options = {}) => {
    if (url.endsWith('/api/orders/77/check')) return jsonResponse({ message: 'Order not found' }, 404);
    return realFetch(url, options);
  });
  const { container } = await renderScreen({ tableId: 3, tableNumber: 'W3', status: 'occupied' });
  await within(orderList(container)).findByText('Chicken Cutty');

  await userEvent.click(screen.getByRole('button', { name: 'CLOSE ORDER' }));

  expect(await screen.findByText(/Could not load the check/)).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'PROCESS PAYMENT' })).not.toBeInTheDocument();
});

test('after a check is paid the screen starts over as a new order', async () => {
  existingOrders = [{ orderId: 77, tableId: 3, status: 'open' }];
  existingItems = [{ orderItemId: 50, menuItemId: 1, price: 17.0, quantity: 1, status: 'fired' }];
  const { container } = await renderScreen({ tableId: 3, tableNumber: 'W3', status: 'occupied' });
  await within(orderList(container)).findByText('Chicken Cutty');
  await userEvent.click(screen.getByRole('button', { name: 'CLOSE ORDER' }));
  await userEvent.click(await screen.findByRole('button', { name: 'PROCESS PAYMENT' }));

  // The success message stays up for 2 seconds, then the screen resets
  expect(
    await screen.findByText('Current Order - New Quick Order', {}, { timeout: 4000 })
  ).toBeInTheDocument();
  expect(within(orderList(container)).queryByText('Chicken Cutty')).not.toBeInTheDocument();
  expect(screen.getByText('Add items to order')).toBeInTheDocument();
});

test('an order with every item removed can be cleared off its table', async () => {
  existingOrders = [{ orderId: 77, tableId: 3, status: 'open' }];
  existingItems = [{ orderItemId: 51, menuItemId: 2, price: 6.0, quantity: 1, status: 'draft' }];
  const { container } = await renderScreen({ tableId: 3, tableNumber: 'W3', status: 'occupied' });
  await within(orderList(container)).findByText('Fries');

  // The guests changed their minds: remove the only item
  await userEvent.click(within(orderList(container)).getByRole('button', { name: 'x' }));

  // There is nothing to pay, so the button offers to clear the table instead
  const clearButton = await screen.findByRole('button', { name: 'CLEAR TABLE' });
  expect(screen.queryByRole('button', { name: 'CLOSE ORDER' })).not.toBeInTheDocument();
  await userEvent.click(clearButton);

  expect(await screen.findByText('Current Order - New Quick Order')).toBeInTheDocument();
  expect(calls).toContain('DELETE /api/orders/77');
  expect(calls).not.toContain('POST /api/payments');
});

test('leaving the screen with unsaved items asks first, and staying keeps them', async () => {
  const setCurrentView = vi.fn();
  const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);         // The server chooses to stay
  const { container } = render(
    <CreateOrder setCurrentView={setCurrentView} selectedTable={null} setSelectedTable={vi.fn()} />
  );
  await screen.findByRole('heading', { name: 'Chicken Cutty' });
  await userEvent.click(menuCard('Chicken Cutty'));

  await userEvent.click(screen.getByRole('button', { name: 'FLOOR MAP' }));

  expect(confirmSpy).toHaveBeenCalledWith('This order has items that have not been saved or sent. Leave and lose them?');
  expect(setCurrentView).not.toHaveBeenCalled();
  expect(within(orderList(container)).getByText('Chicken Cutty')).toBeInTheDocument();

  // Asked again, the server chooses to leave
  confirmSpy.mockReturnValue(true);
  await userEvent.click(screen.getByRole('button', { name: 'FLOOR MAP' }));
  expect(setCurrentView).toHaveBeenCalledWith('floorMap');
});

test('leaving the screen is not questioned when everything on the order is saved', async () => {
  const setCurrentView = vi.fn();
  const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
  render(<CreateOrder setCurrentView={setCurrentView} selectedTable={null} setSelectedTable={vi.fn()} />);
  await screen.findByRole('heading', { name: 'Chicken Cutty' });
  await userEvent.click(menuCard('Chicken Cutty'));
  await userEvent.click(screen.getByRole('button', { name: 'SAVE DRAFT' }));
  await screen.findByText('Draft saved! Items remain editable');

  await userEvent.click(screen.getByRole('button', { name: 'FLOOR MAP' }));

  expect(confirmSpy).not.toHaveBeenCalled();
  expect(setCurrentView).toHaveBeenCalledWith('floorMap');
});

// ============================================================================
// 86'd items
// ============================================================================

test('an 86\'d item stays on the menu, greyed out, and cannot be added', async () => {
  menuNow = MENU.map(item => (item.name === 'Fries' ? { ...item, is86d: true } : item));
  const { container } = await renderScreen();

  // It is still shown, marked 86'D in place of its price
  const friesCard = menuCard('Fries').closest('.menu-item-card');
  expect(friesCard).toHaveClass('is-86d');
  expect(within(friesCard).getByText("86'D")).toBeInTheDocument();
  expect(within(friesCard).queryByText('$6.00')).not.toBeInTheDocument();

  // Tapping it does nothing; other items still work
  await userEvent.click(menuCard('Fries'));
  await userEvent.click(menuCard('Chicken Cutty'));
  expect(within(orderList(container)).queryByText('Fries')).not.toBeInTheDocument();
  expect(within(orderList(container)).getByText('Chicken Cutty')).toBeInTheDocument();
});

test('an item 86\'d after it was added is caught at Send, and the server is told which one', async () => {
  const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Fries'));

  // The kitchen 86's Fries. The order screen picks the change up on its next menu refresh (5 seconds).
  menuNow = MENU.map(item => (item.name === 'Fries' ? { ...item, is86d: true } : item));
  await waitFor(
    () => expect(menuCard('Fries').closest('.menu-item-card')).toHaveClass('is-86d'),
    { timeout: 7000 }
  );

  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));

  expect(alertSpy).toHaveBeenCalledWith("Fries was just 86'd. Remove it from the order first.");
  expect(calls).not.toContain('POST /api/orders');                   // Nothing was created or sent
  expect(within(orderList(container)).getByText('Fries')).toBeInTheDocument();
}, 12000);

// ============================================================================
// One ring-in path, and a check closed on another terminal
// ============================================================================

test('a plain item is rung in through the same request as every other item, with no price in it', async () => {
  await renderScreen();
  await userEvent.click(menuCard('Chicken Cutty'));

  await userEvent.click(screen.getByRole('button', { name: 'SAVE DRAFT' }));
  await screen.findByText('Draft saved! Items remain editable');

  // The backend works out the price from the menu. A price sent from here used to be saved as-is.
  const save = requests.find(r => r.method === 'POST' && r.path === '/api/order-items');
  // (ringInPosition is the item's place on the order: the one thing besides the item itself the screen does say)
  expect(save.body).toEqual({ orderId: 77, menuItemId: 1, quantity: 1, specialInstructions: '', modifierIds: [], ringInPosition: 0 });
  expect(save.body).not.toHaveProperty('price');
  expect(save.body).not.toHaveProperty('status');
});

test('the price shown after saving is the one the backend worked out', async () => {
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Combo Plate'));
  await userEvent.click(choice(pickerFor('Combo Plate'), 'French Toast'));
  await userEvent.click(within(pickerFor('Combo Plate')).getByRole('button', { name: 'ADD TO ORDER' }));

  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));
  await screen.findByText('15 seconds to edit');

  // 15.00 + 2.00 for French Toast
  const totals = container.querySelector('.order-totals');
  expect(within(totals).getByText('$17.00')).toBeInTheDocument();
});

test('Send on a check that was closed on another terminal says so and keeps the unsent items for a new check', async () => {
  const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
  vi.spyOn(console, 'error').mockImplementation(() => {});    // The refusal is logged on purpose - keep the test output clean
  // Table W3 has an open check with one item the kitchen already has
  existingOrders = [{ orderId: 77, tableId: 3, status: 'open' }];
  existingItems = [{ orderItemId: 50, menuItemId: 1, price: 17.0, quantity: 1, status: 'fired' }];
  const { container } = await renderScreen({ tableId: 3, tableNumber: 'W3', status: 'occupied' });
  await within(orderList(container)).findByText('Chicken Cutty');

  // Another server takes payment for W3 on a different terminal. This screen does not know yet.
  checkClosedElsewhere = true;
  existingOrders = [];                                              // The table has no open check any more
  await userEvent.click(menuCard('Fries'));
  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));

  await waitFor(() => expect(alertSpy).toHaveBeenCalledWith(
    'This check was already closed on another terminal. Nothing was added or sent. Your unsent items are still here: press SEND ORDER to start a new check for them.'
  ));
  // Nothing went to the kitchen, and the screen does not pretend otherwise
  expect(calls.filter(c => c.endsWith('/send'))).toEqual([]);
  expect(screen.queryByText('15 seconds to edit')).not.toBeInTheDocument();

  // The paid item is gone from the screen; the round that was not sent is still there
  const list = orderList(container);
  await waitFor(() => expect(within(list).queryByText('Chicken Cutty')).not.toBeInTheDocument());
  expect(within(list).getByText('Fries')).toBeInTheDocument();

  // Pressing Send again starts a NEW check on the table instead of adding to the closed one
  checkClosedElsewhere = false;
  const callsBefore = calls.length;
  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));
  await screen.findByText('15 seconds to edit');
  expect(calls.slice(callsBefore)).toEqual([
    'POST /api/orders',
    'POST /api/order-items',
    'POST /api/order-items/order/77/send'
  ]);
});

test('SEND NOW? on a check that was closed on another terminal says so and stops the countdown', async () => {
  const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
  vi.spyOn(console, 'error').mockImplementation(() => {});
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Chicken Cutty'));
  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));
  await screen.findByText('15 seconds to edit');

  checkClosedElsewhere = true;
  await userEvent.click(screen.getByRole('button', { name: 'SEND NOW?' }));

  await waitFor(() => expect(alertSpy).toHaveBeenCalledWith(
    'This check was already closed on another terminal. Nothing was added or sent.'
  ));
  // The items on the closed check are off the screen and no countdown is left running
  expect(within(orderList(container)).queryByText('Chicken Cutty')).not.toBeInTheDocument();
  expect(screen.queryByText(/seconds to edit/)).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'SEND ORDER' })).toBeInTheDocument();
});

test('changing modifiers on a check that was closed on another terminal says the check was closed, not that the item was sent', async () => {
  const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Combo Plate'));
  await userEvent.click(choice(pickerFor('Combo Plate'), 'White Toast'));
  await userEvent.click(within(pickerFor('Combo Plate')).getByRole('button', { name: 'ADD TO ORDER' }));
  await userEvent.click(screen.getByRole('button', { name: 'SAVE DRAFT' }));
  await screen.findByText('Draft saved! Items remain editable');

  checkClosedElsewhere = true;
  await userEvent.click(within(orderList(container)).getByRole('button', { name: 'Edit Combo Plate' }));
  await userEvent.click(choice(pickerFor('Combo Plate'), 'French Toast'));
  await userEvent.click(within(pickerFor('Combo Plate')).getByRole('button', { name: 'SAVE CHANGES' }));

  await waitFor(() => expect(alertSpy).toHaveBeenCalledWith(
    'This check was already closed on another terminal. Nothing was added or sent.'
  ));
  expect(alertSpy).not.toHaveBeenCalledWith('Too late to change this item - it was already sent to the prep station');
});

test('removing an item from a check that was closed on another terminal says the check was closed, not that the item was sent', async () => {
  const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Chicken Cutty'));
  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));
  await screen.findByText('15 seconds to edit');

  // The check is paid on another terminal while this screen is still counting down
  checkClosedElsewhere = true;
  await userEvent.click(within(orderList(container)).getByRole('button', { name: 'x' }));

  await waitFor(() => expect(alertSpy).toHaveBeenCalledWith(
    'This check was already closed on another terminal. Nothing was removed.'
  ));
  expect(alertSpy).not.toHaveBeenCalledWith('Too late to remove this item - it was already sent to the prep station');
  // The paid check is off the screen and no countdown is left running
  expect(within(orderList(container)).queryByText('Chicken Cutty')).not.toBeInTheDocument();
  expect(screen.queryByText(/seconds to edit/)).not.toBeInTheDocument();
});

test('paying a check that was already paid on another terminal says so and clears it off the screen', async () => {
  const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
  existingOrders = [{ orderId: 77, tableId: 3, status: 'open' }];
  existingItems = [{ orderItemId: 50, menuItemId: 1, price: 17.0, quantity: 1, status: 'fired' }];
  const { container } = await renderScreen({ tableId: 3, tableNumber: 'W3', status: 'occupied' });
  await within(orderList(container)).findByText('Chicken Cutty');
  await userEvent.click(screen.getByRole('button', { name: 'CLOSE ORDER' }));
  await screen.findByRole('button', { name: 'PROCESS PAYMENT' });

  // Two servers have the payment window up for W3. The other one presses first.
  checkClosedElsewhere = true;
  existingOrders = [];                                              // The table has no open check any more
  await userEvent.click(screen.getByRole('button', { name: 'PROCESS PAYMENT' }));

  await waitFor(() => expect(alertSpy).toHaveBeenCalledWith(
    'This check was already closed on another terminal. Nothing was charged.'
  ));
  // The payment window is gone, nothing claims the payment went through, and the paid item is off the screen
  expect(screen.queryByRole('button', { name: 'PROCESS PAYMENT' })).not.toBeInTheDocument();
  expect(screen.queryByText('Payment Processed Successfully!')).not.toBeInTheDocument();
  await waitFor(() => expect(within(orderList(container)).queryByText('Chicken Cutty')).not.toBeInTheDocument());
});

test('PROCESS PAYMENT cannot be pressed a second time while the payment is on its way', async () => {
  existingOrders = [{ orderId: 77, tableId: 3, status: 'open' }];
  existingItems = [{ orderItemId: 50, menuItemId: 1, price: 17.0, quantity: 1, status: 'fired' }];
  const { container } = await renderScreen({ tableId: 3, tableNumber: 'W3', status: 'occupied' });
  await within(orderList(container)).findByText('Chicken Cutty');
  await userEvent.click(screen.getByRole('button', { name: 'CLOSE ORDER' }));
  await screen.findByRole('button', { name: 'PROCESS PAYMENT' });

  // A slow network: the backend does not answer until the test lets it
  let letThePaymentThrough;
  holdPayments = new Promise(resolve => { letThePaymentThrough = resolve; });
  await userEvent.click(screen.getByRole('button', { name: 'PROCESS PAYMENT' }));

  // While it waits, the button says so and is switched off...
  const waiting = await screen.findByRole('button', { name: 'PROCESSING...' });
  expect(waiting).toBeDisabled();
  // ...so an impatient second tap sends nothing
  await userEvent.click(waiting);
  expect(requests.filter(r => r.method === 'POST' && r.path === '/api/payments')).toHaveLength(1);

  letThePaymentThrough();
  await screen.findByText('Payment Processed Successfully!');
  expect(requests.filter(r => r.method === 'POST' && r.path === '/api/payments')).toHaveLength(1);
});

// ============================================================================
// One tax rate: the running total uses the rate the backend charges
// ============================================================================

test('the running total is worked out with the tax rate the backend gives, not a copy kept on the screen', async () => {
  // The owner changed notta.tax-rate to 8.25%. The screen used to hold its own 3%,
  // so the guest was shown $23.69 and then charged $24.90.
  taxRate = 0.0825;
  const { container } = await renderScreen();

  await userEvent.click(menuCard('Chicken Cutty'));
  await userEvent.click(menuCard('Fries'));

  // 17.00 + 6.00 = 23.00, 8.25% tax = 1.8975 -> 1.90, total = 24.90
  const totals = container.querySelector('.order-totals');
  expect(await within(totals).findByText('Tax (8.25%):')).toBeInTheDocument();
  expect(within(totals).getByText('$23.00')).toBeInTheDocument();
  expect(within(totals).getByText('$1.90')).toBeInTheDocument();
  expect(within(totals).getByText('$24.90')).toBeInTheDocument();
});

test('when the tax rate cannot be loaded, the screen shows no tax or total rather than a guess', async () => {
  settingsFail = true;
  vi.spyOn(console, 'error').mockImplementation(() => {});      // The failed load is logged; keep the test output quiet
  const { container } = await renderScreen();

  await userEvent.click(menuCard('Chicken Cutty'));
  await userEvent.click(menuCard('Fries'));

  // The subtotal needs no rate, so it is still shown. Tax and total are left blank:
  // a number worked out from a rate the screen made up could differ from the check.
  const totals = container.querySelector('.order-totals');
  expect(within(totals).getByText('$23.00')).toBeInTheDocument();
  expect(within(totals).getByText('Tax:')).toBeInTheDocument();
  expect(within(totals).getAllByText('--')).toHaveLength(2);
  expect(within(totals).queryByText('$0.69')).not.toBeInTheDocument();

  // Taking orders does not depend on it: the item can still be sent
  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));
  expect(await screen.findByText('15 seconds to edit')).toBeInTheDocument();
});

// ============================================================================
// One clock: the countdown is measured on the backend's clock
// ============================================================================

const THREE_HOURS_MS = 3 * 60 * 60 * 1000;

test('the edit window still closes on time on a tablet whose clock is hours behind the backend\'s', async () => {
  // The backend says "the window closes at 10:00:01" by its own clock. A tablet that
  // thinks it is 7:00 used to count down three hours, with items that looked editable
  // long after the kitchen had them.
  backendClockAheadMs = THREE_HOURS_MS;
  editWindowMs = 1200;  // Shrink the window so the test does not wait 15 real seconds
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Chicken Cutty'));

  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));

  expect(
    await screen.findByText('Items locked and sent to prep station', {}, { timeout: 4000 })
  ).toBeInTheDocument();
  expect(orderList(container).querySelectorAll('.order-item.locked')).toHaveLength(1);
});

test('the edit window stays open for its full time on a tablet whose clock is hours ahead of the backend\'s', async () => {
  // The other direction: a tablet that thinks it is 1:00 when the backend says 10:00
  // used to see the closing time as long past, and locked the items after one second
  // while the backend still allowed changes.
  backendClockAheadMs = -THREE_HOURS_MS;
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Chicken Cutty'));

  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));
  await screen.findByText('15 seconds to edit');

  // A little over a second later the countdown is still running, and the item can still be removed
  expect(await screen.findByText(/^1[34] seconds to edit$/, {}, { timeout: 3000 })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'SEND NOW?' })).toBeInTheDocument();
  expect(within(orderList(container)).getByRole('button', { name: 'x' })).toBeInTheDocument();
});

test('reopening a table during its edit window shows the seconds really left, whatever this tablet\'s clock says', async () => {
  backendClockAheadMs = THREE_HOURS_MS;
  existingOrders = [{ orderId: 77, tableId: 3, status: 'open' }];
  existingItems = [{
    orderItemId: 50, menuItemId: 1, price: 17.0, quantity: 1, status: 'limbo', ringInPosition: 0,
    delayExpiresAt: asBackendTime(backendNow() + 9500)                    // 9 and a half seconds left, by the backend's clock
  }];

  await renderScreen({ tableId: 3, tableNumber: 'W3', status: 'occupied' });

  expect(await screen.findByText(/^[89] seconds to edit$/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'SEND NOW?' })).toBeInTheDocument();
});

// ============================================================================
// Ticket order: every item says where it sits on the order
// ============================================================================

// The ringInPosition of every item the screen asked the backend to save, in the order asked
const positionsRungIn = () =>
  requests.filter(r => r.method === 'POST' && r.path === '/api/order-items').map(r => r.body.ringInPosition);

test('each item is rung in with its place on the order, so the ticket can list them as they were entered', async () => {
  // The saves go out together and can reach the backend in any order. The place each
  // item holds on this screen is the only record of what the server tapped first.
  await renderScreen();
  await userEvent.click(menuCard('Chicken Cutty'));
  await userEvent.click(menuCard('Fries'));
  await userEvent.click(menuCard('Chicken Cutty'));

  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));
  await screen.findByText('15 seconds to edit');

  expect(positionsRungIn()).toEqual([0, 1, 2]);

  // An item added during the edit window takes the next place
  await userEvent.click(menuCard('Fries'));
  await waitFor(() => expect(positionsRungIn()).toEqual([0, 1, 2, 3]));
});

test('an item added to a reopened table goes after everything already on the check', async () => {
  // Positions 0 and 4: an earlier item was removed, so the numbers have a gap. The next
  // item goes after the highest one in use, not at "number of items on screen".
  existingOrders = [{ orderId: 77, tableId: 3, status: 'open' }];
  existingItems = [
    { orderItemId: 50, menuItemId: 1, price: 17.0, quantity: 1, status: 'fired', ringInPosition: 0 },
    { orderItemId: 51, menuItemId: 2, price: 6.0, quantity: 1, status: 'fired', ringInPosition: 4 }
  ];
  const { container } = await renderScreen({ tableId: 3, tableNumber: 'W3', status: 'occupied' });
  await within(orderList(container)).findByText('Fries');

  await userEvent.click(menuCard('Chicken Cutty'));
  await userEvent.click(menuCard('Fries'));
  await userEvent.click(screen.getByRole('button', { name: 'SAVE DRAFT' }));
  await screen.findByText('Draft saved! Items remain editable');

  expect(positionsRungIn()).toEqual([5, 6]);

  // A second round saved afterwards carries on from there
  await userEvent.click(menuCard('Chicken Cutty'));
  await userEvent.click(screen.getByRole('button', { name: 'SAVE DRAFT' }));
  await waitFor(() => expect(positionsRungIn()).toEqual([5, 6, 7]));
});

test('an item that was removed before it was saved leaves no hole in the positions sent', async () => {
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Chicken Cutty'));
  await userEvent.click(menuCard('Fries'));
  await userEvent.click(menuCard('Chicken Cutty'));
  // Take the Fries back off before anything is saved
  await userEvent.click(within(orderList(container)).getAllByRole('button', { name: 'x' })[1]);

  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));
  await screen.findByText('15 seconds to edit');

  expect(positionsRungIn()).toEqual([0, 1]);
});
