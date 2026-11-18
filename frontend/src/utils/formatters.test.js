import { formatUpcharge } from './formatters';

test('formatUpcharge shows upcharges with a plus, discounts with a minus, and nothing for free modifiers', () => {
  expect(formatUpcharge(2)).toBe('+$2.00');
  expect(formatUpcharge(0.5)).toBe('+$0.50');
  expect(formatUpcharge(-1)).toBe('-$1.00');
  expect(formatUpcharge(0)).toBe('');
  expect(formatUpcharge(null)).toBe('');
  expect(formatUpcharge(undefined)).toBe('');
});
