import { formatTableName } from '../../utils/formatters';
import './ActiveOrders.css';

// A kitchen ticket that has waited this many minutes turns yellow, then red
export const WAITING_WARNING_MINUTES = 10;
export const WAITING_LATE_MINUTES = 15;

/**
 * One order as a ticket on the Active Orders screen: its table, how long it has
 * waited, and its items with their modifiers, notes, and status.
 *
 * It only draws what it is given. Which items are on the ticket, whether it is done,
 * and how long it has waited are worked out by the screen (ActiveOrders), which also
 * does the talking to the backend when START or COMPLETE is pressed.
 *
 * In its own file so that the template gallery can draw the very same ticket the
 * kitchen display does (views/Gallery). Styles come from ActiveOrders.css.
 *
 * @param {Object} order - The order: { orderId, tableNumber, isQuickOrder, ... }
 * @param {Array} items - The items to list, already in ring-in order
 * @param {boolean} isDone - Nothing on the ticket is left to make
 * @param {number|null} waitingMinutes - How long the oldest unfinished item has waited
 *        (kitchen display only; null leaves the wait off)
 * @param {boolean} showButtons - Whether START and COMPLETE are offered
 * @param {Function} onStart - Called with an item's ID when START is pressed
 * @param {Function} onComplete - Called with an item's ID when COMPLETE is pressed
 */
function OrderTicket({ order, items, isDone, waitingMinutes, showButtons, onStart, onComplete }) {
  return (
    <div className={`order-card ${isDone ? 'done' : ''}`}>
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
        {items.map(item => (
            // One ticket line: the item row, then its modifiers and kitchen note underneath
            <div key={item.orderItemId} className="order-item-ticket">
            <div className="order-item-row">
            <span>{item.quantity}x</span>
            <span>{item.name}</span>
            <span className={`status-${item.status}`}>{item.status}</span>

            {showButtons && item.status === 'pending' && (
              <button
                className="btn-start"                                                   // changes status from pending to fired
                onClick={() => onStart(item.orderItemId)}
              >
                START
              </button>
            )}

            {showButtons && item.status === 'fired' && (
              <button
                className="btn-complete"                                                                  // Complete order button appears when status is fired
                onClick={() => onComplete(item.orderItemId)}
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
}

export default OrderTicket;
