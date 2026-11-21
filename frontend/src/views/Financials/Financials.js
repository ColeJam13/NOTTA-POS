import { useState, useEffect } from 'react';
import NavBar from '../../components/NavBar/NavBar';
import { formatTableName, formatTimeAgo, formatPaymentMethod } from '../../utils/formatters';
import './Financials.css';
import { getJson } from '../../services/api';
import { serverNow } from '../../services/serverClock';

// Write a Date the way the backend reads it: "2026-11-18T00:00:00", in this device's own
// time with no time zone. (toISOString() would convert to UTC, which is a different clock
// from the one the backend stamps orders with.)
const toLocalDateTime = (date) => {
    const pad = (number) => String(number).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
        `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
};

// What the screen shows before the first report arrives, or if it cannot be loaded
const EMPTY_REPORT = {
    orderCount: 0,
    revenue: 0,
    tax: 0,
    tips: 0,
    totalCollected: 0,
    avgOrderValue: 0,
    avgTipPercent: 0,
    paymentBreakdown: [],
    recentTransactions: []
};

/**
 * Financials screen: sales, tips, and payment methods for a time period.
 *
 * The numbers come from the backend (GET /api/reports/financials), which adds
 * up the checks closed in the period. This screen only picks the period and
 * draws the answer. It used to download every order, payment, order item, and
 * table and do the adding up here.
 */
function Financials({ setCurrentView, setSelectedTable }) {
    const [timePeriod, setTimePeriod] = useState('today');
    const [customStartDate, setCustomStartDate] = useState('');
    const [customEndDate, setCustomEndDate] = useState('');
    const [report, setReport] = useState(EMPTY_REPORT);

                                                                            // Get date range based on selected period
    const getDateRange = () => {
        // "Today" is the backend's today. Checks are stamped with the backend's clock, so
        // a device whose own date is wrong, or which sits in another time zone, would
        // otherwise ask for a day that is not the one the checks were closed on
        // (see services/serverClock.js). backendToday() makes a new Date each time it is
        // called, because setHours() below changes the Date it is called on.
        const backendToday = () => new Date(serverNow());
        const now = backendToday();
        let startDate, endDate;

        switch(timePeriod) {
            case 'today':
                startDate = new Date(now.setHours(0, 0, 0, 0));
                endDate = new Date(now.setHours(23, 59, 59, 999));
                break;
            case 'week':
                const weekStart = new Date(now);
                weekStart.setDate(now.getDate() - now.getDay());
                weekStart.setHours(0, 0, 0, 0);
                startDate = weekStart;
                endDate = new Date(backendToday().setHours(23, 59, 59, 999));  // Through the end of today, so a check closed a second from now is still counted
                break;
            case 'month':
                startDate = new Date(now.getFullYear(), now.getMonth(), 1);
                endDate = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59, 999);
                break;
            case 'custom':
                // A date box gives text like "2026-11-18". new Date("2026-11-18") reads that as
                // midnight in UTC, which is the evening BEFORE in US time zones, so picking
                // today as both dates found nothing. Adding a time of day makes JavaScript
                // read it as local time, and the end date runs to the last moment of its day.
                startDate = customStartDate ? new Date(`${customStartDate}T00:00:00`) : new Date(2000, 0, 1);
                endDate = customEndDate ? new Date(`${customEndDate}T23:59:59.999`) : new Date(backendToday().setHours(23, 59, 59, 999));
                break;
            default:
                startDate = new Date(now.setHours(0, 0, 0, 0));
                endDate = new Date(now.setHours(23, 59, 59, 999));
        }

        return { startDate, endDate };
    };

    // The period as two pieces of text. Text (not Date objects) so the effect below only
    // runs again when the period really changes, not on every redraw.
    const { startDate, endDate } = getDateRange();
    const from = toLocalDateTime(startDate);
    const to = toLocalDateTime(endDate);

    useEffect(() => {                                                       // Load the report on mount, and again whenever the period changes
        getJson(`/api/reports/financials?from=${from}&to=${to}`)
            .then(data => setReport(data))
            .catch(error => console.error('Error fetching financials report:', error));
    }, [from, to]);

    // The names the screen below has always used for these numbers
    const metrics = {
        totalRevenue: report.revenue,
        totalTax: report.tax,
        totalTips: report.tips,
        totalCollected: report.totalCollected,
        orderCount: report.orderCount,
        avgOrderValue: report.avgOrderValue,
        avgTipPercent: report.avgTipPercent,
        paymentBreakdown: report.paymentBreakdown,
        recentTransactions: report.recentTransactions
    };

    return (
        <div className="page-with-nav">
            <NavBar currentView="financials" setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />
            <div className="financials-page">
                <h2>FINANCIALS</h2>

                <div className="time-period-selector">
                    <button
                        className={`period-btn ${timePeriod === 'today' ? 'active' : ''}`}
                        onClick={() => setTimePeriod('today')}
                    >
                        TODAY
                    </button>
                    <button
                        className={`period-btn ${timePeriod === 'week' ? 'active' : ''}`}
                        onClick={() => setTimePeriod('week')}
                    >
                        THIS WEEK
                    </button>
                    <button
                        className={`period-btn ${timePeriod === 'month' ? 'active' : ''}`}
                        onClick={() => setTimePeriod('month')}
                    >
                        THIS MONTH
                    </button>
                    <button
                        className={`period-btn ${timePeriod === 'custom' ? 'active' : ''}`}
                        onClick={() => setTimePeriod('custom')}
                    >
                        CUSTOM
                    </button>
                </div>

                {timePeriod === 'custom' && (
                    <div className="custom-date-range">
                        <div className="date-input-group">
                            <label>Start Date:</label>
                            <input
                                type="date"
                                value={customStartDate}
                                onChange={(e) => setCustomStartDate(e.target.value)}
                            />
                        </div>
                        <div className="date-input-group">
                            <label>End Date:</label>
                            <input
                                type="date"
                                value={customEndDate}
                                onChange={(e) => setCustomEndDate(e.target.value)}
                            />
                        </div>
                    </div>
                )}

                <div className="stats-grid">
                    <div className="stat-card">
                        <div className="stat-label">Total Revenue</div>
                        <div className="stat-value large">${metrics.totalRevenue.toFixed(2)}</div>
                        <div className="stat-note">before tax</div>
                    </div>

                    <div className="stat-card">
                        <div className="stat-label">Total Tips</div>
                        <div className="stat-value large">${metrics.totalTips.toFixed(2)}</div>
                    </div>

                    <div className="stat-card">
                        <div className="stat-label">Orders Closed</div>
                        <div className="stat-value large">{metrics.orderCount}</div>
                    </div>

                    <div className="stat-card">
                        <div className="stat-label">Avg Order Value</div>
                        <div className="stat-value">${metrics.avgOrderValue.toFixed(2)}</div>
                    </div>

                    <div className="stat-card">
                        <div className="stat-label">Avg Tip %</div>
                        <div className="stat-value">{metrics.avgTipPercent.toFixed(1)}%</div>
                    </div>

                    <div className="stat-card">
                        <div className="stat-label">Total w/ Tips</div>
                        {/* Everything collected: sales + tax + tips. Tax used to be left out, so this
                            never matched the payment amounts in the breakdown below. */}
                        <div className="stat-value">${metrics.totalCollected.toFixed(2)}</div>
                        <div className="stat-note">includes ${metrics.totalTax.toFixed(2)} tax</div>
                    </div>
                </div>

              <div className="payment-breakdown">
                  <h3>Payment Method Breakdown</h3>
                  {metrics.paymentBreakdown.length > 0 ? (
                      // The backend sends the methods in a consistent order: CASH, CREDIT CARD, DEBIT CARD, GIFT CARD
                      metrics.paymentBreakdown.map(row => (
                          <div key={row.paymentMethod} className="payment-row">
                              <div className="payment-label">{formatPaymentMethod(row.paymentMethod)}:</div>
                              <div className="payment-amount">${row.amount.toFixed(2)}</div>
                              <div className="payment-bar-container">
                                  <div className="payment-bar" style={{ width: `${row.percent}%` }}>
                                      <span className="payment-percent">{row.percent.toFixed(1)}%</span>
                                  </div>
                              </div>
                          </div>
                      ))
                  ) : (
                      <p className="no-data">No payment data for selected period</p>
                  )}
              </div>

                <div className="recent-transactions">
                    <h3>Recent Transactions</h3>
                    {metrics.recentTransactions.length > 0 ? (
                        <div className="transaction-list">
                            {metrics.recentTransactions.map((transaction) => (
                                <div key={transaction.orderId} className="transaction-item">
                                    <div className="transaction-info">
                                        <div className="transaction-table">{formatTableName(transaction)}</div>
                                        <div className="transaction-server">Server: {transaction.serverName || 'N/A'}</div>
                                        <div className="transaction-time">{formatTimeAgo(transaction.closedAt)}</div>
                                        <div className="transaction-method">{formatPaymentMethod(transaction.paymentMethod)}</div>
                                    </div>
                                    <div className="transaction-amounts">
                                        <div className="transaction-total">${transaction.total.toFixed(2)}</div>
                                        <div className="transaction-tip">Tip: ${transaction.tip.toFixed(2)}</div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    ) : (
                        <p className="no-data">No transactions for selected period</p>
                    )}
                </div>
            </div>
        </div>
    );
}

export default Financials;