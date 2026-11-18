import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';

afterEach(() => {
  jest.restoreAllMocks();
});

// Smoke test: the app should boot to the Home screen and show its landing buttons.
// (Replaces the default Create React App test, which looked for "learn react"
// text that was removed when the Home view was built.)
test('renders the home screen with its landing buttons', () => {
  render(<App />);

  // The logo proves the Home view mounted
  expect(screen.getByAltText(/NOTTA-POS Logo/i)).toBeInTheDocument();

  // A couple of landing buttons prove navigation options rendered
  expect(screen.getByRole('button', { name: /FLOOR MAP/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /CREATE ORDER/i })).toBeInTheDocument();
});

// Walks Floor Map -> table W1 -> CREATE ORDER in the nav bar, against a fake backend
// (global.fetch replaced with a function that returns canned JSON, as in CreateOrder.test.js).
test('tapping CREATE ORDER while a table is on screen starts a fresh order, not more items on that table', async () => {
  const json = (data) => ({ ok: true, status: 200, json: async () => data });
  global.fetch = jest.fn(async (url) => {
    const path = url.replace('http://localhost:8080', '');
    if (path === '/api/tables?quickOrders=false') {
      return json([{ tableId: 1, tableNumber: 'W1', seatCount: 2, status: 'occupied', shape: 'circle', xposition: 50, yposition: 40 }]);
    }
    if (path === '/api/menu-items') return json([{ menuItemId: 1, name: 'Chicken Cutty', price: 17.0, category: 'Savory' }]);
    if (path === '/api/menu-items/modifier-groups') return json([]);
    if (path === '/api/orders?tableId=1') return json([{ orderId: 77, tableId: 1, status: 'open' }]);
    if (path === '/api/order-items/order/77') {
      return json([{ orderItemId: 50, menuItemId: 1, price: 17.0, quantity: 1, status: 'fired' }]);
    }
    if (path === '/api/order-item-modifiers/order/77') return json([]);
    throw new Error(`Unexpected request in test: ${path}`);
  });

  const { container } = render(<App />);
  await userEvent.click(screen.getByRole('button', { name: 'FLOOR MAP' }));
  await userEvent.click(await screen.findByText('W1'));

  // Table W1's order is on screen, with the item it already has
  expect(await screen.findByText('Current Order - Table W1')).toBeInTheDocument();
  const list = () => container.querySelector('.order-items-list');
  expect(await within(list()).findByText('Chicken Cutty')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: 'CREATE ORDER' }));

  // A new order: W1's item is gone from the list and nothing links this screen to W1's check.
  // (This used to keep W1's items and order under a "New Quick Order" heading.)
  expect(await screen.findByText('Current Order - New Quick Order')).toBeInTheDocument();
  expect(within(list()).queryByText('Chicken Cutty')).not.toBeInTheDocument();
  expect(screen.getByText('Add items to order')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'CLOSE ORDER' })).toBeDisabled();
});
