import { useState, useEffect, useCallback } from 'react';
import NavBar from '../NavBar/NavBar';
import { formatTableName, formatTimeAgo, formatDateTime, formatPaymentMethod, formatUpcharge } from '../../utils/formatters';
import { getJson } from '../../services/api';
import './TableChecks.css';

// How often the screen asks the backend for the latest checks, in milliseconds
const REFRESH_MS = 3000;

// WHO SEES A TIP is decided by the backend, not here. A person is sent the tip
// and the payment method on checks they opened; a manager on every check (the
// owner's decision D10). For anyone else those two fields are NOT IN the check
// at all, and this screen shows nothing in their place: no "Tip: $0.00", no
// blank label. The screen never looks at the signed-in person's role for
// this. It draws what it was sent.
const tipIsShown = (check) => check.tipAmount != null;              // != null is false for a missing field as well as for null

// The amount on a closed check: what the guest paid (check + tip) when the tip
// was sent, and the check alone when it was not
const amountOn = (check) => check.total + (tipIsShown(check) ? check.tipAmount : 0);

/**
 * The body of the Tables screen and the Quick Orders screen: an ACTIVE view of
 * open checks and a CLOSED view of recent paid ones.
 *
 * WHAT: One component for both screens. They were two files with the same
 *       280 lines; the only differences were which tables they asked for and
 *       a few words of text.
 * HOW:  One request (GET /api/tables/overview) returns everything shown here,
 *       already matched up by the backend. These screens used to download
 *       every order, order item, payment, and menu item every 3 seconds and
 *       match them up in the browser.
 *
 * The tip and payment method on a closed check are only shown when the
 * backend sent them (see tipIsShown above).
 *
 * The page styles (cards, pop-up) come from Tables.css, loaded by the Tables view.
 *
 * @param {boolean} quickOrders - true for the Quick Orders screen, false for Tables
 * @param {string} title - The heading, e.g. "TABLES"
 * @param {string} navKey - Which nav bar button to highlight
 * @param {string} emptyActiveText - Shown when nothing is open
 * @param {string} emptyClosedText - Shown when nothing has been closed
 */
function TableChecks({ quickOrders, title, navKey, emptyActiveText, emptyClosedText, setCurrentView, setSelectedTable }) {
    const [view, setView] = useState('active');

    // { activeTables: [...], closedChecks: [...] } - see TablesOverviewView on the backend
    const [overview, setOverview] = useState({ activeTables: [], closedChecks: [] });
    const [selectedClosedOrder, setSelectedClosedOrder] = useState(null);

    const loadOverview = useCallback(() => {
        getJson(`/api/tables/overview?quickOrders=${quickOrders}`)
            .then(data => setOverview(data))
            .catch(error => console.error('Error fetching tables overview:', error));
    }, [quickOrders]);

    useEffect(() => {
        loadOverview();                                                     // Load on mount...
        const interval = setInterval(loadOverview, REFRESH_MS);             // ...then auto-refresh every 3 seconds
        return () => clearInterval(interval);
    }, [loadOverview]);

                                                                            // Determine border color based on status
    const borderColorFor = (table) => {
        if (table.completedCount === table.itemCount) return 'green';
        if (table.limboCount > 0) return 'purple';
        if (table.pendingCount > 0 || table.firedCount > 0) return 'yellow';
        return 'default';
    };

    return (
        <div className="page-with-nav">
        <NavBar currentView={navKey} setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />
            <div className="tables-page">
                <h2>{title}</h2>

                <div className="view-toggle">
                    <button
                        className={`toggle-btn ${view === 'active' ? 'active' : ''}`}
                        onClick={() => setView('active')}
                    >
                        ACTIVE
                    </button>
                    <button
                        className={`toggle-btn ${view === 'closed' ? 'active' : ''}`}
                        onClick={() => setView('closed')}
                    >
                        CLOSED
                    </button>
                </div>

                {view === 'active' && (
                    <div className="tables-grid">
                        {overview.activeTables.map(table => (
                            <div 
                                key={table.tableId} 
                                className={`table-card border-${borderColorFor(table)}`}
                                onClick={() => {
                                    setSelectedTable(table);                // The card carries the table's own fields, which is what the order screen needs
                                    setCurrentView('createOrder');
                                }}
                            >
                                <h3 className="table-card-number">{table.tableNumber}</h3>
                                <div className="table-card-total">${table.subtotal.toFixed(2)}</div>
                                <div className="table-card-items">{table.itemCount} items</div>
                                <div className="table-card-status">
                                    {table.limboCount > 0 && `${table.limboCount}L `}
                                    {table.pendingCount > 0 && `${table.pendingCount}P `}
                                    {table.firedCount > 0 && `${table.firedCount}F `}
                                    {table.completedCount > 0 && `${table.completedCount}C`}
                                </div>
                                <div className="table-card-server">Server: {table.serverName || 'N/A'}</div>
                                <div className="table-card-time">{formatTimeAgo(table.openedAt)}</div>
                            </div>
                        ))}

                        {overview.activeTables.length === 0 && (
                            <p className="no-tables">{emptyActiveText}</p>
                        )}
                    </div>
                )}

                {view === 'closed' && (
                    <div className="tables-grid">
                        {overview.closedChecks.map(check => (
                            <div 
                                key={check.orderId} 
                                className="table-card closed-card"
                                onClick={() => setSelectedClosedOrder(check)}
                            >
                                <h3 className="table-card-number">{check.tableNumber}</h3>
                                {/* What the guest paid: check + tip. On somebody else's check, the check alone. */}
                                <div className="table-card-total">${amountOn(check).toFixed(2)}</div>
                                <div className="table-card-items">{check.items.length} items</div>
                                <div className="table-card-server">Server: {check.serverName || 'N/A'}</div>
                                {tipIsShown(check) && (
                                    <div className="table-card-payment">{formatPaymentMethod(check.paymentMethod)}</div>
                                )}
                                <div className="table-card-time">{formatTimeAgo(check.closedAt || check.createdAt)}</div>
                            </div>
                        ))}

                        {overview.closedChecks.length === 0 && (
                            <p className="no-tables">{emptyClosedText}</p>
                        )}
                    </div>
                )}
            </div>

            {selectedClosedOrder && (
                <div className="modal-overlay" onClick={() => setSelectedClosedOrder(null)}>
                    <div className="modal-content" onClick={(e) => e.stopPropagation()}>
                        <button className="modal-close" onClick={() => setSelectedClosedOrder(null)}>×</button>
                        
                        <h2>{formatTableName(selectedClosedOrder)}</h2>
                        <div className="modal-info">
                            <p><strong>Server:</strong> {selectedClosedOrder.serverName || 'N/A'}</p>
                            <p><strong>Order Created:</strong> {formatDateTime(selectedClosedOrder.createdAt)}</p>
                            <p><strong>Order Closed:</strong> {formatDateTime(selectedClosedOrder.closedAt)}</p>
                            {tipIsShown(selectedClosedOrder) && (
                                <p><strong>Payment Method:</strong> {formatPaymentMethod(selectedClosedOrder.paymentMethod)}</p>
                            )}
                        </div>

                        <div className="modal-items">
                            <h3>Order Items</h3>
                            {selectedClosedOrder.items.map((item) => (
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
                                <span>${selectedClosedOrder.subtotal.toFixed(2)}</span>
                            </div>
                            <div className="modal-total-row">
                                <span>Tax:</span>
                                <span>${selectedClosedOrder.tax.toFixed(2)}</span>
                            </div>
                            {/* The Tip row is left out altogether on a check whose tip was not sent */}
                            {tipIsShown(selectedClosedOrder) && (
                                <div className="modal-total-row">
                                    <span>Tip:</span>
                                    <span>${selectedClosedOrder.tipAmount.toFixed(2)}</span>
                                </div>
                            )}
                            <div className="modal-total-row final">
                                <span>Total:</span>
                                <span>${amountOn(selectedClosedOrder).toFixed(2)}</span>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

export default TableChecks;
