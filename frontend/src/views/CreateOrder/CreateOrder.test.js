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
let itemsAreLocked;   // true = the fake backend answers 409 (already sent to kitchen) to modifier changes

// What the real backend does when it saves modifiers: menu price + the chosen upcharges
const priceWithUpcharges = (menuItemId, modifierIds) => {
  const basePrice = MENU.find(m => m.menuItemId === menuItemId).price;
  const allModifiers = MODIFIER_GROUPS.flatMap(entry => entry.groups).flatMap(group => group.modifiers);
  return modifierIds.reduce(
    (sum, id) => sum + allModifiers.find(modifier => modifier.modifierId === id).priceAdjustment,
    basePrice
  );
};

const jsonResponse = (data, status = 200) => ({
  ok: status >= 200 && status < 300,
  status,
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
  itemsAreLocked = false;

  global.fetch = jest.fn(async (url, options = {}) => {
    const method = options.method || 'GET';
    const path = url.replace('http://localhost:8080', '');
    const body = options.body ? JSON.parse(options.body) : null;
    calls.push(`${method} ${path}`);
    requests.push({ method, path, body });

    if (method === 'GET' && path === '/api/menu-items') return jsonResponse(MENU);
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
    if (method === 'POST' && path === '/api/order-items') {
      return jsonResponse({ orderItemId: nextOrderItemId++, ...body }, 201);
    }
    if (method === 'POST' && path === '/api/order-items/with-modifiers') {
      if (refuseItemSaves) return jsonResponse({ message: 'Invalid modifier selection' }, 400);
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
  // (the screen loads the menu and its modifier groups when it opens, and that is all)
  expect(calls).toEqual(['GET /api/menu-items', 'GET /api/menu-items/modifier-groups']);
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

  // Requests, in order: load menu + modifiers, find next QO number, create table, open order, seat table, save item, send
  expect(calls).toEqual([
    'GET /api/menu-items',
    'GET /api/menu-items/modifier-groups',
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
  expect(calls).toEqual(['GET /api/menu-items', 'GET /api/menu-items/modifier-groups']);
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
  const saves = requests.filter(r => r.method === 'POST' && r.path === '/api/order-items/with-modifiers');
  expect(saves).toHaveLength(1);
  expect(saves[0].body).toEqual({
    orderId: 77,
    menuItemId: 4,
    quantity: 1,
    specialInstructions: 'no butter',
    modifierIds: [14, 24]
  });
  // ...and it was NOT also saved through the plain endpoint
  expect(calls).not.toContain('POST /api/order-items');
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
  expect(calls).toEqual(['GET /api/menu-items', 'GET /api/menu-items/modifier-groups']);

  // Saving the draft sends the note along
  await userEvent.click(screen.getByRole('button', { name: 'SAVE DRAFT' }));
  await screen.findByText('Draft saved! Items remain editable');
  const save = requests.find(r => r.path === '/api/order-items/with-modifiers');
  expect(save.body).toEqual({
    orderId: 77,
    menuItemId: 2,
    quantity: 1,
    specialInstructions: 'extra crispy',
    modifierIds: []
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
  const alertSpy = jest.spyOn(window, 'alert').mockImplementation(() => {});
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
  const alertSpy = jest.spyOn(window, 'alert').mockImplementation(() => {});
  jest.spyOn(console, 'error').mockImplementation(() => {});    // The failure is logged on purpose - keep the test output clean
  refuseItemSaves = true;
  const { container } = await renderScreen();
  await userEvent.click(menuCard('Fries'));
  await userEvent.click(menuCard('Combo Plate'));
  await userEvent.click(choice(pickerFor('Combo Plate'), 'White Toast'));
  await userEvent.click(within(pickerFor('Combo Plate')).getByRole('button', { name: 'ADD TO ORDER' }));

  await userEvent.click(screen.getByRole('button', { name: 'SEND ORDER' }));

  // No edit window, no send request - the kitchen got nothing, and the screen does not pretend otherwise
  await waitFor(() => expect(alertSpy).toHaveBeenCalledWith('Failed to send order'));
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
