import { apiFetch } from '../../services/api';

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
 * Open a new order on a table and mark the table as occupied.
 *
 * @param {Object} table - The table the order belongs to
 * @returns {Promise<number>} - The new order's ID
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
  const order = await orderResponse.json();
  console.log('Order created', order);

  // Mark table as occupied
  await apiFetch(`/api/tables/${table.tableId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status: 'occupied' })
  });

  return order.orderId;
};

/**
 * Save one item to an order as a draft (not sent to the kitchen yet).
 *
 * @param {number} orderId - Order to add the item to
 * @param {Object} item - Item with menuItemId, quantity, and price
 * @returns {Promise<Object>} - The saved order item, including its new orderItemId
 */
export const createDraftItem = async (orderId, item) => {
  const response = await apiFetch('/api/order-items', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      orderId: orderId,
      menuItemId: item.menuItemId,
      quantity: item.quantity,
      price: item.price,
      status: 'draft'
    })
  });
  return response.json();
};

/**
 * Save every draft item that is not in the database yet.
 *
 * All saves run in parallel and we wait for ALL of them to finish, so a Send
 * right afterwards never misses an item that was still being saved.
 *
 * @param {number} orderId - Order the items belong to
 * @param {Array} items - The full on-screen item list
 * @returns {Promise<Array>} - One entry per item, in the same order: the new
 *                             orderItemId for items that were just saved, or
 *                             null for items that did not need saving.
 *                             (Matching by position, not by menu item, keeps
 *                             two of the same dish from sharing one ID.)
 */
export const saveNewDraftItems = (orderId, items) => {
  return Promise.all(
    items.map(async (item) => {
      const needsSaving = item.status === 'draft' && !item.orderItemId;
      if (!needsSaving) return null;

      const created = await createDraftItem(orderId, item);
      return created.orderItemId;
    })
  );
};

// Send ALL draft items for this order (backend starts the edit timer and answers with the sent items)
export const sendDraftItems = async (orderId) => {
  const sendResponse = await apiFetch(`/api/order-items/order/${orderId}/send`, {
    method: 'POST'
  });
  return sendResponse.json();
};

// Send everything on the order to the prep stations right now (bypass the timer)
export const sendOrderNow = (orderId) => {
  return apiFetch(`/api/order-items/order/${orderId}/send-now`, {
    method: 'POST'
  });
};

// Delete a saved order item. Returns true only if the backend confirmed the delete.
export const deleteOrderItem = async (orderItemId) => {
  const deleteResponse = await apiFetch(`/api/order-items/${orderItemId}`, {
    method: 'DELETE'
  });
  console.log('Delete response status:', deleteResponse.status);
  return deleteResponse.ok;
};

/**
 * Load every item from ALL open orders on a table, with menu item names attached.
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

  // Flatten all items into one array
  const allItems = allItemArrays.flat();
  const formattedItems = allItems.map(item => ({
    orderItemId: item.orderItemId,
    menuItemId: item.menuItemId,
    name: menuData.find(m => m.menuItemId === item.menuItemId)?.name,
    price: item.price,
    quantity: item.quantity,
    status: item.status
  }));

  console.log('Loaded all items from all orders:', formattedItems);

  // Use the FIRST order as the "current" order for adding new items
  return { orderId: openOrders[0].orderId, items: formattedItems };
};
