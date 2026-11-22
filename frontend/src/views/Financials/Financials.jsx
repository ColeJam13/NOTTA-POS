import { useState, useEffect } from 'react';
import NavBar from '../../components/NavBar/NavBar';
import { formatTableName, formatTimeAgo, formatPaymentMethod } from '../../utils/formatters';
import './Financials.css';
import { getJson } from '../../services/api';
import PeriodPicker, { rangeOf, periodOf } from '../../components/PeriodPicker';

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
    // The period on screen: today, this week, this month, or two dates. The buttons and
    // what each period means are shared with the leaderboard (components/PeriodPicker).
    const [period, setPeriod] = useState(periodOf('today'));
    const [report, setReport] = useState(EMPTY_REPORT);

    // The period as two pieces of text. Text (not Date objects) so the effect below only
    // runs again when the period really changes, not on every redraw.
    const { from, to } = rangeOf(period);

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

                <PeriodPicker period={period} onChange={setPeriod} />

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