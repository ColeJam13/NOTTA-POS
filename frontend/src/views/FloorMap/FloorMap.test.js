import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import FloorMap from './FloorMap';
import { StaffContext } from '../../context/StaffContext';

/**
 * Behavior tests for the Floor Map and its edit mode.
 *
 * WHAT: Loads the map against a fake backend, drags and turns tables in edit
 *       mode, and checks what the screen shows and what is sent on Save.
 * WHY:  Edit mode changes where every table sits. It must save exactly what
 *       was moved, save nothing on Cancel, and never open a table's order
 *       while tables are being rearranged.
 *
 * The fake backend works the same way as in CreateOrder.test.js: global.fetch
 * is replaced with a function that returns canned JSON.
 *
 * Dragging is simulated with mouse events: press on a table, move over the
 * floor, release. The test browser does no layout, so the floor has no size
 * and sits at 0,0 - a mouse position is the same as a position on the floor.
 */

let tables;           // what GET /api/tables?quickOrders=false returns
let requests;         // [{ method, path, body }]
let refuseLayout;     // true = the fake backend answers 500 to saving the layout

beforeEach(() => {
  tables = [
    { tableId: 1, tableNumber: 'W1', seatCount: 2, status: 'available', shape: 'circle', rotation: 0, xposition: 50, yposition: 40 },
    { tableId: 2, tableNumber: 'B4', seatCount: 4, status: 'occupied', shape: 'square', rotation: 0, xposition: 300, yposition: 200 }
  ];
  requests = [];
  refuseLayout = false;

  global.fetch = jest.fn(async (url, options = {}) => {
    const method = options.method || 'GET';
    const path = url.replace('http://localhost:8080', '');
    const body = options.body ? JSON.parse(options.body) : null;
    requests.push({ method, path, body });
    const json = (data, status = 200) => ({ ok: status < 300, status, json: async () => data });

    if (method === 'GET' && path === '/api/tables?quickOrders=false') return json(tables);
    if (method === 'PUT' && path === '/api/tables/layout') {
      if (refuseLayout) return json({ message: 'Something went wrong on the server' }, 500);
      return json([]);
    }
    throw new Error(`Unexpected request in test: ${method} ${path}`);
  });
});

afterEach(() => {
  jest.restoreAllMocks();
});

const renderMap = async (props = {}) => {
  render(<FloorMap setCurrentView={jest.fn()} setSelectedTable={jest.fn()} {...props} />);
  await screen.findByText('W1');
};

const table = (number) => screen.getByTestId(`table-${number}`);
const floor = () => screen.getByTestId('floor-canvas');

// Press on a table at its top-left corner plus (grabX, grabY), drag to put that same
// point at (toX, toY) on the floor, and let go
const drag = (number, from, to) => {
  fireEvent.mouseDown(table(number), { clientX: from.x, clientY: from.y });
  fireEvent.mouseMove(floor(), { clientX: to.x, clientY: to.y });
  fireEvent.mouseUp(floor());
};

const layoutRequests = () => requests.filter(r => r.method === 'PUT' && r.path === '/api/tables/layout');

test('outside edit mode, tapping a table opens its order', async () => {
  const setCurrentView = jest.fn();
  const setSelectedTable = jest.fn();
  await renderMap({ setCurrentView, setSelectedTable });

  await userEvent.click(table('B4'));

  expect(setSelectedTable).toHaveBeenCalledWith(expect.objectContaining({ tableId: 2, tableNumber: 'B4' }));
  expect(setCurrentView).toHaveBeenCalledWith('createOrder');
});

test('tables are drawn where the backend says, turned by their rotation', async () => {
  tables[1].rotation = 45;
  await renderMap();

  expect(table('W1')).toHaveStyle({ left: '50px', top: '40px' });
  expect(table('B4')).toHaveStyle({ left: '300px', top: '200px', transform: 'rotate(45deg)' });
});

test('in edit mode, tapping a table selects it instead of opening its order', async () => {
  const setCurrentView = jest.fn();
  await renderMap({ setCurrentView });

  await userEvent.click(screen.getByRole('button', { name: 'EDIT LAYOUT' }));
  await userEvent.click(table('B4'));

  expect(setCurrentView).not.toHaveBeenCalled();
  expect(table('B4')).toHaveClass('selected');
  expect(screen.getByRole('button', { name: 'ROTATE' })).toBeEnabled();
});

test('dragging a table moves it, snapped to the grid, and Save sends only the tables that changed', async () => {
  await renderMap();
  await userEvent.click(screen.getByRole('button', { name: 'EDIT LAYOUT' }));
  expect(screen.getByRole('button', { name: 'SAVE LAYOUT' })).toBeDisabled();       // Nothing has moved yet

  // Grab W1 (at 50,40) ten pixels in from its corner and carry it to 207,163.
  // Its corner lands at 197,153, which snaps to 200,150.
  drag('W1', { x: 60, y: 50 }, { x: 207, y: 163 });

  expect(table('W1')).toHaveStyle({ left: '200px', top: '150px' });
  expect(table('B4')).toHaveStyle({ left: '300px', top: '200px' });                  // The other table did not move

  await userEvent.click(screen.getByRole('button', { name: 'SAVE LAYOUT' }));

  // Back out of edit mode, with the table in its new spot
  expect(await screen.findByRole('button', { name: 'EDIT LAYOUT' })).toBeInTheDocument();
  expect(table('W1')).toHaveStyle({ left: '200px', top: '150px' });
  expect(layoutRequests()).toHaveLength(1);
  expect(layoutRequests()[0].body).toEqual({ tables: [{ tableId: 1, x: 200, y: 150, rotation: 0 }] });
});

test('ROTATE turns the selected table 45 degrees at a time and wraps around at a full turn', async () => {
  tables[1].rotation = 315;
  await renderMap();
  await userEvent.click(screen.getByRole('button', { name: 'EDIT LAYOUT' }));
  expect(screen.getByRole('button', { name: 'ROTATE' })).toBeDisabled();             // No table is selected yet

  fireEvent.mouseDown(table('W1'), { clientX: 60, clientY: 50 });
  fireEvent.mouseUp(floor());
  await userEvent.click(screen.getByRole('button', { name: 'ROTATE' }));
  await userEvent.click(screen.getByRole('button', { name: 'ROTATE' }));
  expect(table('W1')).toHaveStyle({ transform: 'rotate(90deg)' });

  // B4 starts at 315 degrees: one more step is a full turn, back to 0
  fireEvent.mouseDown(table('B4'), { clientX: 310, clientY: 210 });
  fireEvent.mouseUp(floor());
  await userEvent.click(screen.getByRole('button', { name: 'ROTATE' }));

  await userEvent.click(screen.getByRole('button', { name: 'SAVE LAYOUT' }));
  await screen.findByRole('button', { name: 'EDIT LAYOUT' });
  expect(layoutRequests()[0].body).toEqual({
    tables: [
      { tableId: 1, x: 50, y: 40, rotation: 90 },
      { tableId: 2, x: 300, y: 200, rotation: 0 }
    ]
  });
});

test('a table cannot be dragged off the top or left edge of the floor', async () => {
  await renderMap();
  await userEvent.click(screen.getByRole('button', { name: 'EDIT LAYOUT' }));

  drag('W1', { x: 60, y: 50 }, { x: -200, y: -80 });

  expect(table('W1')).toHaveStyle({ left: '0px', top: '0px' });
});

test('CANCEL puts every table back and saves nothing', async () => {
  await renderMap();
  await userEvent.click(screen.getByRole('button', { name: 'EDIT LAYOUT' }));
  drag('W1', { x: 60, y: 50 }, { x: 207, y: 163 });
  expect(table('W1')).toHaveStyle({ left: '200px', top: '150px' });

  await userEvent.click(screen.getByRole('button', { name: 'CANCEL' }));

  expect(screen.getByRole('button', { name: 'EDIT LAYOUT' })).toBeInTheDocument();
  expect(table('W1')).toHaveStyle({ left: '50px', top: '40px' });
  expect(layoutRequests()).toHaveLength(0);
});

test('when the layout cannot be saved, the screen says so and keeps the changes', async () => {
  refuseLayout = true;
  await renderMap();
  await userEvent.click(screen.getByRole('button', { name: 'EDIT LAYOUT' }));
  drag('W1', { x: 60, y: 50 }, { x: 207, y: 163 });

  await userEvent.click(screen.getByRole('button', { name: 'SAVE LAYOUT' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('The layout could not be saved');
  // Still in edit mode, table still where it was dragged, so nothing has to be redone
  expect(screen.getByRole('button', { name: 'CANCEL' })).toBeInTheDocument();
  expect(table('W1')).toHaveStyle({ left: '200px', top: '150px' });
  await waitFor(() => expect(screen.getByRole('button', { name: 'SAVE LAYOUT' })).toBeEnabled());
});

// ============================================================================
// Who may edit the layout
// ============================================================================

// Draw the map as it looks to a signed-in person with the given role
const renderMapAs = async (role) => {
  render(
    <StaffContext.Provider value={{ staff: { staffId: 1, name: 'Test', role }, signOut: jest.fn() }}>
      <FloorMap setCurrentView={jest.fn()} setSelectedTable={jest.fn()} />
    </StaffContext.Provider>
  );
  await screen.findByText('W1');
};

test('a manager is offered EDIT LAYOUT', async () => {
  await renderMapAs('manager');

  expect(screen.getByRole('button', { name: 'EDIT LAYOUT' })).toBeInTheDocument();
});

test('a server sees the map but is not offered EDIT LAYOUT', async () => {
  await renderMapAs('server');

  expect(screen.getByTestId('table-W1')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'EDIT LAYOUT' })).not.toBeInTheDocument();
});
