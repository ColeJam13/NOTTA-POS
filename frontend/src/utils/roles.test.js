import { canDo, canOpenScreen, initialsOf } from './roles';

/**
 * Tests for the role rules: who may open which screen and do which restricted thing.
 */

test('managers may open every screen', () => {
  for (const view of ['home', 'floorMap', 'activeTables', 'quickOrders', 'activeOrders', 'createOrder', 'eightySix', 'financials', 'challenges', 'staff']) {
    expect(canOpenScreen('manager', view)).toBe(true);
  }
});

test('servers and bartenders may open the floor screens but not Financials or Staff', () => {
  for (const role of ['server', 'bartender']) {
    expect(canOpenScreen(role, 'createOrder')).toBe(true);
    expect(canOpenScreen(role, 'activeOrders')).toBe(true);
    expect(canOpenScreen(role, 'eightySix')).toBe(true);
    expect(canOpenScreen(role, 'financials')).toBe(false);
    expect(canOpenScreen(role, 'staff')).toBe(false);
  }
});

test('hosts may open the Floor Map and Tables only', () => {
  expect(canOpenScreen('host', 'floorMap')).toBe(true);
  expect(canOpenScreen('host', 'activeTables')).toBe(true);
  expect(canOpenScreen('host', 'createOrder')).toBe(false);
  expect(canOpenScreen('host', 'financials')).toBe(false);
});

test('the kitchen may open the kitchen display and the 86 board only', () => {
  expect(canOpenScreen('kitchen', 'activeOrders')).toBe(true);
  expect(canOpenScreen('kitchen', 'eightySix')).toBe(true);
  expect(canOpenScreen('kitchen', 'createOrder')).toBe(false);
  expect(canOpenScreen('kitchen', 'floorMap')).toBe(false);
});

test('everyone may open Home, and a role the app does not know may open nothing else', () => {
  expect(canOpenScreen('kitchen', 'home')).toBe(true);
  expect(canOpenScreen('janitor', 'home')).toBe(true);
  expect(canOpenScreen('janitor', 'floorMap')).toBe(false);
});

test('only managers may edit the floor layout', () => {
  expect(canDo('manager', 'edit-layout')).toBe(true);
  expect(canDo('server', 'edit-layout')).toBe(false);
  expect(canDo('host', 'edit-layout')).toBe(false);
});

test('the kitchen, the bar, and managers may change what is 86\'d', () => {
  expect(canDo('kitchen', 'change-86')).toBe(true);
  expect(canDo('bartender', 'change-86')).toBe(true);
  expect(canDo('manager', 'change-86')).toBe(true);
  expect(canDo('server', 'change-86')).toBe(false);
  expect(canDo('manager', 'something-that-does-not-exist')).toBe(false);
});

test('with nobody signed in (a screen drawn on its own in a test) nothing is restricted', () => {
  expect(canOpenScreen(null, 'financials')).toBe(true);
  expect(canDo(undefined, 'edit-layout')).toBe(true);
});

test('initialsOf makes the two letters for the nav bar badge', () => {
  expect(initialsOf('Sarah Miller')).toBe('SM');
  expect(initialsOf('Mary Jo Smith')).toBe('MS');
  expect(initialsOf('Morgan')).toBe('MO');
  expect(initialsOf('  kit  ')).toBe('KI');
  expect(initialsOf('')).toBe('?');
  expect(initialsOf(null)).toBe('?');
});
