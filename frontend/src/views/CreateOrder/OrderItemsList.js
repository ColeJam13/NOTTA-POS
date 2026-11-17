import { forwardRef } from 'react';
import { Lock } from 'lucide-react';

// An item is locked once it has gone to the prep station - it can no longer be edited or removed
const isLocked = (item) =>
  item.status === 'pending' || item.status === 'fired' || item.status === 'completed';

/**
 * The scrolling list of items on the current order (left-hand panel).
 *
 * Locked items show a lock icon. Draft and limbo items show an "x" to remove them.
 * forwardRef lets the parent hold a reference to the list's <div> so it can
 * auto-scroll to the newest item.
 *
 * Styles come from CreateOrder.css (loaded by the parent view).
 */
const OrderItemsList = forwardRef(function OrderItemsList({ items, onRemoveItem }, ref) {
  return (
    <div className="order-items-list" ref={ref}>
    {items.map((item, index) => (
        <div key={index} className={`order-item ${
          isLocked(item) ? 'locked' :
          (item.status === 'draft' && item.orderItemId) ? 'saved-draft' :
          ''
        }`}>
        <span>
          {isLocked(item) && <Lock size={14} className="lock-icon" />}
          {item.name}
        </span>
        <span>${item.price.toFixed(2)}</span>
        {(item.status === 'draft' || item.status === 'limbo') && (
          <button
              className="btn-remove"
              onClick={() => onRemoveItem(item, index)}
          >
              x
          </button>
        )}
        </div>
    ))}
    </div>
  );
});

export default OrderItemsList;
