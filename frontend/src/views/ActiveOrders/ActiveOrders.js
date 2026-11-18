import { useState, useEffect, useCallback } from 'react';
import NavBar from '../../components/NavBar/NavBar';
import { formatTableName } from '../../utils/formatters';
import './ActiveOrders.css';
import { apiFetch, getJson } from '../../services/api';

function ActiveOrders({ setCurrentView, setSelectedTable }) {
  const [view, setView] = useState('FOH');
  // Each entry is one open order with its table info and items already attached:
  // { orderId, tableNumber, isQuickOrder, serverName,
  //   items: [{ orderItemId, name, quantity, status, specialInstructions, modifiers: [{ modifierId, name }], ... }] }
  const [orders, setOrders] = useState([]);

  // One request returns open orders + their items + item names + table numbers.
  // (This used to be four separate downloads matched up here in the browser.)
  const loadActiveOrders = useCallback(() => {
    getJson('/api/orders/active')
      .then(data => setOrders(data))
      .catch(error => console.error('Error fetching active orders:', error));
  }, []);

  useEffect(() => {
    loadActiveOrders();                                               // First useEffect - loads data on mount
  }, [loadActiveOrders]);

  useEffect(() => {
    const interval = setInterval(loadActiveOrders, 3000);             // Second useEffect - polls every 3 seconds

    return () => clearInterval(interval);
  }, [loadActiveOrders]);

  // Tell the backend an item moved to a new status, then update that one item on screen
  // right away instead of waiting for the next 3-second poll.
  const changeItemStatus = async (orderItemId, action, newStatus) => {
    try {
      const response = await apiFetch(`/api/order-items/${orderItemId}/${action}`, {
        method: 'PUT'
      });

      if (!response.ok) {
        console.error(`Failed to ${action} item ${orderItemId}: status ${response.status}`);
        return;                                                       // Leave the screen as-is if the backend refused
      }

      setOrders(prevOrders =>
        prevOrders.map(order => ({
          ...order,
          items: order.items.map(i =>
            i.orderItemId === orderItemId
              ? { ...i, status: newStatus }
              : i
          )
        }))
      );
    } catch (error) {
      console.error(`Error trying to ${action} item:`, error);
    }
  };

  return (
    <div className="page-with-nav">
      <NavBar currentView="activeOrders" setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />
            <div className="active-orders-page">
            <h2>ACTIVE ORDERS</h2>

            <div className="view-toggle">
                <button
                className={`toggle-btn ${view === 'FOH' ? 'active' : ''}`}              // FOH Toggle button for active orders
                onClick={() => setView('FOH')}
                >
                FOH VIEW
                </button>
                <button
                className={`toggle-btn ${view === 'BOH' ? 'active' : ''}`}              // BOH toggle button for active orders
                onClick={() => setView('BOH')}
                >
                BOH VIEW
                </button>
            </div>

            <div className="orders-display">
                {orders.length === 0 ? (
                <p className="no-orders">NO ACTIVE ORDERS</p>
                ) : (
                orders.map(order => {
                    const filteredItems = view === 'BOH'
                    ? order.items.filter(item => item.status === 'pending' || item.status === 'fired' || item.status === 'completed')         // FOH and BOH available state views
                    : order.items.filter(item => item.status !== 'draft');

                    // Sort by orderItemId to keep items in a stable order when status changes
                    // (copy first with [...] so the array held in state is never reordered in place)
                    const sortedItems = [...filteredItems].sort((a, b) => a.orderItemId - b.orderItemId);

                    if (sortedItems.length === 0) return null;

                    return (
                    <div key={order.orderId} className="order-card">
                        <h3>{formatTableName(order)} - Order #{order.orderId}</h3>
                        <div className="order-items">
                        {sortedItems.map(item => (
                            // One ticket line: the item row, then its modifiers and kitchen note underneath
                            <div key={item.orderItemId} className="order-item-ticket">
                            <div className="order-item-row">
                            <span>{item.quantity}x</span>
                            <span>{item.name}</span>
                            <span className={`status-${item.status}`}>{item.status}</span>

                            {view === 'BOH' && item.status === 'pending' && (
                              <button
                                className="btn-start"                                                   // changes status from pending to fired
                                onClick={() => changeItemStatus(item.orderItemId, 'start', 'fired')}
                              >
                                START
                              </button>
                            )}

                            {view === 'BOH' && item.status === 'fired' && (
                              <button
                                className="btn-complete"                                                                  // Complete order button appears when status is fired
                                onClick={() => changeItemStatus(item.orderItemId, 'complete', 'completed')}             // changed state to completed
                              >
                                COMPLETE
                              </button>
                            )}
                            </div>

                            {/* What the kitchen needs to make the dish: modifiers, one per line */}
                            {item.modifiers && item.modifiers.length > 0 && (
                              <ul className="ticket-modifiers">
                                {item.modifiers.map(modifier => (
                                  <li key={modifier.modifierId}>{modifier.name}</li>
                                ))}
                              </ul>
                            )}

                            {/* Free-text kitchen note (allergies, "no onions", ...) */}
                            {item.specialInstructions && (
                              <p className="ticket-note">NOTE: {item.specialInstructions}</p>
                            )}
                            </div>
                        ))}
                        </div>
                    </div>
                    );
                })
                )}
            </div>
        </div>
    </div>
  );
}

export default ActiveOrders;
