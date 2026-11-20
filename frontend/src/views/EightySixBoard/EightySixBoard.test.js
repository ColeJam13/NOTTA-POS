import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import EightySixBoard from './EightySixBoard';
import { StaffContext } from '../../context/StaffContext';

/**
 * Behavior tests for the 86 board.
 *
 * WHAT: Loads the board against a fake backend, 86's an item, puts it back on,
 *       and checks what the board shows and which requests were sent.
 * WHY:  The board is how the kitchen tells the floor an item is out. If a tap
 *       here does not reach the backend, servers keep selling food that is gone.
 *
 * The fake backend works the same way as in CreateOrder.test.js: global.fetch
 * is replaced with a function that returns canned JSON.
 */

let menu;             // what GET /api/menu-items returns
let calls;            // e.g. ['GET /api/menu-items', 'PUT /api/menu-items/2/86']
let refuseChanges;    // true = the fake backend answers 500 to 86 / un-86

beforeEach(() => {
  menu = [
    { menuItemId: 1, name: 'Chicken Cutty', price: 17.0, category: 'Savory', is86d: false },
    { menuItemId: 2, name: 'Shrimp & Grits', price: 18.0, category: 'Savory', is86d: false },
    { menuItemId: 3, name: 'Latte', price: 5.0, category: 'Coffee', is86d: true }
  ];
  calls = [];
  refuseChanges = false;

  global.fetch = jest.fn(async (url, options = {}) => {
    const method = options.method || 'GET';
    const path = url.replace('http://localhost:8080', '');
    calls.push(`${method} ${path}`);
    const json = (data, status = 200) => ({ ok: status < 300, status, json: async () => data });

    if (method === 'GET' && path === '/api/menu-items') return json(menu);

    const change = path.match(/^\/api\/menu-items\/(\d+)\/(86|un-86)$/);
    if (method === 'PUT' && change) {
      if (refuseChanges) return json({ message: 'Something went wrong on the server' }, 500);
      // Like the real backend: flip the flag and answer with the updated item
      const item = menu.find(m => m.menuItemId === Number(change[1]));
      item.is86d = change[2] === '86';
      return json({ ...item });
    }
    throw new Error(`Unexpected request in test: ${method} ${path}`);
  });
});

afterEach(() => {
  jest.restoreAllMocks();
});

const renderBoard = async () => {
  render(<EightySixBoard setCurrentView={jest.fn()} setSelectedTable={jest.fn()} />);
  await screen.findByRole('button', { name: /Chicken Cutty/ });
};

// The "86'd now" box at the top, and one category block of the menu
const nowBox = () => screen.getByRole('region', { name: "86'd now" });
const category = (name) => screen.getByRole('region', { name });

test('the board lists what is 86\'d now and the whole menu by category', async () => {
  await renderBoard();

  // Latte is the only item that is out
  expect(within(nowBox()).getByText("86'D NOW (1)")).toBeInTheDocument();
  expect(within(nowBox()).getByRole('button', { name: 'Put Latte back on' })).toBeInTheDocument();

  // Every item is listed under its category, pressed = 86'd
  expect(within(category('Savory')).getByRole('button', { name: /Chicken Cutty/ })).toHaveAttribute('aria-pressed', 'false');
  expect(within(category('Savory')).getByRole('button', { name: /Shrimp & Grits/ })).toHaveAttribute('aria-pressed', 'false');
  expect(within(category('Coffee')).getByRole('button', { name: /Latte/ })).toHaveAttribute('aria-pressed', 'true');
});

test('tapping an available item 86\'s it', async () => {
  await renderBoard();

  await userEvent.click(within(category('Savory')).getByRole('button', { name: /Shrimp & Grits/ }));

  expect(await within(nowBox()).findByRole('button', { name: 'Put Shrimp & Grits back on' })).toBeInTheDocument();
  expect(within(nowBox()).getByText("86'D NOW (2)")).toBeInTheDocument();
  expect(within(category('Savory')).getByRole('button', { name: /Shrimp & Grits/ })).toHaveAttribute('aria-pressed', 'true');
  expect(calls).toContain('PUT /api/menu-items/2/86');
});

test('tapping an 86\'d item puts it back on, from the menu or from the list at the top', async () => {
  await renderBoard();

  await userEvent.click(within(nowBox()).getByRole('button', { name: 'Put Latte back on' }));

  expect(await within(nowBox()).findByText("Nothing is 86'd")).toBeInTheDocument();
  expect(within(category('Coffee')).getByRole('button', { name: /Latte/ })).toHaveAttribute('aria-pressed', 'false');
  expect(calls).toContain('PUT /api/menu-items/3/un-86');

  // And again from the menu grid: 86 it, then tap it once more
  await userEvent.click(within(category('Coffee')).getByRole('button', { name: /Latte/ }));
  await within(nowBox()).findByRole('button', { name: 'Put Latte back on' });
  await userEvent.click(within(category('Coffee')).getByRole('button', { name: /Latte/ }));
  await within(nowBox()).findByText("Nothing is 86'd");
  expect(calls.filter(c => c.startsWith('PUT'))).toEqual([
    'PUT /api/menu-items/3/un-86',
    'PUT /api/menu-items/3/86',
    'PUT /api/menu-items/3/un-86'
  ]);
});

test('when the backend refuses a change, the board says so and the item stays as it was', async () => {
  refuseChanges = true;
  await renderBoard();

  await userEvent.click(within(category('Savory')).getByRole('button', { name: /Chicken Cutty/ }));

  expect(await screen.findByRole('alert')).toHaveTextContent('Could not update Chicken Cutty. Try again.');
  await waitFor(() =>
    expect(within(category('Savory')).getByRole('button', { name: /Chicken Cutty/ })).toHaveAttribute('aria-pressed', 'false')
  );
  expect(within(nowBox()).getByText("86'D NOW (1)")).toBeInTheDocument();
});

// ============================================================================
// Who may change the board
// ============================================================================

// Draw the board as it looks to a signed-in person with the given role
const renderBoardAs = async (role) => {
  render(
    <StaffContext.Provider value={{ staff: { staffId: 1, name: 'Test', role }, signOut: jest.fn() }}>
      <EightySixBoard setCurrentView={jest.fn()} setSelectedTable={jest.fn()} />
    </StaffContext.Provider>
  );
  await screen.findByRole('button', { name: /Chicken Cutty/ });
};

test('a server can read the board but not change it', async () => {
  await renderBoardAs('server');

  // What is out is still shown...
  expect(within(nowBox()).getByText("86'D NOW (1)")).toBeInTheDocument();
  expect(screen.getByText("Only the kitchen, the bar, and managers can change what is 86'd.")).toBeInTheDocument();

  // ...but nothing on the board can be tapped
  expect(within(category('Savory')).getByRole('button', { name: /Chicken Cutty/ })).toBeDisabled();
  expect(within(nowBox()).getByRole('button', { name: 'Put Latte back on' })).toBeDisabled();
  await userEvent.click(within(category('Savory')).getByRole('button', { name: /Chicken Cutty/ }));
  expect(calls.filter(c => c.startsWith('PUT'))).toEqual([]);
});

test('kitchen staff can change the board', async () => {
  await renderBoardAs('kitchen');

  expect(screen.queryByText(/Only the kitchen, the bar, and managers/)).not.toBeInTheDocument();
  await userEvent.click(within(category('Savory')).getByRole('button', { name: /Chicken Cutty/ }));

  expect(await within(nowBox()).findByRole('button', { name: 'Put Chicken Cutty back on' })).toBeInTheDocument();
  expect(calls).toContain('PUT /api/menu-items/1/86');
});
