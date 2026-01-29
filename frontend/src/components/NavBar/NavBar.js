import './NavBar.css';
import { AlertCircle } from 'lucide-react';

function NavBar({ currentView, setCurrentView, setSelectedTable }) {  // Add setSelectedTable prop
  return (
    <div className="navbar">
      <div className="navbar-top">
        <h1 className="navbar-logo" onClick={() => setCurrentView('home')} style={{cursor: 'pointer'}}>NOTTA-POS</h1>
        
        <div className="navbar-search">
          <input type="text" placeholder="Find table, order, item..." />
        </div>
        
        <div className="navbar-right">
          <button className="notifications-btn">
            <AlertCircle size={20} /> <span className="notification-badge">2</span>
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
        <button className={`nav-btn ${currentView === 'floorMap' ? 'active' : ''}`} onClick={() => setCurrentView('floorMap')}>FLOOR MAP</button>
        <button className={`nav-btn ${currentView === 'activeTables' ? 'active' : ''}`} onClick={() => setCurrentView('activeTables')}>TABLES</button>
        <button className={`nav-btn ${currentView === 'quickOrders' ? 'active' : ''}`} onClick={() => setCurrentView('quickOrders')}>QUICK ORDERS</button>
        <button className={`nav-btn ${currentView === 'activeOrders' ? 'active' : ''}`} onClick={() => setCurrentView('activeOrders')}>ACTIVE ORDERS</button>
        <button className={`nav-btn ${currentView === 'createOrder' ? 'active' : ''}`} onClick={() => {
          setSelectedTable(null);  // Clear selected table
          setCurrentView('createOrder');
        }}>CREATE ORDER</button>
        <button className={`nav-btn ${currentView === 'financials' ? 'active' : ''}`} onClick={() => setCurrentView('financials')}>FINANCIALS</button>
        <button className={`nav-btn ${currentView === 'challenges' ? 'active' : ''}`} onClick={() => setCurrentView('challenges')}>CHALLENGES</button>
      </div>
    </div>
  );
}

export default NavBar;