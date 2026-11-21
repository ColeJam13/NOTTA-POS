import { forwardRef } from 'react';
import { Lock, Pencil } from 'lucide-react';
import { formatUpcharge } from '../../utils/formatters';

// An item is locked once it has gone to the prep station - it can no longer be edited or removed
const isLocked = (item) =>
  item.status === 'pending' || item.status === 'fired' || item.status === 'completed';

/**
 * The scrolling list of items on the current order (left-hand panel).
 *
 * Each item shows its modifiers and kitchen note underneath its name.
 * Locked items show a lock icon. Draft and limbo items show a pencil to
 * change their modifiers or note, and an "x" to remove them.
 * forwardRef lets the parent hold a reference to the list's <div> so it can
 * auto-scroll to the newest item.
 *
 * Styles come from CreateOrder.css (loaded by the parent view).
 */
const OrderItemsList = forwardRef(function OrderItemsList({ items, onRemoveItem, onEditItem }, ref) {
  return (
    <div className="order-items-list" ref={ref}>
    {items.map((item, index) => (
        <div key={index} className={`order-item ${
          isLocked(item) ? 'locked' :
          (item.status === 'draft' && item.orderItemId) ? 'saved-draft' :
          ''
        }`}>
        <div className="order-item-info">
          <span>
            {isLocked(item) && <Lock size={14} className="lock-icon" />}
            {item.name}
          </span>

          {/* Modifiers, one per line, with the upcharge beside any that cost extra */}
          {item.modifiers && item.modifiers.length > 0 && (
            <ul className="order-item-modifiers">
              {item.modifiers.map(modifier => (
                <li key={modifier.modifierId}>
                  {modifier.name}
                  {modifier.priceAdjustment !== 0 && <small>{formatUpcharge(modifier.priceAdjustment)}</small>}
                </li>
              ))}
            </ul>
          )}

          {/* Kitchen note */}
          {item.specialInstructions && (
            <p className="order-item-note">NOTE: {item.specialInstructions}</p>
          )}
        </div>
        <span>${item.price.toFixed(2)}</span>
        {(item.status === 'draft' || item.status === 'limbo') && (
          <button
              className="btn-edit-item"
              aria-label={`Edit ${item.name}`}                // Icon-only button: this is the name screen readers (and tests) see
              onClick={() => onEditItem(item, index)}
          >
              <Pencil size={16} />
          </button>
        )}
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
