import { calculateOrderTotals, formatTaxRate, modifierUpcharge, priceWithModifiers } from './totals';

/**
 * Tests for the shared money math.
 *
 * WHY: An item's price on screen is the menu price plus its modifier
 *      upcharges. These pin that sum down, including the floating point
 *      cases that would otherwise show a guest $17.299999.
 */

test('modifierUpcharge adds up the chosen modifiers and treats free ones as zero', () => {
  expect(modifierUpcharge([])).toBe(0);
  expect(modifierUpcharge()).toBe(0);
  expect(modifierUpcharge([
    { name: 'French Toast', priceAdjustment: 2.0 },
    { name: 'Hashbrowns', priceAdjustment: 0 },
    { name: 'Add Chicken', priceAdjustment: 4.0 }
  ])).toBe(6);
  // A modifier with no priceAdjustment at all counts as free
  expect(modifierUpcharge([{ name: 'Plain' }, { name: 'Mystery', priceAdjustment: null }])).toBe(0);
});

test('priceWithModifiers is the menu price plus upcharges, rounded to whole cents', () => {
  expect(priceWithModifiers(15, [])).toBe(15);
  expect(priceWithModifiers(15, [{ priceAdjustment: 2.0 }, { priceAdjustment: 4.0 }])).toBe(21);
  // 0.1 + 0.2 is 0.30000000000000004 in floating point - it must come out as 0.3
  expect(priceWithModifiers(0.1, [{ priceAdjustment: 0.2 }])).toBe(0.3);
});

test('priceWithModifiers never goes below zero', () => {
  expect(priceWithModifiers(1, [{ priceAdjustment: -5 }])).toBe(0);
});

test('order totals include upcharges because they are already part of each item price', () => {
  // Combo Plate 15.00 + French Toast 2.00 = 17.00, plus Fries 6.00
  const comboPlate = { price: priceWithModifiers(15, [{ priceAdjustment: 2.0 }]), quantity: 1 };
  const fries = { price: 6.0, quantity: 1 };

  // 23.00 subtotal, 3% tax = 0.69, total = 23.69
  expect(calculateOrderTotals([comboPlate, fries], 0.03)).toEqual({ subtotal: 23, tax: 0.69, total: 23.69 });
});

test('tax that lands on exactly half a cent rounds up, the same as the backend', () => {
  // 16.50 x 3% = 0.495. JavaScript stores that as 0.49499999..., which would round DOWN to 0.49
  // while the backend rounds half a cent up to 0.50 - a one cent difference between the order
  // screen and the check.
  expect(calculateOrderTotals([{ price: 16.5, quantity: 1 }], 0.03)).toEqual({ subtotal: 16.5, tax: 0.5, total: 17 });
});

test('order totals multiply price by quantity and treat a missing quantity as one', () => {
  expect(calculateOrderTotals([{ price: 3.5, quantity: 3 }, { price: 6 }], 0.03).subtotal).toBe(16.5);
});

test('order totals use whatever tax rate they are given', () => {
  // The rate comes from the backend (GET /api/settings). Nothing in this file holds one.
  // 23.00 x 8.25% = 1.8975 -> 1.90
  expect(calculateOrderTotals([{ price: 23, quantity: 1 }], 0.0825)).toEqual({ subtotal: 23, tax: 1.9, total: 24.9 });
  // A rate of zero is a real rate (a tax-free item or place), not "no rate"
  expect(calculateOrderTotals([{ price: 23, quantity: 1 }], 0)).toEqual({ subtotal: 23, tax: 0, total: 23 });
});

test('with no tax rate there is a subtotal but no tax or total, rather than a guess', () => {
  // Before the rate has arrived, or if it could not be loaded. A total worked out from a
  // made-up rate could differ from what the guest is charged.
  expect(calculateOrderTotals([{ price: 23, quantity: 1 }], null)).toEqual({ subtotal: 23, tax: null, total: null });
  expect(calculateOrderTotals([{ price: 23, quantity: 1 }])).toEqual({ subtotal: 23, tax: null, total: null });
});

test('formatTaxRate shows a rate as a percentage with no trailing zeros', () => {
  expect(formatTaxRate(0.03)).toBe('3%');
  expect(formatTaxRate(0.0825)).toBe('8.25%');
  expect(formatTaxRate(0.1)).toBe('10%');
  expect(formatTaxRate(0)).toBe('0%');
});
