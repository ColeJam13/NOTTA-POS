import './NavBar.css';

function NavBar({ currentView, setCurrentView }) {
  return (
    <div className="navbar">
      <div className="navbar-top">
        <h1 className="navbar-logo" onClick={() => setCurrentView('home')} style={{cursor: 'pointer'}}>NOTA-POS</h1>
        
        <div className="navbar-search">
          <input type="text" placeholder="Find table, order, item..." />
        </div>
        
        <div className="navbar-right">
          <button className="notifications-btn">
            🔔 <span className="notification-badge">2</span>
          </button>
          <div className="user-info">
            <div className="user-avatar">CJ</div>
            <div className="user-details">
              <span className="user-name">Cole J</span>
              <span className="user-role">Manager</span>
            </div>
          </div>
        </div>
      </div>
      
      <div className="navbar-bottom">
        <button className="nav-btn" onClick={() => setCurrentView('floorMap')}>FLOOR MAP</button>
        <button className="nav-btn" onClick={() => setCurrentView('activeTables')}>TABLES</button>
        <button className={`nav-btn ${currentView === 'activeOrders' ? 'active' : ''}`} onClick={() => setCurrentView('activeOrders')}>ACTIVE ORDERS</button>
        <button className={`nav-btn ${currentView === 'createOrder' ? 'active' : ''}`} onClick={() => setCurrentView('createOrder')}>CREATE ORDER</button>
        <button className="nav-btn" onClick={() => setCurrentView('financials')}>FINANCIALS</button>
        <button className="nav-btn" onClick={() => setCurrentView('challenges')}>CHALLENGES</button>
      </div>
    </div>
  );
}

export default NavBar;