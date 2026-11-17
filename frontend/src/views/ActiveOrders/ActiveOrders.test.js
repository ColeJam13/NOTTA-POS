import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ActiveOrders from './ActiveOrders';

/**
 * Behavior tests for the Active Orders screen (the kitchen ticket).
 *
 * WHAT: Loads the screen against a fake backend and checks that each item's
 *       modifiers and kitchen note are printed under it, in both the FOH and
 *       BOH views.
 * WHY:  "Combo Plate" alone is not enough to cook it. If a modifier or an
 *       allergy note is missing from the ticket, the wrong food goes out.
 *
 * The fake backend works the same way as in CreateOrder.test.js: global.fetch
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

beforeEach(() => {
  global.fetch = jest.fn(async (url) => {
    const path = url.replace('http://localhost:8080', '');
    if (path === '/api/orders/active') {
      return { ok: true, status: 200, json: async () => ACTIVE_ORDERS };
    }
    throw new Error(`Unexpected request in test: ${path}`);
  });
});

afterEach(() => {
  jest.restoreAllMocks();
});

// Render the screen and wait for the order card to load
const renderScreen = async () => {
  const view = render(<ActiveOrders setCurrentView={jest.fn()} setSelectedTable={jest.fn()} />);
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
