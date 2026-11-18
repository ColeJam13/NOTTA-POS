import { formatTimeAgo, formatUpcharge } from './formatters';

test('formatUpcharge shows upcharges with a plus, discounts with a minus, and nothing for free modifiers', () => {
  expect(formatUpcharge(2)).toBe('+$2.00');
  expect(formatUpcharge(0.5)).toBe('+$0.50');
  expect(formatUpcharge(-1)).toBe('-$1.00');
  expect(formatUpcharge(0)).toBe('');
  expect(formatUpcharge(null)).toBe('');
  expect(formatUpcharge(undefined)).toBe('');
});

test('formatTimeAgo counts minutes, then hours and minutes, then days', () => {
  const minutesAgo = (minutes) => new Date(Date.now() - minutes * 60 * 1000).toISOString();

  expect(formatTimeAgo(minutesAgo(5))).toBe('5m ago');
  expect(formatTimeAgo(minutesAgo(135))).toBe('2h 15m ago');
  expect(formatTimeAgo(minutesAgo(3 * 24 * 60))).toBe('3d ago');
});

test('formatTimeAgo never shows a negative time when this device is a little behind the server', () => {
  // The server stamped the order 20 seconds "in the future" by this device's clock
  const slightlyAhead = new Date(Date.now() + 20 * 1000).toISOString();
  expect(formatTimeAgo(slightlyAhead)).toBe('0m ago');
});
