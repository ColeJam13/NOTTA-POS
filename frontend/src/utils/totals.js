/**
 * Order total calculations - the single source of truth for money math
 * on the frontend.
 *
 * WHY: The tax rate used to be typed as 0.03 in the Create Order screen and
 *      again in the Payment modal. If one was changed and the other was not,
 *      the guest would see one total on the order and be charged another.
 */

// Sales tax rate as a decimal (0.03 = 3%)
export const TAX_RATE = 0.03;

// Tax rate as a display label, e.g. "3%"
export const TAX_RATE_LABEL = `${(TAX_RATE * 100).toFixed(TAX_RATE * 100 % 1 === 0 ? 0 : 2)}%`;

/**
 * Round a dollar amount to whole cents.
 * Floating point math can produce values like 10.299999; this fixes them
 * before they are added together or sent to the backend.
 */
const roundToCents = (amount) => Math.round(amount * 100) / 100;

/**
 * Calculate subtotal, tax, and total for a list of order items.
 *
 * @param {Array} items - Order items, each with a price and optional quantity
 * @returns {{subtotal: number, tax: number, total: number}} - Amounts in dollars
 */
export const calculateOrderTotals = (items) => {
  // Price x quantity for every line (quantity defaults to 1 if missing)
  const subtotal = roundToCents(
    items.reduce((sum, item) => sum + item.price * (item.quantity ?? 1), 0)
  );
  const tax = roundToCents(subtotal * TAX_RATE);
  const total = roundToCents(subtotal + tax);

  return { subtotal, tax, total };
};
