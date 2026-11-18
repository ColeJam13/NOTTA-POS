import './NavBar.css';
import { AlertCircle } from 'lucide-react';

/**
 * The bar at the top of every screen.
 *
 * confirmLeave is optional. A screen that can hold unsaved work (the order
 * screen) passes a function that returns false to keep the user where they
 * are. It is asked before every navigation from this bar.
 */
function NavBar({ currentView, setCurrentView, setSelectedTable, confirmLeave }) {  // Add setSelectedTable prop

  // Every button in the bar goes through here, so the "unsaved work" question is
  // asked in one place - and asked BEFORE anything about the current screen changes.
  const goTo = (view) => {
    if (confirmLeave && !confirmLeave()) return;

    if (view === 'createOrder') {
      setSelectedTable(null);  // Clear selected table
    }
    setCurrentView(view);
  };

  return (
    <div className="navbar">
      <div className="navbar-top">
        <h1 className="navbar-logo" onClick={() => goTo('home')} style={{cursor: 'pointer'}}>NOTTA-POS</h1>
        
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
        <button className={`nav-btn ${currentView === 'floorMap' ? 'active' : ''}`} onClick={() => goTo('floorMap')}>FLOOR MAP</button>
        <button className={`nav-btn ${currentView === 'activeTables' ? 'active' : ''}`} onClick={() => goTo('activeTables')}>TABLES</button>
        <button className={`nav-btn ${currentView === 'quickOrders' ? 'active' : ''}`} onClick={() => goTo('quickOrders')}>QUICK ORDERS</button>
        <button className={`nav-btn ${currentView === 'activeOrders' ? 'active' : ''}`} onClick={() => goTo('activeOrders')}>ACTIVE ORDERS</button>
        <button className={`nav-btn ${currentView === 'createOrder' ? 'active' : ''}`} onClick={() => goTo('createOrder')}>CREATE ORDER</button>
        <button className={`nav-btn ${currentView === 'eightySix' ? 'active' : ''}`} onClick={() => goTo('eightySix')}>86 BOARD</button>
        <button className={`nav-btn ${currentView === 'financials' ? 'active' : ''}`} onClick={() => goTo('financials')}>FINANCIALS</button>
        <button className={`nav-btn ${currentView === 'challenges' ? 'active' : ''}`} onClick={() => goTo('challenges')}>CHALLENGES</button>
      </div>
    </div>
  );
}

export default NavBar;
