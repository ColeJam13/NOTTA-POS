import { useState, useEffect, useRef, useCallback } from 'react';
import NavBar from '../../components/NavBar';
import PaymentModal from '../../components/PaymentModal';
import { formatTableName } from '../../utils/formatters';
import './CreateOrder.css';
import { apiFetch } from '../../services/api';
import { calculateOrderTotals, TAX_RATE_LABEL } from '../../utils/totals';
import MenuPanel from './MenuPanel';
import OrderItemsList from './OrderItemsList';
import { useEditTimer } from './useEditTimer';
import {
  createQuickOrderTable,
  openOrderForTable,
  createDraftItem,
  saveNewDraftItems,
  sendDraftItems,
  sendOrderNow,
  deleteOrderItem,
  loadOpenOrderForTable
} from './orderActions';

/**
 * Create Order screen.
 *
 * This file owns the screen's state and decides what happens when the server
 * taps something. The pieces it used to contain now live next to it:
 *   orderActions.js   - requests to the backend
 *   useEditTimer.js   - the 15-second edit-window countdown
 *   MenuPanel.js      - category tabs + menu item cards
 *   OrderItemsList.js - the list of items on the order
 */
function CreateOrder({ setCurrentView, selectedTable, setSelectedTable }) {
  const [menuItems, setMenuItems] = useState([]);
  const [selectedCategory, setSelectedCategory] = useState('Savory');
  const [orderItems, setOrderItems] = useState([])
  const [currentTable, setCurrentTable] = useState(selectedTable); // Changed: store entire table object instead of just ID
  const [currentOrderId, setCurrentOrderId] = useState(null);
  const orderItemsRef = useRef(null);
  const [showDraftSaved, setShowDraftSaved] = useState(false);
  const [showPaymentModal, setShowPaymentModal] = useState(false);

  // Subtotal, tax, and total for the items on screen (shared math with the Payment modal)
  const totals = calculateOrderTotals(orderItems);

  // When the edit window closes, every item still in limbo becomes locked ("pending") on screen.
  // The backend locks them on its own schedule; this keeps the screen in step with it.
  const lockLimboItems = useCallback(() => {
    setOrderItems(prevItems => prevItems.map(item => {
      if (item.status === 'limbo') return { ...item, status: 'pending' };
      return item;                                                                    // Don't touch draft/pending/fired/completed items
    }));
  }, []);

  const timer = useEditTimer(lockLimboItems);
  const secondsLeft = timer.secondsLeft;

// Set initial table state - if selectedTable exists, use it. Otherwise null (Quick Order will be created on Save/Send)
useEffect(() => {
  if (selectedTable) {
    setCurrentTable(selectedTable);
  } else {
    // No table selected - will create Quick Order when user saves/sends
    setCurrentTable(null);
  }
}, [selectedTable]);

// Load existing order if table is occupied
useEffect(() => {
  if (currentTable && currentTable.status === 'occupied') {
    loadOpenOrderForTable(currentTable.tableId)
      .then(openOrder => {
        if (openOrder) {
          setCurrentOrderId(openOrder.orderId);
          setOrderItems(openOrder.items);
        }
      })
      .catch(error => console.error('Error loading order:', error));
  }
}, [currentTable]); // Changed dependency from selectedTable to currentTable

useEffect(() => {
  apiFetch('/api/menu-items')                             // fetch menu items
    .then(response => response.json())
    .then(data => {
      console.log('Menu items:', data);
      setMenuItems(data);
    })
    .catch(error => console.error('Error fetching menu items:', error));
  }, []);

    useEffect(() => {
    if (orderItemsRef.current) {
      orderItemsRef.current.scrollTop = orderItemsRef.current.scrollHeight;
    }
  }, [orderItems]);

  // Make sure there is a table and an open order to attach items to.
  // Shared by Send and Save Draft (this block used to be written out in both).
  const ensureTableAndOrder = async () => {
    let orderId = currentOrderId;
    let tableToUse = currentTable;

    // If no table exists yet (Quick Order scenario), create one now
    if (!tableToUse) {
      tableToUse = await createQuickOrderTable();
      setCurrentTable(tableToUse);
    }

    // If no current order, create a new one
    if (!orderId) {
      orderId = await openOrderForTable(tableToUse);
      setCurrentOrderId(orderId);
    }

    return orderId;
  };

  // Save any drafts that are not in the database yet and record their new IDs on screen.
  // newIds lines up with orderItems by position: an ID for each item just saved, null otherwise.
  const saveDrafts = async (orderId) => {
    const newIds = await saveNewDraftItems(orderId, orderItems);

    setOrderItems(prevItems => prevItems.map((item, index) => {
      const newId = newIds[index];
      if (newId && !item.orderItemId) {
        return { ...item, orderItemId: newId };
      }
      return item;
    }));
  };

  const sendOrder = async () => {
    try {
      setShowDraftSaved(false);  // Clear the draft notification when sending

      const orderId = await ensureTableAndOrder();

      // Add only the DRAFT items that aren't already in the database
      // Wait for ALL items to be created before sending
      await saveDrafts(orderId);

      // Now send ALL draft items for this order (backend will only send items with status 'draft')
      const sentItems = await sendDraftItems(orderId);
      console.log('Sent items response:', sentItems);

      if (sentItems && sentItems.length > 0) {
        const expiresAt = new Date(sentItems[0].delayExpiresAt);
        console.log('Timer expires at:', expiresAt);
        console.log('Current time:', new Date());
        timer.start(expiresAt);
      }

      // Every draft is now in limbo (timer running, still editable)
      setOrderItems(prevItems => prevItems.map(item => {
        if (item.status === 'draft') return { ...item, status: 'limbo' };
        return item;
      }));

    } catch (error) {
      console.error('Error sending order:', error);
      alert('Failed to send order');
    }
  };

  const saveDraft = async () => {
    try {
      const orderId = await ensureTableAndOrder();

      // Save only draft items that don't have an orderItemId (not yet in database)
      await saveDrafts(orderId);

      // Show success notification
      setShowDraftSaved(true);

      // Hide after 3 seconds
      setTimeout(() => {
        setShowDraftSaved(false);
      }, 3000);

    } catch (error) {
      console.error('Error saving draft:', error);
      alert('Failed to save draft');
    }
  };

  // The Send button does two jobs: start the edit window, or cut it short ("SEND NOW?")
  const handleSendClick = async () => {
    if (timer.isRunning && currentOrderId) {
      // Call backend to send now
      await sendOrderNow(currentOrderId);

      // Stop the timer
      timer.finishNow();

      // Update local state: change limbo items to pending
      lockLimboItems();
    } else {
      sendOrder();
    }
  };

  const removeItem = async (item, index) => {
    console.log('X button clicked! Item:', item);

    // If item has an orderItemId, delete it from database
    if (item.orderItemId) {
      console.log('Item has orderItemId, attempting delete:', item.orderItemId);
      try {
        const deleted = await deleteOrderItem(item.orderItemId);

        if (!deleted) {
          console.error('Failed to delete item from backend');
          return; // Don't remove from UI if backend delete failed
        }

        console.log('Successfully deleted item:', item.orderItemId);
      } catch (error) {
        console.error('Error deleting item:', error);
        return; // Don't remove from UI if delete failed
      }
    } else {
      console.log('Item has NO orderItemId, skipping backend delete');
    }

    // Remove from local state ONLY after successful backend delete
    console.log('Removing item from local state at index:', index);
    setOrderItems(prevItems => prevItems.filter((_, i) => i !== index));

    // Reset timer if active
    if (timer.isRunning) {
      console.log('Resetting timer');
      timer.restart();
    }
  };

  const addItem = async (item) => {
    // If timer is active and we have an order, create and send immediately
    if (timer.isRunning && currentOrderId) {
      try {
        // Create item
        const created = await createDraftItem(currentOrderId, {
          menuItemId: item.menuItemId,
          quantity: 1,
          price: item.price
        });
        console.log('Created item response:', created);

        // Send it immediately
        await sendDraftItems(currentOrderId);

        // Add to state as 'limbo' with orderItemId - USE FUNCTIONAL UPDATE
        setOrderItems(prevItems => [...prevItems, {
          orderItemId: created.orderItemId,
          menuItemId: item.menuItemId,
          name: item.name,
          price: item.price,
          quantity: 1,
          status: 'limbo'
        }]);

        console.log('Added item to state with orderItemId:', created.orderItemId);

        // Reset timer
        timer.restart();

      } catch (error) {
        console.error('Error adding item during timer:', error);
      }
    } else {
      // No timer - normal behavior (add as draft)
      setOrderItems(prevItems => [...prevItems, {
        menuItemId: item.menuItemId,
        name: item.name,
        price: item.price,
        quantity: 1,
        status: 'draft'
      }]);
    }

    // A new round of items is starting - clear the leftover "locked" message
    timer.clearLockedMessage();
  };

  return (
    <div className="page-with-nav">
      <NavBar currentView="createOrder" setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />
            <div className="app">
            <div className="order-panel">
                <h2>Current Order - {currentTable ? formatTableName(currentTable) : 'New Quick Order'}</h2>

                <div className={`notification-bar ${
                  showDraftSaved ? 'notification-success' :
                  secondsLeft !== null && secondsLeft > 0 ? 'notification-timer' :
                  secondsLeft === 0 ? 'notification-locked' :
                  'notification-default'
                }`}>
                  {showDraftSaved ? 'Draft saved! Items remain editable' :
                  secondsLeft !== null && secondsLeft > 0 ? `${secondsLeft} seconds to edit` :
                  secondsLeft === 0 ? 'Items locked and sent to prep station' :
                  orderItems.length === 0 ? 'Add items to order' :
                  'Ready to send or save as draft'}
                </div>

                <OrderItemsList ref={orderItemsRef} items={orderItems} onRemoveItem={removeItem} />

            <div className="order-totals">
                <div className="total-row">
                <span>Subtotal:</span>
                <span>${totals.subtotal.toFixed(2)}</span>
                </div>
                <div className="total-row">
                <span>Tax ({TAX_RATE_LABEL}):</span>
                <span>${totals.tax.toFixed(2)}</span>
                </div>
                <div className="total-row total">
                <span>TOTAL:</span>
                <span>${totals.total.toFixed(2)}</span>
                </div>
            </div>
                <div className="order-actions">
                  <div className="action-row">
                    <button className="btn-save" onClick={saveDraft}>SAVE DRAFT</button>
                    <button className="btn-send" onClick={handleSendClick}>
                        {timer.isRunning ? 'SEND NOW?' : 'SEND ORDER'}
                    </button>
                  </div>
                  <button
                    className="btn-close-order"
                    onClick={() => setShowPaymentModal(true)}
                    disabled={orderItems.length === 0}
                  >
                    CLOSE ORDER
                  </button>
                </div>
            </div>

            <MenuPanel
              menuItems={menuItems}
              selectedCategory={selectedCategory}
              onSelectCategory={setSelectedCategory}
              onAddItem={addItem}
            />
        </div>

      {showPaymentModal && (
        <PaymentModal
          order={{ orderId: currentOrderId }}
          orderItems={orderItems}
          onClose={() => setShowPaymentModal(false)}
          onPaymentSuccess={() => {
            setShowPaymentModal(false);
            setOrderItems([]);
            setCurrentOrderId(null);
            setCurrentTable(null);
            setSelectedTable(null);
          }}
        />
      )}
    </div>
  );
}

export default CreateOrder;
