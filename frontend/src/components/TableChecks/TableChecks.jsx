import { useState, useEffect, useCallback } from 'react';
import NavBar from '../NavBar/NavBar';
import { formatTimeAgo, formatPaymentMethod } from '../../utils/formatters';
import ActiveTableCard from './ActiveTableCard';
// The pop-up for a paid check, and the one rule for whether its tip is shown (decided by
// the backend: a check that was sent with no tip in it shows none)
import ClosedCheck, { tipIsShown, amountOn } from '../ClosedCheck';
import { getJson } from '../../services/api';
import './TableChecks.css';

// How often the screen asks the backend for the latest checks, in milliseconds
const REFRESH_MS = 3000;

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
 * backend sent them (see tipIsShown in components/ClosedCheck).
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
                            <ActiveTableCard
                                key={table.tableId}
                                table={table}
                                onOpen={() => {
                                    setSelectedTable(table);                // The card carries the table's own fields, which is what the order screen needs
                                    setCurrentView('createOrder');
                                }}
                            />
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

            {/* The paid check that was tapped, as a pop-up. The search box opens the same one. */}
            {selectedClosedOrder && (
                <ClosedCheck check={selectedClosedOrder} onClose={() => setSelectedClosedOrder(null)} />
            )}
        </div>
    );
}

export default TableChecks;
