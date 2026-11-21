/**
 * Order total calculations - the single source of truth for money math
 * on the frontend.
 *
 * WHY: The tax rate used to be typed as 0.03 in the Create Order screen and
 *      again in the Payment modal. If one was changed and the other was not,
 *      the guest would see one total on the order and be charged another.
 */

// THE TAX RATE IS NOT IN THIS FILE. It used to be (TAX_RATE = 0.03), beside the
// backend's own copy, and the two had to be changed together. Now there is one:
// notta.tax-rate in backend/src/main/resources/application.properties. The backend
// works out every check that is charged with it, and hands it to the order screen
// (GET /api/settings) for the running total. The functions below take the rate as
// an argument, so nothing here can be out of step with what is charged.

/**
 * A tax rate as a display label: 0.03 -> "3%", 0.0825 -> "8.25%".
 * (toFixed(2) keeps two decimals; the + in front turns "3.00" back into 3.)
 *
 * @param {number} taxRate - The rate as a decimal
 * @returns {string} - e.g. "3%"
 */
export const formatTaxRate = (taxRate) => `${+(taxRate * 100).toFixed(2)}%`;

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
 * @param {number|null} taxRate - The rate the backend gave (0.03 = 3%), or null if it
 *                                has not arrived (or could not be loaded)
 * @returns {{subtotal: number, tax: number|null, total: number|null}} - Amounts in dollars.
 *          With no rate, tax and total are null: the caller shows them as blank. A total
 *          worked out from a guessed rate could differ from what the guest is charged.
 */
export const calculateOrderTotals = (items, taxRate) => {
  // Price x quantity for every line (quantity defaults to 1 if missing)
  const subtotal = roundToCents(
    items.reduce((sum, item) => sum + item.price * (item.quantity ?? 1), 0)
  );

  if (taxRate === null || taxRate === undefined) {
    return { subtotal, tax: null, total: null };
  }

  const tax = roundToCents(subtotal * taxRate);
  const total = roundToCents(subtotal + tax);

  return { subtotal, tax, total };
};
