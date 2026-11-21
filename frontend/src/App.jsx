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
import ChangePin from './views/ChangePin';
import Staff from './views/Staff';
import Settings from './views/Settings';
import Gallery from './views/Gallery';
import TemplatePreviewBar from './components/TemplatePreviewBar';
import { StaffContext } from './context/StaffContext';
import { apiFetch, getJson, setSessionToken, setOnSessionEnded } from './services/api';
import { showChosenTemplate, stopPreview } from './templates';
import { canOpenScreen } from './utils/roles';
import './App.css';

// How often a signed-in terminal asks the backend which template the restaurant uses,
// in milliseconds. This is how long a manager's change takes to reach the other terminals.
const TEMPLATE_REFRESH_MS = 15000;

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

  // Who is signed in: { staffId, name, role, mustChangePin }, or null when nobody is
  const [staff, setStaff] = useState(null);

  // True while a stored token is being checked after a page reload, so the PIN
  // screen does not flash up for someone who is still signed in
  const [checkingSession, setCheckingSession] = useState(() => readStoredToken() !== null);

  // Forget who was signed in and go back to the PIN screen
  const clearSession = useCallback(() => {
    stopPreview();                  // A template a manager was trying out must not be left on for the next person
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

  // THE TEMPLATE (the look of every screen). The restaurant has one, chosen by a manager
  // and kept on the backend. Ask for it as soon as someone is signed in, and again every
  // few seconds, so a change made on one terminal reaches all the others without anyone
  // reloading a page. The answer goes to templates/index.js, which restyles the page.
  // Someone who must first choose a new PIN is refused everything else by the backend,
  // so nothing is asked until that is done.
  const signedInAndReady = staff !== null && !staff.mustChangePin;
  useEffect(() => {
    if (!signedInAndReady) return undefined;

    const loadTemplate = () => {
      getJson('/api/settings')
        .then(settings => showChosenTemplate(settings.template))
        .catch(() => {});             // No answer: keep the look the screen has. It is asked again shortly.
    };

    loadTemplate();
    const interval = setInterval(loadTemplate, TEMPLATE_REFRESH_MS);
    return () => clearInterval(interval);
  }, [signedInAndReady]);

  // Counts how many times the order screen has been opened. It is used as the
  // screen's "key": when a key changes, React throws the old copy of the component
  // away and builds a fresh one, with fresh state.
  const [orderScreenVisit, setOrderScreenVisit] = useState(0);

  // Every screen changes view through this. Opening the order screen always starts
  // it fresh - without this, tapping CREATE ORDER while a table's order was on screen
  // kept that table's items and order under a "New Quick Order" heading, and the next
  // items rung in went onto the old table's check.
  const setCurrentView = (view) => {
    // A screen this person's role may not open is simply not opened. (The nav bar and the
    // Home screen do not offer it either; this covers any other way of getting there, such
    // as a button on one screen that leads to another.)
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

  // Signed in with the starter PIN: the only screen is "choose a new PIN". The backend
  // refuses everything else from this person too, so no other screen would work anyway.
  // When the PIN is changed the backend hands the person back without the flag, and the
  // app opens as usual.
  if (staff.mustChangePin) {
    return <ChangePin staff={staff} onPinChanged={setStaff} onSignOut={signOut} />;
  }

  return (
    // Every screen below can read who is signed in with useStaff()
    <StaffContext.Provider value={{ staff, signOut }}>
      {/* Draws nothing unless a manager is trying a template out; then it sits above
          whichever screen is open */}
      <TemplatePreviewBar />
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

  if (currentView === 'settings') {
    return <Settings setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />;
  }

  if (currentView === 'gallery') {
    return <Gallery setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />;
  }

  return <Home setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />;  
  }
}

export default App;