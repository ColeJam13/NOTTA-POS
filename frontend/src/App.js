import { useState, useEffect, useCallback } from 'react';
import Home from './views/Home';
import CreateOrder from './views/CreateOrder';
import ActiveOrders from './views/ActiveOrders';
import FloorMap from './views/FloorMap';
import Tables from './views/Tables';
import QuickOrders from './views/QuickOrders';
import Financials from './views/Financials';
import Challenges from './views/Challenges';
import EightySixBoard from './views/EightySixBoard';
import SignIn from './views/SignIn';
import Staff from './views/Staff';
import { StaffContext } from './context/StaffContext';
import { apiFetch, setSessionToken, setOnSessionEnded } from './services/api';
import { canOpenScreen } from './utils/roles';
import './App.css';

// Where the signed-in person's token is kept so a page reload does not sign them out.
// sessionStorage belongs to one browser tab and is emptied when the tab is closed.
const TOKEN_STORAGE_KEY = 'notta.staffToken';

const readStoredToken = () => {
  try {
    return window.sessionStorage.getItem(TOKEN_STORAGE_KEY);
  } catch (error) {
    return null;                    // Storage is switched off in this browser - sign-in still works, it just does not survive a reload
  }
};

const storeToken = (token) => {
  try {
    if (token) {
      window.sessionStorage.setItem(TOKEN_STORAGE_KEY, token);
    } else {
      window.sessionStorage.removeItem(TOKEN_STORAGE_KEY);
    }
  } catch (error) {
    // Nothing to do: see readStoredToken
  }
};

function App() {
  const [currentView, setCurrentViewState] = useState('home');
  const [selectedTable, setSelectedTable] = useState(null);

  // Who is signed in: { staffId, name, role }, or null when nobody is
  const [staff, setStaff] = useState(null);

  // True while a stored token is being checked after a page reload, so the PIN
  // screen does not flash up for someone who is still signed in
  const [checkingSession, setCheckingSession] = useState(() => readStoredToken() !== null);

  // Forget who was signed in and go back to the PIN screen
  const clearSession = useCallback(() => {
    setSessionToken(null);
    storeToken(null);
    setStaff(null);
    setSelectedTable(null);
    setCurrentViewState('home');
  }, []);

  // SIGN OUT in the nav bar: tell the backend to end the session, then forget it here
  const signOut = useCallback(() => {
    apiFetch('/api/staff/sign-out', { method: 'POST' }).catch(() => {});      // Signing out here must not depend on the backend answering
    clearSession();
  }, [clearSession]);

  // A PIN was accepted
  const handleSignedIn = useCallback(({ staff: signedInStaff, token }) => {
    setSessionToken(token);
    storeToken(token);
    setStaff(signedInStaff);
    setCurrentViewState('home');
  }, []);

  // When the app loads: if a token was stored (the page was reloaded), ask the backend
  // whether it is still good. Also say what to do if the backend ends a session later.
  useEffect(() => {
    setOnSessionEnded(clearSession);

    const storedToken = readStoredToken();
    if (!storedToken) return;

    setSessionToken(storedToken);
    apiFetch('/api/staff/me')
      .then(response => (response.ok ? response.json() : null))
      .then(currentStaff => {
        if (currentStaff) {
          setStaff(currentStaff);
        } else {
          clearSession();
        }
      })
      .catch(() => clearSession())
      .finally(() => setCheckingSession(false));
  }, [clearSession]);

  // Counts how many times the order screen has been opened. It is used as the
  // screen's "key": when a key changes, React throws the old copy of the component
  // away and builds a fresh one, with fresh state.
  const [orderScreenVisit, setOrderScreenVisit] = useState(0);

  // Every screen changes view through this. Opening the order screen always starts
  // it fresh - without this, tapping CREATE ORDER while a table's order was on screen
  // kept that table's items and order under a "New Quick Order" heading, and the next
  // items rung in went onto the old table's check.
  const setCurrentView = (view) => {
    // A screen this person's role may not open is simply not opened. (The nav bar does
    // not offer it either; this covers every other way of getting there, such as a host
    // tapping a table on the Floor Map, which would open the order screen.)
    if (!canOpenScreen(staff?.role, view)) return;

    if (view === 'createOrder') {
      setOrderScreenVisit(visit => visit + 1);
    }
    setCurrentViewState(view);
  };

  // Nothing is shown until someone signs in
  if (checkingSession) {
    return null;
  }

  if (!staff) {
    return <SignIn onSignedIn={handleSignedIn} />;
  }

  return (
    // Every screen below can read who is signed in with useStaff()
    <StaffContext.Provider value={{ staff, signOut }}>
      {renderView()}
    </StaffContext.Provider>
  );

  // The screen for the current view
  function renderView() {

  if (currentView === 'createOrder') {
    return <CreateOrder key={orderScreenVisit} setCurrentView={setCurrentView} selectedTable={selectedTable} setSelectedTable={setSelectedTable} />;  
  }

  if (currentView === 'activeOrders') {
    return <ActiveOrders setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />;  
  }

  if (currentView === 'floorMap') {
    return <FloorMap setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />;
  }

  if (currentView === 'activeTables') {
    return <Tables setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />;
  }

  if (currentView === 'quickOrders') {
    return <QuickOrders setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />;
  }

  if (currentView === 'financials') {
    return <Financials setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />;  
  }

  if (currentView === 'eightySix') {
    return <EightySixBoard setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />;
  }

  if (currentView === 'challenges') {
    return <Challenges setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />;  
  }

  if (currentView === 'staff') {
    return <Staff setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />;
  }

  return <Home setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />;  
  }
}

export default App;