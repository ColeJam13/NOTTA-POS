import { formatTableName, formatDateTime, formatPaymentMethod, formatUpcharge } from '../../utils/formatters';

// WHO SEES A TIP is decided by the backend, not here. A person is sent the tip
// and the payment method on checks they opened; a manager on every check (the
// owner's decision D10). For anyone else those two fields are NOT IN the check
// at all, and the screens show nothing in their place: no "Tip: $0.00", no
// blank label. No screen looks at the signed-in person's role for
// this. It draws what it was sent.
export const tipIsShown = (check) => check.tipAmount != null;              // != null is false for a missing field as well as for null

// The amount on a closed check: what the guest paid (check + tip) when the tip
// was sent, and the check alone when it was not
export const amountOn = (check) => check.total + (tipIsShown(check) ? check.tipAmount : 0);

/**
 * A paid check, as a pop-up: who served it, when, every item with its modifiers
 * and kitchen note, and the amounts.
 *
 * WHAT: The pop-up the Tables and Quick Orders screens open from a closed
 *       check's card, and the search box opens from a closed check it found.
 * WHY ONE COMPONENT: it was written inside TableChecks. When the search box
 *       needed the same pop-up, a copy would have meant two places deciding
 *       whether a tip is shown. There is one: tipIsShown above.
 *
 * It draws what it is given and asks the backend nothing.
 *
 * Its styles (.modal-overlay, .modal-content, the check lines and totals) are
 * in views/Tables/Tables.css and views/QuickOrders/QuickOrders.css, where they
 * were before this was a component. Merging those two stylesheets is a known
 * gap (CLAUDE.md); it changes what is drawn in small ways, so it is not done
 * in passing here.
 *
 * @param {Object} check - A closed check (ClosedCheckView on the backend)
 * @param {Function} onClose - Called when the pop-up is dismissed
 */
function ClosedCheck({ check, onClose }) {
    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-content" onClick={(e) => e.stopPropagation()}>
                <button className="modal-close" onClick={onClose}>×</button>

                <h2>{formatTableName(check)}</h2>
                <div className="modal-info">
                    <p><strong>Server:</strong> {check.serverName || 'N/A'}</p>
                    <p><strong>Order Created:</strong> {formatDateTime(check.createdAt)}</p>
                    <p><strong>Order Closed:</strong> {formatDateTime(check.closedAt)}</p>
                    {tipIsShown(check) && (
                        <p><strong>Payment Method:</strong> {formatPaymentMethod(check.paymentMethod)}</p>
                    )}
                </div>

                <div className="modal-items">
                    <h3>Order Items</h3>
                    {check.items.map((item) => (
                        // One line of the check: the item and its price, then its modifiers and kitchen note underneath
                        <div key={item.orderItemId} className="check-line">
                            <div className="modal-item">
                                <span>{item.name}</span>
                                <span>${item.price.toFixed(2)}</span>
                            </div>

                            {item.modifiers.length > 0 && (
                                <ul className="check-line-modifiers">
                                    {item.modifiers.map(modifier => (
                                        <li key={modifier.modifierId}>
                                            {modifier.name}
                                            {modifier.priceAdjustment !== 0 && <small>{formatUpcharge(modifier.priceAdjustment)}</small>}
                                        </li>
                                    ))}
                                </ul>
                            )}

                            {item.specialInstructions && (
                                <p className="check-line-note">NOTE: {item.specialInstructions}</p>
                            )}
                        </div>
                    ))}
                </div>

                <div className="modal-totals">
                    <div className="modal-total-row">
                        <span>Subtotal:</span>
                        <span>${check.subtotal.toFixed(2)}</span>
                    </div>
                    <div className="modal-total-row">
                        <span>Tax:</span>
                        <span>${check.tax.toFixed(2)}</span>
                    </div>
                    {/* The Tip row is left out altogether on a check whose tip was not sent */}
                    {tipIsShown(check) && (
                        <div className="modal-total-row">
                            <span>Tip:</span>
                            <span>${check.tipAmount.toFixed(2)}</span>
                        </div>
                    )}
                    <div className="modal-total-row final">
                        <span>Total:</span>
                        <span>${amountOn(check).toFixed(2)}</span>
                    </div>
                </div>
            </div>
        </div>
    );
}

export default ClosedCheck;
