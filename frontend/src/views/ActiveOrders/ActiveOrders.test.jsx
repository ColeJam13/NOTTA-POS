import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ActiveOrders from './ActiveOrders';
import { StaffContext } from '../../context/StaffContext';

/**
 * Behavior tests for the Active Orders screen (the kitchen ticket).
 *
 * WHAT: Loads the screen against a fake backend and checks that each item's
 *       modifiers and kitchen note are printed under it, in both the FOH and
 *       BOH views.
 * WHY:  "Combo Plate" alone is not enough to cook it. If a modifier or an
 *       allergy note is missing from the ticket, the wrong food goes out.
 *
 * The fake backend works the same way as in CreateOrder.test.jsx: global.fetch
 * is replaced with a function that returns canned JSON.
 */

// What GET /api/orders/active returns: one open order with three items
const ACTIVE_ORDERS = [
  {
    orderId: 77,
    tableId: 3,
    tableNumber: 'W3',
    isQuickOrder: false,
    serverName: 'Sarah',
    items: [
      {
        orderItemId: 100, menuItemId: 4, name: 'Combo Plate', quantity: 1, price: 17.0, status: 'pending',
        specialInstructions: 'nut allergy',
        modifiers: [
          { orderItemId: 100, modifierId: 14, name: 'French Toast', priceAdjustment: 2.0 },
          { orderItemId: 100, modifierId: 16, name: 'Hashbrowns', priceAdjustment: 0 }
        ]
      },
      {
        orderItemId: 101, menuItemId: 2, name: 'Fries', quantity: 1, price: 6.0, status: 'pending',
        specialInstructions: null,
        modifiers: []
      },
      {
        // Still in the edit window: front of house sees it, the kitchen does not yet
        orderItemId: 102, menuItemId: 3, name: 'Latte', quantity: 1, price: 6.0, status: 'limbo',
        specialInstructions: 'oat milk',
        modifiers: [{ orderItemId: 102, modifierId: 31, name: 'Vanilla', priceAdjustment: 1.0 }]
      }
    ]
  }
];

// What GET /api/prep-stations returns
const STATIONS = [
  { prepStationId: 1, name: 'Kitchen', isActive: true },
  { prepStationId: 2, name: 'Bar', isActive: true }
];

let activeOrders;     // what GET /api/orders/active returns (a test can replace it)
let backendClockAheadMs;    // how far the fake backend's clock is ahead of this device's (negative = behind)

// What time the fake backend thinks it is. A test moves it away from this device's
// clock to stand for a kitchen tablet whose own clock is wrong.
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

beforeEach(() => {
  activeOrders = ACTIVE_ORDERS;
  backendClockAheadMs = 0;

  global.fetch = vi.fn(async (url) => {
    const path = url.replace('http://localhost:8080', '');
    if (path === '/api/orders/active') return answer(activeOrders);
    if (path === '/api/prep-stations') return answer(STATIONS);
    throw new Error(`Unexpected request in test: ${path}`);
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

// Render the screen and wait for the order card to load
const renderScreen = async () => {
  const view = render(<ActiveOrders setCurrentView={vi.fn()} setSelectedTable={vi.fn()} />);
  await screen.findByText('Table W3 - Order #77');
  return view;
};

// The ticket line (item row + modifiers + note) that contains the given item name
const ticketFor = (name) => screen.getByText(name).closest('.order-item-ticket');

test('FOH view prints each item\'s modifiers and kitchen note under it', async () => {
  await renderScreen();

  const comboPlate = ticketFor('Combo Plate');
  expect(within(comboPlate).getByText('French Toast')).toBeInTheDocument();
  expect(within(comboPlate).getByText('Hashbrowns')).toBeInTheDocument();
  expect(within(comboPlate).getByText('NOTE: nut allergy')).toBeInTheDocument();

  // Items still in the edit window show their modifiers and note to the front of house too
  const latte = ticketFor('Latte');
  expect(within(latte).getByText('Vanilla')).toBeInTheDocument();
  expect(within(latte).getByText('NOTE: oat milk')).toBeInTheDocument();
});

test('BOH view prints modifiers and notes for the items the kitchen has received', async () => {
  await renderScreen();

  await userEvent.click(screen.getByRole('button', { name: 'BOH VIEW' }));

  const comboPlate = ticketFor('Combo Plate');
  expect(within(comboPlate).getByText('French Toast')).toBeInTheDocument();
  expect(within(comboPlate).getByText('Hashbrowns')).toBeInTheDocument();
  expect(within(comboPlate).getByText('NOTE: nut allergy')).toBeInTheDocument();
  expect(within(comboPlate).getByRole('button', { name: 'START' })).toBeInTheDocument();

  // The Latte is still in limbo, so neither it nor its modifiers have reached the kitchen
  expect(screen.queryByText('Latte')).not.toBeInTheDocument();
  expect(screen.queryByText('Vanilla')).not.toBeInTheDocument();
  expect(screen.queryByText('NOTE: oat milk')).not.toBeInTheDocument();
});

test('an item with no modifiers and no note prints as a single line', async () => {
  await renderScreen();

  const fries = ticketFor('Fries');
  expect(fries.querySelector('.ticket-modifiers')).not.toBeInTheDocument();
  expect(fries.querySelector('.ticket-note')).not.toBeInTheDocument();
});

// ============================================================================
// Kitchen display (BOH view): stations, waiting time, finished tickets
// ============================================================================

const minutesAgo = (minutes) => new Date(Date.now() - minutes * 60 * 1000).toISOString();

// One item on a kitchen ticket
const ticketItem = (orderItemId, name, status, prepStationId, sentMinutesAgo) => ({
  orderItemId, menuItemId: orderItemId, name, quantity: 1, price: 10.0, status,
  specialInstructions: null, modifiers: [], prepStationId, sentAt: minutesAgo(sentMinutesAgo)
});

// Three tables: W1 has food and a drink, sent 4 minutes ago. W2 has food sent 12 minutes ago.
// W3's food was sent 20 minutes ago and one plate is already finished.
const KITCHEN_ORDERS = [
  { orderId: 1, tableId: 1, tableNumber: 'W1', isQuickOrder: false, serverName: 'Sarah', items: [
    ticketItem(11, 'Chicken Cutty', 'pending', 1, 4),
    ticketItem(12, 'Mimosa', 'pending', 2, 4)
  ] },
  { orderId: 2, tableId: 2, tableNumber: 'W2', isQuickOrder: false, serverName: 'Sarah', items: [
    ticketItem(21, 'Garden Toast', 'fired', 1, 12)
  ] },
  { orderId: 3, tableId: 3, tableNumber: 'W3', isQuickOrder: false, serverName: 'Sarah', items: [
    ticketItem(31, 'Pancake Stack', 'completed', 1, 20),
    ticketItem(32, 'Shrimp & Grits', 'pending', 1, 20)
  ] }
];

const renderKitchen = async () => {
  activeOrders = KITCHEN_ORDERS;
  render(<ActiveOrders setCurrentView={vi.fn()} setSelectedTable={vi.fn()} />);
  await screen.findByText(/Table W1 - Order #1/);
  await userEvent.click(screen.getByRole('button', { name: 'BOH VIEW' }));
  await screen.findByRole('button', { name: 'KITCHEN' });          // Station buttons have loaded
};

// The ticket card for a table, and the table names of all tickets in the order they are shown
const card = (tableNumber) => screen.getByText(new RegExp(`Table ${tableNumber} - Order`)).closest('.order-card');
const ticketOrder = () =>
  [...document.querySelectorAll('.order-card h3')].map(heading => heading.textContent.match(/Table (\w+)/)[1]);

test('BOH view lists tickets longest wait first and shows how long each has waited', async () => {
  await renderKitchen();

  expect(ticketOrder()).toEqual(['W3', 'W2', 'W1']);

  // Under 10 minutes is plain, 10 or more is a warning, 15 or more is late
  expect(within(card('W1')).getByText('4 MIN')).not.toHaveClass('warning');
  expect(within(card('W2')).getByText('12 MIN')).toHaveClass('warning');
  expect(within(card('W3')).getByText('20 MIN')).toHaveClass('late');
});

test('the kitchen\'s waiting time is measured on the backend\'s clock, not this tablet\'s', async () => {
  // The kitchen tablet thinks it is three hours earlier than the backend does (a wrong
  // clock, or a time zone that was never set). "Sent at 7:02" compared with the tablet's
  // own 4:14 used to read as 0 minutes: a ticket twelve minutes old looked brand new and
  // never turned yellow.
  backendClockAheadMs = 3 * 60 * 60 * 1000;
  const sentMinutesAgoOnTheBackend = (minutes) => asBackendTime(backendNow() - minutes * 60 * 1000);
  activeOrders = [
    { orderId: 2, tableId: 2, tableNumber: 'W2', isQuickOrder: false, serverName: 'Sarah', items: [
      { ...ticketItem(21, 'Garden Toast', 'fired', 1, 0), sentAt: sentMinutesAgoOnTheBackend(12) }
    ] },
    { orderId: 3, tableId: 3, tableNumber: 'W3', isQuickOrder: false, serverName: 'Sarah', items: [
      { ...ticketItem(32, 'Shrimp & Grits', 'pending', 1, 0), sentAt: sentMinutesAgoOnTheBackend(20) }
    ] }
  ];
  render(<ActiveOrders setCurrentView={vi.fn()} setSelectedTable={vi.fn()} />);
  await screen.findByText(/Table W2 - Order #2/);
  await userEvent.click(screen.getByRole('button', { name: 'BOH VIEW' }));
  await screen.findByRole('button', { name: 'KITCHEN' });

  expect(within(card('W2')).getByText('12 MIN')).toHaveClass('warning');
  expect(within(card('W3')).getByText('20 MIN')).toHaveClass('late');
});

test('a ticket lists its items in the order they were rung in, which is not always ID order', async () => {
  // The server tapped Soup, Steak, Pie. The three saves reached the backend as Steak,
  // Pie, Soup, so Soup got the highest ID. The backend sends the items in ring-in order
  // (ringInPosition); the screen used to re-sort them by ID and put the starter last.
  activeOrders = [
    { orderId: 5, tableId: 5, tableNumber: 'W5', isQuickOrder: false, serverName: 'Sarah', items: [
      { ...ticketItem(43, 'Soup', 'pending', 1, 2), ringInPosition: 0 },
      { ...ticketItem(41, 'Steak', 'pending', 1, 2), ringInPosition: 1 },
      { ...ticketItem(42, 'Pie', 'pending', 1, 2), ringInPosition: 2 }
    ] }
  ];
  render(<ActiveOrders setCurrentView={vi.fn()} setSelectedTable={vi.fn()} />);
  await screen.findByText(/Table W5 - Order #5/);

  const namesOnTheTicket = () =>
    [...card('W5').querySelectorAll('.order-item-ticket')].map(line => line.textContent.match(/Soup|Steak|Pie/)[0]);

  // Front of house and the kitchen read the same order
  expect(namesOnTheTicket()).toEqual(['Soup', 'Steak', 'Pie']);
  await userEvent.click(screen.getByRole('button', { name: 'BOH VIEW' }));
  await screen.findByRole('button', { name: 'KITCHEN' });
  expect(namesOnTheTicket()).toEqual(['Soup', 'Steak', 'Pie']);
});

test('FOH view shows no waiting times and no station buttons', async () => {
  activeOrders = KITCHEN_ORDERS;
  render(<ActiveOrders setCurrentView={vi.fn()} setSelectedTable={vi.fn()} />);
  await screen.findByText(/Table W1 - Order #1/);

  expect(screen.queryByText(/ MIN$/)).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'KITCHEN' })).not.toBeInTheDocument();
  expect(ticketOrder()).toEqual(['W1', 'W2', 'W3']);                // The order the backend sent them in
});

test('a station button shows that station only its own items', async () => {
  await renderKitchen();

  // ALL: the kitchen's Chicken Cutty and the bar's Mimosa are both on W1's ticket
  expect(within(card('W1')).getByText('Chicken Cutty')).toBeInTheDocument();
  expect(within(card('W1')).getByText('Mimosa')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: 'BAR' }));

  // The bar sees the Mimosa and nothing else: no food, and no tickets without drinks
  expect(screen.getByRole('button', { name: 'BAR' })).toHaveAttribute('aria-pressed', 'true');
  expect(within(card('W1')).getByText('Mimosa')).toBeInTheDocument();
  expect(screen.queryByText('Chicken Cutty')).not.toBeInTheDocument();
  expect(ticketOrder()).toEqual(['W1']);

  await userEvent.click(screen.getByRole('button', { name: 'KITCHEN' }));

  expect(screen.queryByText('Mimosa')).not.toBeInTheDocument();
  expect(ticketOrder()).toEqual(['W3', 'W2', 'W1']);
});

test('an item with no station is shown to every station', async () => {
  activeOrders = [{ orderId: 1, tableId: 1, tableNumber: 'W1', isQuickOrder: false, serverName: 'Sarah', items: [
    ticketItem(11, 'Daily Special', 'pending', null, 3)
  ] }];
  render(<ActiveOrders setCurrentView={vi.fn()} setSelectedTable={vi.fn()} />);
  await screen.findByText(/Table W1 - Order #1/);
  await userEvent.click(screen.getByRole('button', { name: 'BOH VIEW' }));

  await userEvent.click(await screen.findByRole('button', { name: 'BAR' }));
  expect(screen.getByText('Daily Special')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: 'KITCHEN' }));
  expect(screen.getByText('Daily Special')).toBeInTheDocument();
});

test('a ticket drops off the kitchen view once everything on it is completed, and SHOW DONE brings it back', async () => {
  activeOrders = [
    { orderId: 1, tableId: 1, tableNumber: 'W1', isQuickOrder: false, serverName: 'Sarah', items: [ticketItem(11, 'Chicken Cutty', 'completed', 1, 9)] },
    { orderId: 2, tableId: 2, tableNumber: 'W2', isQuickOrder: false, serverName: 'Sarah', items: [ticketItem(21, 'Garden Toast', 'pending', 1, 2)] }
  ];
  render(<ActiveOrders setCurrentView={vi.fn()} setSelectedTable={vi.fn()} />);
  await screen.findByText(/Table W1 - Order #1/);                   // Front of house still sees the finished table
  await userEvent.click(screen.getByRole('button', { name: 'BOH VIEW' }));

  expect(ticketOrder()).toEqual(['W2']);

  await userEvent.click(screen.getByRole('button', { name: 'SHOW DONE' }));

  // Finished tickets come back at the end, marked DONE, with no waiting time
  expect(ticketOrder()).toEqual(['W2', 'W1']);
  expect(card('W1')).toHaveClass('done');
  expect(within(card('W1')).getByText('DONE')).toBeInTheDocument();
  expect(within(card('W1')).queryByText(/ MIN$/)).not.toBeInTheDocument();
});

test('completing the last item on a ticket clears it from the kitchen view', async () => {
  activeOrders = [
    { orderId: 2, tableId: 2, tableNumber: 'W2', isQuickOrder: false, serverName: 'Sarah', items: [ticketItem(21, 'Garden Toast', 'fired', 1, 6)] }
  ];
  const realFetch = global.fetch;
  global.fetch = vi.fn(async (url, options = {}) => {
    if (options.method === 'PUT' && url.endsWith('/api/order-items/21/complete')) {
      return { ok: true, status: 200, json: async () => ({}) };
    }
    return realFetch(url, options);
  });
  render(<ActiveOrders setCurrentView={vi.fn()} setSelectedTable={vi.fn()} />);
  await screen.findByText(/Table W2 - Order #2/);
  await userEvent.click(screen.getByRole('button', { name: 'BOH VIEW' }));

  await userEvent.click(screen.getByRole('button', { name: 'COMPLETE' }));

  expect(await screen.findByText('ALL CAUGHT UP')).toBeInTheDocument();
  expect(screen.queryByText('Garden Toast')).not.toBeInTheDocument();
});

test('with no open orders at all, the kitchen view says so', async () => {
  activeOrders = [];
  render(<ActiveOrders setCurrentView={vi.fn()} setSelectedTable={vi.fn()} />);
  await screen.findByText('NO ACTIVE ORDERS');

  await userEvent.click(screen.getByRole('button', { name: 'BOH VIEW' }));

  expect(screen.getByText('NO ACTIVE ORDERS')).toBeInTheDocument();
});

// ----------------------------------------------------------------------------
// Who is offered START and COMPLETE
// ----------------------------------------------------------------------------
// The tests above draw the screen with nobody signed in, which is unrestricted.
// These draw it for a particular role, the way App.jsx provides it.

const renderKitchenViewAs = async (role) => {
  activeOrders = [
    { orderId: 1, tableId: 1, tableNumber: 'W1', isQuickOrder: false, serverName: 'Sarah', items: [
      ticketItem(11, 'Chicken Cutty', 'pending', 1, 5),
      ticketItem(12, 'Garden Toast', 'fired', 1, 5)
    ] }
  ];
  const view = render(
    <StaffContext.Provider value={{ staff: { staffId: 1, name: 'Test', role }, signOut: vi.fn() }}>
      <ActiveOrders setCurrentView={vi.fn()} setSelectedTable={vi.fn()} />
    </StaffContext.Provider>
  );
  await screen.findByText(/Table W1 - Order #1/);
  await userEvent.click(screen.getByRole('button', { name: 'BOH VIEW' }));
  return view;
};

test('the kitchen, the bar, and managers are offered START and COMPLETE', async () => {
  for (const role of ['kitchen', 'bartender', 'manager']) {
    const view = await renderKitchenViewAs(role);

    expect(screen.getByRole('button', { name: 'START' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'COMPLETE' })).toBeInTheDocument();

    view.unmount();                   // Take the screen down before drawing it for the next role
  }
});

test('a server can watch the kitchen view but is not offered START or COMPLETE', async () => {
  // The backend refuses a server who starts or completes an item (403), so the buttons
  // are not drawn. The tickets and their statuses still are.
  await renderKitchenViewAs('server');

  expect(screen.getByText('Chicken Cutty')).toBeInTheDocument();
  expect(screen.getByText('Garden Toast')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'START' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'COMPLETE' })).not.toBeInTheDocument();
});
