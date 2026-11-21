import { apiFetch, getJson, ORDER_NOT_OPEN } from '../../services/api';

/**
 * Backend calls used by the Create Order screen.
 *
 * WHAT: Plain async functions - no React state in here.
 * WHY:  These requests used to live inside the button click handlers of
 *       CreateOrder.js (the "open an order" block existed twice, once for
 *       Send and once for Save Draft). Pulling them out leaves the component
 *       responsible for what the screen shows, and this file responsible for
 *       talking to the server.
 */

// Helper function to create a Quick Order table
export const createQuickOrderTable = async () => {
  try {
    console.log('Creating Quick Order table...');

    // 1. Get all existing tables to find highest QO number
    const tablesResponse = await apiFetch('/api/tables');
    const allTables = await tablesResponse.json();

    // 2. Filter for Quick Order tables (starting with "QO")
    const quickOrders = allTables.filter(t => t.tableNumber && t.tableNumber.startsWith('QO'));

    // 3. Find the highest number and increment
    let nextQONumber = 1;
    if (quickOrders.length > 0) {
      const qoNumbers = quickOrders.map(t => {
        const num = parseInt(t.tableNumber.substring(2)); // Remove "QO" prefix
        return isNaN(num) ? 0 : num;
      });
      nextQONumber = Math.max(...qoNumbers) + 1;
    }

    const newTableNumber = `QO${nextQONumber}`;
    console.log('Creating Quick Order:', newTableNumber);

    // 4. Create the new Quick Order table
    const createResponse = await apiFetch('/api/tables', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        tableNumber: newTableNumber,
        section: 'Quick Orders',
        seatCount: 0,
        status: 'available',
        isQuickOrder: true
      })
    });

    if (!createResponse.ok) {
      throw new Error('Failed to create Quick Order table');
    }

    const newTable = await createResponse.json();
    console.log('Successfully created Quick Order table:', newTable);
    return newTable;
  } catch (error) {
    console.error('Error creating Quick Order:', error);
    throw error;
  }
};

/**
 * Open a new order on a table. The backend marks the table as occupied in the
 * same step (this used to be a second request from here; if it failed, the
 * Floor Map showed a table with an open check as free).
 *
 * If the table already has an open order - another server opened it a moment
 * ago - the backend answers with that order instead of starting a second check.
 *
 * @param {Object} table - The table the order belongs to
 * @returns {Promise<number>} - The order's ID
 * @throws {Error} - If the backend refuses to open the order
 */
export const openOrderForTable = async (table) => {
  const orderResponse = await apiFetch('/api/orders', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      tableId: table.tableId,
      orderType: 'dine_in',
      status: 'open',
      serverName: table.serverName || 'Unknown'
    })
  });
  if (!orderResponse.ok) {
    throw new Error(`Opening an order on table ${table.tableId} failed with status ${orderResponse.status}`);
  }
  const order = await orderResponse.json();
  console.log('Order created', order);

  return order.orderId;
};

/**
 * Load the modifier groups for the whole menu, in one request.
 *
 * @returns {Promise<Object>} - A lookup from menuItemId to that item's groups
 *          (each group already contains its choices). Items with no modifiers
 *          are not in the lookup, so "no entry" means "add it straight away".
 */
export const loadModifierGroupsByMenuItem = async () => {
  const entries = await getJson('/api/menu-items/modifier-groups');

  const groupsByMenuItemId = {};
  entries.forEach(entry => {
    groupsByMenuItemId[entry.menuItemId] = entry.groups;
  });
  return groupsByMenuItemId;
};

// The code the backend puts on its answer when an order is no longer open
// (the check was closed, usually on another terminal). Screens check for this
// code, never for the wording of the message.
// It is defined in services/api.js and passed on from here, so the order screen
// can keep importing everything it needs from this one file.
export { ORDER_NOT_OPEN };

/**
 * Turn a refused response into an Error that says why.
 *
 * Every error from the backend has the same body: a "message" written for the
 * person at the screen, and for a few situations a "code" the screen can act on.
 *
 * @param {Response} response - A response whose status is not 2xx
 * @param {string} fallbackMessage - What to say if the body cannot be read
 * @returns {Promise<Error>} - With .message (the backend's wording), .status, and .code (or null)
 */
const refusalFrom = async (response, fallbackMessage) => {
  const body = await response.json().catch(() => null);
  const error = new Error(body?.message || fallbackMessage);
  error.status = response.status;
  error.code = body?.code || null;
  return error;
};

/**
 * Save one item to an order as a draft (not sent to the kitchen yet).
 *
 * Every item goes through the same request, with or without modifiers. The
 * request carries no price: the backend works it out from the menu and the
 * chosen modifiers, and checks the order is still open and the item can be
 * ordered. (Plain items used to go to a second endpoint that saved the price
 * sent from here.)
 *
 * @param {number} orderId - Order to add the item to
 * @param {Object} item - Item with menuItemId and quantity; optionally
 *                        modifiers (array) and specialInstructions (kitchen note)
 * @returns {Promise<Object>} - The saved order item, including its new orderItemId and its price
 * @throws {Error} - If the backend refuses the item (so a failed save is never mistaken
 *                   for a saved one). The error carries the backend's message and code.
 */
export const createDraftItem = async (orderId, item) => {
  const modifiers = item.modifiers || [];

  const response = await apiFetch('/api/order-items', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      orderId: orderId,
      menuItemId: item.menuItemId,
      quantity: item.quantity,
      specialInstructions: item.specialInstructions || '',
      modifierIds: modifiers.map(modifier => modifier.modifierId)   // No price: the backend adds up menu price + upcharges
    })
  });

  if (!response.ok) {
    throw await refusalFrom(response, `Saving item ${item.menuItemId} failed with status ${response.status}`);
  }
  return response.json();
};

/**
 * Replace the modifiers and kitchen note on an item that is already saved.
 *
 * @param {number} orderItemId - The saved item to change
 * @param {Object} selection - { modifiers: Array, specialInstructions: string }
 * @returns {Promise<{locked: boolean, closed: boolean, item: Object|null}>}
 *          locked: the item already went to the prep station and can no longer be changed.
 *          closed: the whole check was closed (on another terminal) - nothing on it can change.
 *          Otherwise item is the updated order item (with its new price).
 */
export const updateItemModifiers = async (orderItemId, selection) => {
  const response = await apiFetch(`/api/order-items/${orderItemId}/modifiers`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      specialInstructions: selection.specialInstructions,
      modifierIds: selection.modifiers.map(modifier => modifier.modifierId)
    })
  });

  // 409 Conflict = this change can no longer be made. The code says which of the two reasons.
  if (response.status === 409) {
    const refusal = await refusalFrom(response, 'This item can no longer be changed.');
    if (refusal.code === ORDER_NOT_OPEN) return { locked: false, closed: true, item: null };
    return { locked: true, closed: false, item: null };     // The edit window closed before this change arrived
  }

  if (!response.ok) {
    throw new Error(`Updating item ${orderItemId} failed with status ${response.status}`);
  }
  return { locked: false, closed: false, item: await response.json() };
};

/**
 * Save every draft item that is not in the database yet.
 *
 * All saves run in parallel and we wait for ALL of them to finish, so a Send
 * right afterwards never misses an item that was still being saved.
 *
 * @param {number} orderId - Order the items belong to
 * @param {Array} items - The full on-screen item list
 * @returns {Promise<{newIds: Array, failure: Error|null}>}
 *          newIds: one entry per item, in the same order: the new orderItemId
 *                  for items that were just saved, or null for items that did
 *                  not need saving. (Matching by position, not by menu item,
 *                  keeps two of the same dish from sharing one ID.) An item
 *                  whose save FAILED also gets null, so the caller can spot
 *                  it: it needed saving but has no ID.
 *          failure: why the first refused item was refused (the backend's
 *                   message and code), or null if every save worked.
 */
export const saveNewDraftItems = async (orderId, items) => {
  let failure = null;

  const newIds = await Promise.all(
    items.map(async (item) => {
      const needsSaving = item.status === 'draft' && !item.orderItemId;
      if (!needsSaving) return null;

      try {
        const created = await createDraftItem(orderId, item);
        return created.orderItemId;
      } catch (error) {
        // Do not let one failed item hide the IDs of the items that did save
        console.error('Error saving draft item:', error);
        if (!failure) failure = error;
        return null;
      }
    })
  );

  return { newIds, failure };
};

// Send ALL draft items for this order (backend starts the edit timer and answers with the sent items).
// Throws if the backend refuses - for example because the check was closed on another terminal.
export const sendDraftItems = async (orderId) => {
  const sendResponse = await apiFetch(`/api/order-items/order/${orderId}/send`, {
    method: 'POST'
  });
  if (!sendResponse.ok) {
    throw await refusalFrom(sendResponse, `Sending order ${orderId} failed with status ${sendResponse.status}`);
  }
  return sendResponse.json();
};

// Send everything on the order to the prep stations right now (bypass the timer).
// Throws if the backend refuses, the same as sendDraftItems.
export const sendOrderNow = async (orderId) => {
  const response = await apiFetch(`/api/order-items/order/${orderId}/send-now`, {
    method: 'POST'
  });
  if (!response.ok) {
    throw await refusalFrom(response, `Sending order ${orderId} failed with status ${response.status}`);
  }
  return response;
};

/**
 * Delete a saved order item.
 *
 * @param {number} orderItemId - The saved item to remove
 * @returns {Promise<{deleted: boolean, locked: boolean, closed: boolean}>} - deleted is true
 *          only if the backend confirmed the delete. locked is true when the backend refused
 *          because the item already went to the prep station (409 Conflict). closed is true
 *          when it refused because the whole check was closed, usually on another terminal.
 */
export const deleteOrderItem = async (orderItemId) => {
  const deleteResponse = await apiFetch(`/api/order-items/${orderItemId}`, {
    method: 'DELETE'
  });
  console.log('Delete response status:', deleteResponse.status);

  // Both refusals are a 409. The code in the body tells them apart.
  if (deleteResponse.status === 409) {
    const refusal = await refusalFrom(deleteResponse, 'The item could not be removed');
    const closed = refusal.code === ORDER_NOT_OPEN;
    return { deleted: false, locked: !closed, closed };
  }
  return { deleted: deleteResponse.ok, locked: false, closed: false };
};

/**
 * Clear an empty order off its table, so the table is free again.
 * Used when the guests leave before ordering, or every item was removed:
 * there is nothing to pay, so there is no payment to close the order.
 *
 * @param {number} orderId - The open order to clear (must have no items)
 * @returns {Promise<{cleared: boolean, message: string}>} - message is the backend's
 *          reason when it refuses (for example, the order still has items on it)
 */
export const clearEmptyOrder = async (orderId) => {
  const response = await apiFetch(`/api/orders/${orderId}`, { method: 'DELETE' });
  if (response.ok) return { cleared: true, message: '' };

  // Error responses from the backend always carry a "message" written for the person at the screen
  const error = await response.json().catch(() => null);
  return { cleared: false, message: error?.message || 'The table could not be cleared.' };
};

/**
 * Load every item from ALL open orders on a table, with menu item names,
 * modifiers, and kitchen notes attached.
 *
 * @param {number} tableId - Table to look up
 * @returns {Promise<{orderId: number, items: Array}|null>} - The order to keep adding
 *          to plus its items, or null if the table has no open order
 */
export const loadOpenOrderForTable = async (tableId) => {
  const ordersResponse = await apiFetch(`/api/orders?tableId=${tableId}`);
  const orders = await ordersResponse.json();

  const openOrders = orders.filter(o => o.status === 'open');
  if (openOrders.length === 0) return null;

  // Fetch menu items first (needed to turn menuItemId into a name)
  const menuResponse = await apiFetch('/api/menu-items');
  const menuData = await menuResponse.json();

  // Fetch items from ALL open orders and wait for all item fetches to complete
  const allItemArrays = await Promise.all(
    openOrders.map(order =>
      apiFetch(`/api/order-items/order/${order.orderId}`)
        .then(response => response.json())
    )
  );

  // Fetch the modifiers chosen for those items (one request per open order, names included)
  const allModifierArrays = await Promise.all(
    openOrders.map(order => getJson(`/api/order-item-modifiers/order/${order.orderId}`))
  );

  // Group the modifiers by the order item they belong to
  const modifiersByOrderItemId = {};
  allModifierArrays.flat().forEach(modifier => {
    if (!modifiersByOrderItemId[modifier.orderItemId]) {
      modifiersByOrderItemId[modifier.orderItemId] = [];
    }
    modifiersByOrderItemId[modifier.orderItemId].push(modifier);
  });

  // Flatten all items into one array
  const allItems = allItemArrays.flat();
  const formattedItems = allItems.map(item => ({
    orderItemId: item.orderItemId,
    menuItemId: item.menuItemId,
    name: menuData.find(m => m.menuItemId === item.menuItemId)?.name,
    price: item.price,                                                  // Already includes any modifier upcharges
    quantity: item.quantity,
    status: item.status,
    delayExpiresAt: item.delayExpiresAt,                                // When this item's edit window closes (only meaningful while status is 'limbo')
    modifiers: modifiersByOrderItemId[item.orderItemId] || [],
    specialInstructions: item.specialInstructions || ''
  }));

  console.log('Loaded all items from all orders:', formattedItems);

  // Use the FIRST order as the "current" order for adding new items
  return { orderId: openOrders[0].orderId, items: formattedItems };
};
