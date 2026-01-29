import { useState, useEffect } from 'react';
import NavBar from '../../components/NavBar/NavBar';
import { formatTableName, formatTimeAgo, formatPaymentMethod } from '../../utils/formatters';
import './Financials.css';

function Financials({ setCurrentView, setSelectedTable }) {
    const [timePeriod, setTimePeriod] = useState('today');
    const [customStartDate, setCustomStartDate] = useState('');
    const [customEndDate, setCustomEndDate] = useState('');
    const [orders, setOrders] = useState([]);
    const [payments, setPayments] = useState([]);
    const [orderItems, setOrderItems] = useState([]);
    const [tables, setTables] = useState([]);

    useEffect(() => {                                                       // Load orders, payments, order items, and tables on mount
        fetch('http://localhost:8080/api/orders')
            .then(response => response.json())
            .then(data => setOrders(data))
            .catch(error => console.error('Error fetching orders:', error));

        fetch('http://localhost:8080/api/payments')
            .then(response => response.json())
            .then(data => setPayments(data))
            .catch(error => console.error('Error fetching payments:', error));

        fetch('http://localhost:8080/api/order-items')
            .then(response => response.json())
            .then(data => setOrderItems(data))
            .catch(error => console.error('Error fetching order items:', error));

        fetch('http://localhost:8080/api/tables')
            .then(response => response.json())
            .then(data => setTables(data))
            .catch(error => console.error('Error fetching tables:', error));
    }, []);

                                                                            // Get date range based on selected period
    const getDateRange = () => {
        const now = new Date();
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
                endDate = new Date();
                break;
            case 'month':
                startDate = new Date(now.getFullYear(), now.getMonth(), 1);
                endDate = new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59, 999);
                break;
            case 'custom':
                startDate = customStartDate ? new Date(customStartDate) : new Date(0);
                endDate = customEndDate ? new Date(customEndDate) : new Date();
                break;
            default:
                startDate = new Date(now.setHours(0, 0, 0, 0));
                endDate = new Date(now.setHours(23, 59, 59, 999));
        }

        return { startDate, endDate };
    };

                                                                            // Filter closed orders by date range
    const getFilteredOrders = () => {
        const { startDate, endDate } = getDateRange();
        
        return orders.filter(order => {
            if (order.status !== 'closed' || !order.closedAt) return false;
            const closedDate = new Date(order.closedAt);
            return closedDate >= startDate && closedDate <= endDate;
        });
    };

                                                                            // Calculate financial metrics
    const calculateMetrics = () => {
        const filteredOrders = getFilteredOrders();
        
        if (filteredOrders.length === 0) {
            return {
                totalRevenue: 0,
                totalTips: 0,
                orderCount: 0,
                avgOrderValue: 0,
                avgTipPercent: 0,
                paymentBreakdown: {},
                recentTransactions: []
            };
        }

                                                                            // Calculate revenue and tips
        let totalRevenue = 0;
        let totalTips = 0;
        const paymentMethodTotals = {};

        filteredOrders.forEach(order => {
            const payment = payments.find(p => p.orderId === order.orderId);
            if (payment) {
                const tipAmount = parseFloat(payment.tipAmount) || 0;
                const paymentAmount = parseFloat(payment.amount) || 0;
                
                totalTips += tipAmount;
                
                                                                            // Track payment method totals (using payment.amount which is the subtotal)
                const method = payment.paymentMethod || 'unknown';
                if (!paymentMethodTotals[method]) {
                    paymentMethodTotals[method] = 0;
                }
                paymentMethodTotals[method] += paymentAmount;
            }

                                                                            // Calculate order subtotal from items
            const items = orderItems.filter(item => item.orderId === order.orderId);
            const orderTotal = items.reduce((sum, item) => sum + item.price, 0);
            totalRevenue += orderTotal;
        });

                                                                            // Calculate total of all payment amounts for percentage calculation
        const totalPaymentAmount = Object.values(paymentMethodTotals).reduce((sum, amount) => sum + amount, 0);

                                                                            // Calculate payment breakdown percentages (based on total payment amounts, not order items)
        const paymentBreakdown = {};
        Object.keys(paymentMethodTotals).forEach(method => {
            paymentBreakdown[method] = {
                amount: paymentMethodTotals[method],
                percent: totalPaymentAmount > 0 ? (paymentMethodTotals[method] / totalPaymentAmount * 100).toFixed(1) : 0
            };
        });

                                                                            // Get recent transactions (last 10)
        const recentTransactions = filteredOrders
            .sort((a, b) => new Date(b.closedAt) - new Date(a.closedAt))
            .slice(0, 10)
            .map(order => {
                const payment = payments.find(p => p.orderId === order.orderId);
                const items = orderItems.filter(item => item.orderId === order.orderId);
                const orderTotal = items.reduce((sum, item) => sum + item.price, 0);
                const table = tables.find(t => t.tableId === order.tableId);
                
                return {
                    orderId: order.orderId,
                    table: table,
                    serverName: order.serverName || 'N/A',
                    closedAt: order.closedAt,
                    total: orderTotal,
                    tip: parseFloat(payment?.tipAmount) || 0,
                    paymentMethod: payment?.paymentMethod || 'Unknown'
                };
            });

        const avgOrderValue = totalRevenue / filteredOrders.length;
        const avgTipPercent = totalRevenue > 0 ? (totalTips / totalRevenue * 100) : 0;

        return {
            totalRevenue,
            totalTips,
            orderCount: filteredOrders.length,
            avgOrderValue,
            avgTipPercent,
            paymentBreakdown,
            recentTransactions
        };
    };

    const metrics = calculateMetrics();

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
                        <div className="stat-value">${(metrics.totalRevenue + metrics.totalTips).toFixed(2)}</div>
                    </div>
                </div>

              <div className="payment-breakdown">
                  <h3>Payment Method Breakdown</h3>
                  {Object.keys(metrics.paymentBreakdown).length > 0 ? (
                      // Sort payment methods in consistent order: CASH, CREDIT CARD, DEBIT CARD, GIFT CARD
                      Object.keys(metrics.paymentBreakdown)
                          .sort((a, b) => {
                              const order = { 'cash': 1, 'credit_card': 2, 'debit_card': 3, 'gift_card': 4 };
                              return (order[a] || 99) - (order[b] || 99);
                          })
                          .map(method => (
                          <div key={method} className="payment-row">
                              <div className="payment-label">{formatPaymentMethod(method)}:</div>
                              <div className="payment-amount">${metrics.paymentBreakdown[method].amount.toFixed(2)}</div>
                              <div className="payment-bar-container">
                                  <div className="payment-bar" style={{ width: `${metrics.paymentBreakdown[method].percent}%` }}>
                                      <span className="payment-percent">{metrics.paymentBreakdown[method].percent}%</span>
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
                                        <div className="transaction-table">{formatTableName(transaction.table)}</div>
                                        <div className="transaction-server">Server: {transaction.serverName}</div>
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