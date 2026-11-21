import { useState, useEffect, useCallback } from 'react';
import NavBar from '../../components/NavBar/NavBar';
import { formatTableName } from '../../utils/formatters';
import './ActiveOrders.css';
import { apiFetch, getJson } from '../../services/api';
import { useStaff } from '../../context/StaffContext';
import { canDo } from '../../utils/roles';

// A kitchen ticket that has waited this many minutes turns yellow, then red
const WAITING_WARNING_MINUTES = 10;
const WAITING_LATE_MINUTES = 15;

// Statuses the kitchen sees: the edit window has closed and the item is theirs to make
const isOnKitchenTicket = (item) =>
  item.status === 'pending' || item.status === 'fired' || item.status === 'completed';

// Whole minutes since a timestamp (never below zero - see formatTimeAgo for why)
const minutesSince = (timestamp) =>
  Math.max(0, Math.floor((Date.now() - new Date(timestamp).getTime()) / 1000 / 60));

/**
 * Active Orders: every open order's items and where each one stands.
 *
 * FOH VIEW is for servers: everything that has been sent, including items
 * still in their edit window.
 *
 * BOH VIEW is the kitchen display:
 *   - Station buttons (ALL, KITCHEN, BAR, ...) show one station only its own
 *     items, so the bar is not reading through food tickets.
 *   - Each ticket shows how long its oldest unfinished item has been waiting,
 *     and tickets are listed longest wait first.
 *   - A ticket with nothing left to make drops off the screen. SHOW DONE
 *     brings finished tickets back, for when a plate needs checking.
 */
function ActiveOrders({ setCurrentView, setSelectedTable }) {
  const [view, setView] = useState('FOH');

  // START and COMPLETE are for the people who make the food and drinks. A server can
  // open the BOH view to see how a ticket is coming along, but is not offered buttons
  // the backend would refuse (it allows the kitchen, the bar, and managers).
  const { staff } = useStaff();
  const canMakeItems = canDo(staff?.role, 'make-items');

  // Kitchen display settings (BOH view only)
  const [stations, setStations] = useState([]);           // Prep stations, e.g. [{ prepStationId: 1, name: 'Kitchen' }, ...]
  const [stationId, setStationId] = useState(null);       // The station being shown. null = ALL.
  const [showDone, setShowDone] = useState(false);        // Whether finished tickets are shown
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
    // The station buttons. If this fails the kitchen view still works: it just shows ALL.
    getJson('/api/prep-stations')
      .then(data => setStations(data.filter(station => station.isActive !== false)))
      .catch(error => console.error('Error fetching prep stations:', error));
  }, []);

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

  // Work out which tickets to show, and which items on each
  const tickets = orders
    .map(order => {
      const filteredItems = view === 'BOH'
        ? order.items.filter(item =>
            isOnKitchenTicket(item) &&                                                                                              // FOH and BOH available state views
            // One station sees only its own items. An item with no station is shown to every
            // station: better that two stations see it than that nobody does.
            (stationId === null || item.prepStationId == null || item.prepStationId === stationId))
        : order.items.filter(item => item.status !== 'draft');

      // Sort by orderItemId to keep items in a stable order when status changes
      // (copy first with [...] so the array held in state is never reordered in place)
      const sortedItems = [...filteredItems].sort((a, b) => a.orderItemId - b.orderItemId);

      if (sortedItems.length === 0) return null;

      // Kitchen view: a ticket is done when nothing on it is left to make
      const unfinished = sortedItems.filter(item => item.status !== 'completed');
      const isDone = view === 'BOH' && unfinished.length === 0;
      if (isDone && !showDone) return null;

      // Kitchen view: when the oldest unfinished item on the ticket was sent
      const sentTimes = unfinished.filter(item => item.sentAt).map(item => new Date(item.sentAt).getTime());
      const oldestSentAt = view === 'BOH' && sentTimes.length > 0 ? Math.min(...sentTimes) : null;

      return {
        order,
        sortedItems,
        isDone,
        oldestSentAt,
        waitingMinutes: oldestSentAt !== null ? minutesSince(oldestSentAt) : null
      };
    })
    .filter(ticket => ticket !== null);

  // Kitchen view: longest wait first, finished tickets last. (sort() keeps tickets that
  // tie in the order the backend sent them, which is oldest order first.)
  if (view === 'BOH') {
    tickets.sort((a, b) => {
      if (a.isDone !== b.isDone) return a.isDone ? 1 : -1;
      return (a.oldestSentAt ?? Infinity) - (b.oldestSentAt ?? Infinity);
    });
  }

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

            {view === 'BOH' && (
              // Kitchen display controls: which station, and whether finished tickets are shown
              <div className="kitchen-controls">
                <button
                  className={`station-btn ${stationId === null ? 'active' : ''}`}
                  aria-pressed={stationId === null}
                  onClick={() => setStationId(null)}
                >
                  ALL
                </button>
                {stations.map(station => (
                  <button
                    key={station.prepStationId}
                    className={`station-btn ${stationId === station.prepStationId ? 'active' : ''}`}
                    aria-pressed={stationId === station.prepStationId}
                    onClick={() => setStationId(station.prepStationId)}
                  >
                    {station.name.toUpperCase()}
                  </button>
                ))}
                <button
                  className={`station-btn done-toggle ${showDone ? 'active' : ''}`}
                  aria-pressed={showDone}
                  onClick={() => setShowDone(previous => !previous)}
                >
                  SHOW DONE
                </button>
              </div>
            )}

            <div className="orders-display">
                {tickets.length === 0 ? (
                <p className="no-orders">{orders.length > 0 && view === 'BOH' ? 'ALL CAUGHT UP' : 'NO ACTIVE ORDERS'}</p>
                ) : (
                tickets.map(({ order, sortedItems, isDone, waitingMinutes }) => {
                    return (
                    <div key={order.orderId} className={`order-card ${isDone ? 'done' : ''}`}>
                        <h3>
                          {formatTableName(order)} - Order #{order.orderId}
                          {/* How long the oldest unfinished item has been waiting (kitchen view only) */}
                          {waitingMinutes !== null && (
                            <span className={`ticket-wait ${
                              waitingMinutes >= WAITING_LATE_MINUTES ? 'late' :
                              waitingMinutes >= WAITING_WARNING_MINUTES ? 'warning' : ''
                            }`}>
                              {waitingMinutes} MIN
                            </span>
                          )}
                          {isDone && <span className="ticket-done">DONE</span>}
                        </h3>
                        <div className="order-items">
                        {sortedItems.map(item => (
                            // One ticket line: the item row, then its modifiers and kitchen note underneath
                            <div key={item.orderItemId} className="order-item-ticket">
                            <div className="order-item-row">
                            <span>{item.quantity}x</span>
                            <span>{item.name}</span>
                            <span className={`status-${item.status}`}>{item.status}</span>

                            {view === 'BOH' && canMakeItems && item.status === 'pending' && (
                              <button
                                className="btn-start"                                                   // changes status from pending to fired
                                onClick={() => changeItemStatus(item.orderItemId, 'start', 'fired')}
                              >
                                START
                              </button>
                            )}

                            {view === 'BOH' && canMakeItems && item.status === 'fired' && (
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
