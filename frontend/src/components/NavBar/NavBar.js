import './NavBar.css';
import { AlertCircle, LogOut } from 'lucide-react';
import { useStaff } from '../../context/StaffContext';
import { canOpenScreen, initialsOf, ROLE_LABELS } from '../../utils/roles';

/**
 * The bar at the top of every screen.
 *
 * confirmLeave is optional. A screen that can hold unsaved work (the order
 * screen) passes a function that returns false to keep the user where they
 * are. It is asked before every navigation from this bar.
 */
// The buttons in the bar, left to right
const NAV_BUTTONS = [
  { view: 'floorMap', label: 'FLOOR MAP' },
  { view: 'activeTables', label: 'TABLES' },
  { view: 'quickOrders', label: 'QUICK ORDERS' },
  { view: 'activeOrders', label: 'ACTIVE ORDERS' },
  { view: 'createOrder', label: 'CREATE ORDER' },
  { view: 'eightySix', label: '86 BOARD' },
  { view: 'financials', label: 'FINANCIALS' },
  { view: 'challenges', label: 'CHALLENGES' },
  { view: 'staff', label: 'STAFF' }
];

function NavBar({ currentView, setCurrentView, setSelectedTable, confirmLeave }) {  // Add setSelectedTable prop
  const { staff, signOut } = useStaff();          // Who is signed in (null when this bar is drawn on its own in a test)

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
          {/* Who is signed in, and the way to sign out (so the next person can type their own PIN) */}
          {staff && (
            <div className="user-info">
              <div className="user-avatar">{initialsOf(staff.name)}</div>
              <div className="user-details">
                <span className="user-name">{staff.name}</span>
                <span className="user-role">{ROLE_LABELS[staff.role] || staff.role}</span>
              </div>
              <button className="sign-out-btn" onClick={() => { if (!confirmLeave || confirmLeave()) signOut(); }}>
                <LogOut size={16} /> SIGN OUT
              </button>
            </div>
          )}
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
