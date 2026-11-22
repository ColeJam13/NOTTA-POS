import { useEffect, useRef } from 'react';
import './NavBar.css';
import { LogOut, Settings as SettingsIcon } from 'lucide-react';
import { useStaff } from '../../context/StaffContext';
import { canDo, canOpenScreen, initialsOf, ROLE_LABELS } from '../../utils/roles';
import Search from '../Search';
import NotificationBell from '../Notifications';

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

  // On a phone the row of screen buttons is a strip that scrolls sideways (NavBar.css),
  // and every screen draws its own bar, so the strip would start at its left end each
  // time. This brings the button of the screen that is open into view. On a wide screen
  // every button is already visible and it does nothing.
  const buttons = useRef(null);
  useEffect(() => {
    const active = buttons.current && buttons.current.querySelector('.nav-btn.active');
    // (A test browser has no scrollIntoView; there is nothing to scroll there either.)
    if (active && typeof active.scrollIntoView === 'function') {
      active.scrollIntoView({ block: 'nearest', inline: 'center' });
    }
  }, [currentView]);

  // Every button in the bar goes through here, so the "unsaved work" question is
  // asked in one place - and asked BEFORE anything about the current screen changes.
  const goTo = (view) => {
    if (confirmLeave && !confirmLeave()) return;

    if (view === 'createOrder') {
      setSelectedTable(null);  // Clear selected table
    }
    setCurrentView(view);
  };

  // A table (or an open check's table) chosen in the search box: open its order
  // screen. The same "unsaved work" question as every button in the bar, asked first.
  // Returns false when the person chose to stay, so the box keeps what was typed.
  const openTable = (table) => {
    if (confirmLeave && !confirmLeave()) return false;

    setSelectedTable(table);
    setCurrentView('createOrder');
    return true;
  };

  // The search box goes to a screen the way a button does, and says whether it went
  const openScreen = (view) => {
    if (confirmLeave && !confirmLeave()) return false;

    setCurrentView(view);
    return true;
  };

  return (
    <div className="navbar">
      <div className="navbar-top">
        <h1 className="navbar-logo" onClick={() => goTo('home')} style={{cursor: 'pointer'}}>Notta-Pos</h1>
        
        {/* Find a table, a check, or a dish, and go straight to it (components/Search).
            Every role may search (utils/roles.js); what a result does depends on the role. */}
        {canDo(staff?.role, 'search')
          ? <Search onOpenTable={openTable} onOpenScreen={openScreen} />
          : <div className="navbar-search" />}
        
        <div className="navbar-right">
          {/* The bell: how many notifications this person has, and the panel that lists them
              (components/Notifications). The number comes from the backend. */}
          <NotificationBell />
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
              {/* On a phone only the icon is drawn (NavBar.css), so the button says what it is
                  in aria-label too */}
              <button className="sign-out-btn" aria-label="SIGN OUT" onClick={() => { if (!confirmLeave || confirmLeave()) signOut(); }}>
                <LogOut size={16} aria-hidden="true" /> <span className="sign-out-label">SIGN OUT</span>
              </button>
            </div>
          )}
        </div>
      </div>
      
      <div className="navbar-bottom" ref={buttons}>
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
