/**
 * Order total calculations - the single source of truth for money math
 * on the frontend.
 *
 * WHY: The tax rate used to be typed as 0.03 in the Create Order screen and
 *      again in the Payment modal. If one was changed and the other was not,
 *      the guest would see one total on the order and be charged another.
 */

// Sales tax rate as a decimal (0.03 = 3%).
// The backend works out every check that is actually charged, using its own copy of
// this number: notta.tax-rate in backend/src/main/resources/application.properties.
// This copy is for the running total on the order screen. Change both together.
export const TAX_RATE = 0.03;

// Tax rate as a display label, e.g. "3%"
export const TAX_RATE_LABEL = `${(TAX_RATE * 100).toFixed(TAX_RATE * 100 % 1 === 0 ? 0 : 2)}%`;

/**
 * Round a dollar amount to whole cents.
 * Floating point math can produce values like 10.299999; this fixes them
 * before they are added together or sent to the backend.
 *
 * Number.EPSILON is the smallest difference JavaScript can tell apart near 1.
 * Adding it nudges a value that should sit exactly on half a cent (0.495 is
 * really stored as 0.49499999...) over the line, so it rounds up to 0.50 -
 * the same answer the backend's HALF_UP rounding gives.
 */
const roundToCents = (amount) => Math.round((amount + Number.EPSILON) * 100) / 100;

/**
 * Add up the upcharges of the modifiers chosen for one item.
 *
 * @param {Array} modifiers - Chosen modifiers, each with a priceAdjustment (0 when free)
 * @returns {number} - Total upcharge in dollars
 */
export const modifierUpcharge = (modifiers = []) =>
  roundToCents(
    modifiers.reduce((sum, modifier) => sum + (modifier.priceAdjustment ?? 0), 0)
  );

/**
 * Price of ONE item once its modifier upcharges are added.
 * Example: Combo Plate $15.00 + French Toast $2.00 = $17.00.
 *
 * The backend does this same sum when it saves the item (see
 * OrderItemModifierService) - its answer is the one that gets charged. This
 * copy is for showing the price on screen before the item is saved.
 *
 * An order item's "price" already includes its upcharges, so
 * calculateOrderTotals below needs no changes to account for modifiers.
 *
 * @param {number} basePrice - The menu price
 * @param {Array} modifiers - Chosen modifiers, each with a priceAdjustment
 * @returns {number} - Price in dollars, never below zero
 */
export const priceWithModifiers = (basePrice, modifiers = []) =>
  Math.max(0, roundToCents(basePrice + modifierUpcharge(modifiers)));

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
