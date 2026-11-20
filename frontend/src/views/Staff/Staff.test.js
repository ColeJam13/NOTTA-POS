import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Staff from './Staff';

/**
 * Behavior tests for the Staff screen (managers only).
 *
 * WHAT: Loads the screen against a fake backend, adds and edits staff, and
 *       checks what the screen shows and exactly what is sent.
 * WHY:  This screen decides who can sign in and what they can do. It must
 *       send only what the manager changed, never send a PIN by accident,
 *       and show the backend's reason when a change is refused.
 *
 * The fake backend works the same way as in CreateOrder.test.js: global.fetch
 * is replaced with a function that returns canned JSON.
 */

let staff;            // what GET /api/staff returns
let requests;         // [{ method, path, body }]
let refusal;          // a message = the fake backend answers 409 with it to the next change

beforeEach(() => {
  staff = [
    { staffId: 2, name: 'Morgan', role: 'manager', isActive: true },
    { staffId: 1, name: 'Sarah', role: 'server', isActive: true },
    { staffId: 3, name: 'Jamie', role: 'host', isActive: false }
  ];
  requests = [];
  refusal = null;

  global.fetch = jest.fn(async (url, options = {}) => {
    const method = options.method || 'GET';
    const path = url.replace('http://localhost:8080', '');
    const body = options.body ? JSON.parse(options.body) : null;
    requests.push({ method, path, body });
    const json = (data, status = 200) => ({ ok: status < 300, status, json: async () => data });

    if (method === 'GET' && path === '/api/staff') return json(staff);

    if (refusal && method !== 'GET') return json({ message: refusal }, 409);

    if (method === 'POST' && path === '/api/staff') {
      const created = { staffId: 10, name: body.name, role: body.role, isActive: true };
      staff = [...staff, created];
      return json(created, 201);
    }
    const edit = path.match(/^\/api\/staff\/(\d+)$/);
    if (method === 'PUT' && edit) {
      const { pin, ...visibleChanges } = body;                      // Like the real backend, the PIN never comes back
      staff = staff.map(person => (person.staffId === Number(edit[1]) ? { ...person, ...visibleChanges } : person));
      return json(staff.find(person => person.staffId === Number(edit[1])));
    }
    throw new Error(`Unexpected request in test: ${method} ${path}`);
  });
});

afterEach(() => {
  jest.restoreAllMocks();
});

const renderScreen = async () => {
  render(<Staff setCurrentView={jest.fn()} setSelectedTable={jest.fn()} />);
  await screen.findByText('Morgan');
};

const row = (staffId) => screen.getByTestId(`staff-${staffId}`);
const addForm = () => screen.getByRole('form', { name: 'Add staff' });
const changes = () => requests.filter(r => r.method !== 'GET');

test('the screen lists everyone with their role and whether they can sign in', async () => {
  await renderScreen();

  expect(within(row(2)).getByText('Manager')).toBeInTheDocument();
  expect(within(row(2)).getByText('ACTIVE')).toBeInTheDocument();
  expect(within(row(1)).getByText('Server')).toBeInTheDocument();

  // Someone who has left stays on the list, marked inactive
  expect(within(row(3)).getByText('INACTIVE')).toBeInTheDocument();
  expect(row(3)).toHaveClass('inactive');
});

test('adding a person sends their name, role, and PIN, and they appear on the list', async () => {
  await renderScreen();
  const form = addForm();

  // ADD stays off until there is a name and a full 4-digit PIN
  expect(within(form).getByRole('button', { name: 'ADD' })).toBeDisabled();
  await userEvent.type(within(form).getByLabelText('Name'), 'Alex');
  await userEvent.selectOptions(within(form).getByLabelText('Role'), 'bartender');
  await userEvent.type(within(form).getByLabelText('PIN (4 digits)'), '333');
  expect(within(form).getByRole('button', { name: 'ADD' })).toBeDisabled();
  await userEvent.type(within(form).getByLabelText('PIN (4 digits)'), '3');

  await userEvent.click(within(form).getByRole('button', { name: 'ADD' }));

  expect(await screen.findByText('Alex')).toBeInTheDocument();
  expect(changes()).toEqual([{ method: 'POST', path: '/api/staff', body: { name: 'Alex', role: 'bartender', pin: '3333' } }]);
  // The form is empty again, ready for the next person
  expect(within(form).getByLabelText('Name')).toHaveValue('');
  expect(within(form).getByLabelText('PIN (4 digits)')).toHaveValue('');
});

test('the PIN box takes digits only, four at most, and hides them', async () => {
  await renderScreen();
  const pinBox = within(addForm()).getByLabelText('PIN (4 digits)');

  await userEvent.type(pinBox, '12ab3456');

  expect(pinBox).toHaveValue('1234');
  expect(pinBox).toHaveAttribute('type', 'password');
});

test('when the backend refuses a new person, the screen shows why and keeps what was typed', async () => {
  refusal = 'That PIN is already in use. Pick a different one.';
  await renderScreen();
  const form = addForm();
  await userEvent.type(within(form).getByLabelText('Name'), 'Alex');
  await userEvent.type(within(form).getByLabelText('PIN (4 digits)'), '1111');

  await userEvent.click(within(form).getByRole('button', { name: 'ADD' }));

  expect(await within(form).findByRole('alert')).toHaveTextContent('That PIN is already in use. Pick a different one.');
  expect(within(form).getByLabelText('Name')).toHaveValue('Alex');
  expect(screen.queryByTestId('staff-10')).not.toBeInTheDocument();
});

test('changing a role sends no PIN, so the person keeps the one they have', async () => {
  await renderScreen();

  await userEvent.click(screen.getByRole('button', { name: 'Edit Sarah' }));
  const editor = screen.getByRole('form', { name: 'Edit Sarah' });
  await userEvent.selectOptions(within(editor).getByLabelText('Role'), 'bartender');
  await userEvent.click(within(editor).getByRole('button', { name: 'SAVE' }));

  expect(await within(row(1)).findByText('Bartender')).toBeInTheDocument();
  expect(changes()).toEqual([
    { method: 'PUT', path: '/api/staff/1', body: { name: 'Sarah', role: 'bartender', isActive: true } }
  ]);
  expect(changes()[0].body).not.toHaveProperty('pin');
});

test('typing a new PIN sends it, and a half-typed PIN cannot be saved', async () => {
  await renderScreen();
  await userEvent.click(screen.getByRole('button', { name: 'Edit Sarah' }));
  const editor = screen.getByRole('form', { name: 'Edit Sarah' });

  await userEvent.type(within(editor).getByLabelText(/New PIN/), '44');
  expect(within(editor).getByRole('button', { name: 'SAVE' })).toBeDisabled();

  await userEvent.type(within(editor).getByLabelText(/New PIN/), '44');
  await userEvent.click(within(editor).getByRole('button', { name: 'SAVE' }));

  await waitFor(() => expect(changes()).toHaveLength(1));
  expect(changes()[0].body).toEqual({ name: 'Sarah', role: 'server', isActive: true, pin: '4444' });
});

test('unticking "Can sign in" deactivates the person, who stays on the list', async () => {
  await renderScreen();
  await userEvent.click(screen.getByRole('button', { name: 'Edit Sarah' }));
  const editor = screen.getByRole('form', { name: 'Edit Sarah' });

  await userEvent.click(within(editor).getByLabelText('Can sign in'));
  await userEvent.click(within(editor).getByRole('button', { name: 'SAVE' }));

  expect(await within(row(1)).findByText('INACTIVE')).toBeInTheDocument();
  expect(changes()[0].body).toEqual({ name: 'Sarah', role: 'server', isActive: false });
});

test('when the backend refuses a change, the editor stays open with the reason', async () => {
  refusal = 'Morgan is the only manager. Make someone else a manager first.';
  await renderScreen();
  await userEvent.click(screen.getByRole('button', { name: 'Edit Morgan' }));
  const editor = screen.getByRole('form', { name: 'Edit Morgan' });

  await userEvent.click(within(editor).getByLabelText('Can sign in'));
  await userEvent.click(within(editor).getByRole('button', { name: 'SAVE' }));

  expect(await within(editor).findByRole('alert')).toHaveTextContent('Morgan is the only manager. Make someone else a manager first.');
  expect(screen.getByRole('form', { name: 'Edit Morgan' })).toBeInTheDocument();
});

test('CANCEL closes the editor and sends nothing', async () => {
  await renderScreen();
  await userEvent.click(screen.getByRole('button', { name: 'Edit Sarah' }));
  const editor = screen.getByRole('form', { name: 'Edit Sarah' });
  await userEvent.selectOptions(within(editor).getByLabelText('Role'), 'manager');

  await userEvent.click(within(editor).getByRole('button', { name: 'CANCEL' }));

  expect(screen.queryByRole('form', { name: 'Edit Sarah' })).not.toBeInTheDocument();
  expect(within(row(1)).getByText('Server')).toBeInTheDocument();
  expect(changes()).toEqual([]);
});
