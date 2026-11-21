import './NavBar.css';
import { AlertCircle, LogOut, Settings as SettingsIcon } from 'lucide-react';
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
          {/* The gear opens Settings. Only a manager is offered it (utils/roles.js); it sits up
              here because the row of screen buttons below is already full. */}
          {staff && canOpenScreen(staff.role, 'settings') && (
            <button
              className={`settings-btn ${currentView === 'settings' ? 'active' : ''}`}
              aria-label="Settings"
              title="Settings"
              onClick={() => goTo('settings')}
            >
              <SettingsIcon size={20} />
            </button>
          )}
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
        {/* Only the screens this person's role may open are offered (see utils/roles.js) */}
        {NAV_BUTTONS.filter(button => canOpenScreen(staff?.role, button.view)).map(button => (
          <button
            key={button.view}
            className={`nav-btn ${currentView === button.view ? 'active' : ''}`}
            onClick={() => goTo(button.view)}
          >
            {button.label}
          </button>
        ))}
      </div>
    </div>
  );
}

export default NavBar;
