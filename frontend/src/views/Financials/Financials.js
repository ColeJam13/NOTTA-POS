import NavBar from '../../components/NavBar';
import './Financials.css';

function Financials({ setCurrentView }) {
  // Mock data - will be replaced with API call later
  const mockData = {
    shiftStart: '4:30 PM',
    shiftDuration: '4h 17m',
    totalSales: 847.50,
    totalTips: 152.75,
    ordersClosed: 18,
    avgCheckSize: 47.08,
    avgTipPercent: 18.0,
    tablesServed: 22,
    paymentBreakdown: {
      creditCard: { amount: 620.25, percent: 73 },
      cash: { amount: 227.25, percent: 27 },
      giftCard: { amount: 0, percent: 0 }
    },
    recentTransactions: [
      { table: 'W5', time: '8 mins ago', total: 73.44, tip: 13.22 },
      { table: 'C3', time: '15 mins ago', total: 42.80, tip: 8.56 },
      { table: 'A2', time: '23 mins ago', total: 95.30, tip: 19.06 },
      { table: 'B4', time: '31 mins ago', total: 68.75, tip: 12.38 },
      { table: 'W2', time: '45 mins ago', total: 54.20, tip: 9.76 }
    ]
  };

  return (
    <div className="page-with-nav">
      <NavBar currentView="financials" setCurrentView={setCurrentView} />
      <div className="financials-page">
        <h2>FINANCIALS</h2>
        
        <div className="shift-info">
          Shift Start: {mockData.shiftStart} • Duration: {mockData.shiftDuration}
        </div>

        <div className="stats-grid">
          <div className="stat-card">
            <div className="stat-label">Total Sales</div>
            <div className="stat-value large">${mockData.totalSales.toFixed(2)}</div>
          </div>
          
          <div className="stat-card">
            <div className="stat-label">Total Tips</div>
            <div className="stat-value large">${mockData.totalTips.toFixed(2)}</div>
          </div>
          
          <div className="stat-card">
            <div className="stat-label">Orders Closed</div>
            <div className="stat-value large">{mockData.ordersClosed}</div>
          </div>
          
          <div className="stat-card">
            <div className="stat-label">Avg Check Size</div>
            <div className="stat-value">${mockData.avgCheckSize.toFixed(2)}</div>
          </div>
          
          <div className="stat-card">
            <div className="stat-label">Avg Tip %</div>
            <div className="stat-value">{mockData.avgTipPercent.toFixed(1)}%</div>
          </div>
          
          <div className="stat-card">
            <div className="stat-label">Tables Served</div>
            <div className="stat-value">{mockData.tablesServed}</div>
          </div>
        </div>

        <div className="payment-breakdown">
          <h3>Payment Breakdown</h3>
          
          <div className="payment-row">
            <div className="payment-icon">💳</div>
            <div className="payment-label">Credit Card:</div>
            <div className="payment-amount">${mockData.paymentBreakdown.creditCard.amount.toFixed(2)}</div>
            <div className="payment-bar-container">
              <div className="payment-bar" style={{ width: `${mockData.paymentBreakdown.creditCard.percent}%` }}>
                <span className="payment-percent">{mockData.paymentBreakdown.creditCard.percent}%</span>
              </div>
            </div>
          </div>
          
          <div className="payment-row">
            <div className="payment-icon">💵</div>
            <div className="payment-label">Cash:</div>
            <div className="payment-amount">${mockData.paymentBreakdown.cash.amount.toFixed(2)}</div>
            <div className="payment-bar-container">
              <div className="payment-bar" style={{ width: `${mockData.paymentBreakdown.cash.percent}%` }}>
                <span className="payment-percent">{mockData.paymentBreakdown.cash.percent}%</span>
              </div>
            </div>
          </div>
          
          <div className="payment-row">
            <div className="payment-icon">🎁</div>
            <div className="payment-label">Gift Card:</div>
            <div className="payment-amount">${mockData.paymentBreakdown.giftCard.amount.toFixed(2)}</div>
            <div className="payment-bar-container">
              <div className="payment-bar" style={{ width: `${mockData.paymentBreakdown.giftCard.percent}%` }}>
                <span className="payment-percent">{mockData.paymentBreakdown.giftCard.percent}%</span>
              </div>
            </div>
          </div>
        </div>

        <div className="recent-transactions">
          <h3>Recent Transactions</h3>
          <div className="transaction-list">
            {mockData.recentTransactions.map((transaction, index) => (
              <div key={index} className="transaction-item">
                <div className="transaction-info">
                  <div className="transaction-table">Table {transaction.table}</div>
                  <div className="transaction-time">{transaction.time}</div>
                </div>
                <div className="transaction-amounts">
                  <div className="transaction-total">${transaction.total.toFixed(2)}</div>
                  <div className="transaction-tip">Tip: ${transaction.tip.toFixed(2)}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

export default Financials;