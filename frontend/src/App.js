import { useState } from 'react';
import Home from './views/Home';
import CreateOrder from './views/CreateOrder';
import ActiveOrders from './views/ActiveOrders';
import FloorMap from './views/FloorMap';
import Tables from './views/Tables';
import QuickOrders from './views/QuickOrders';
import Financials from './views/Financials';
import Challenges from './views/Challenges';
import EightySixBoard from './views/EightySixBoard';
import './App.css';

function App() {
  const [currentView, setCurrentViewState] = useState('home');
  const [selectedTable, setSelectedTable] = useState(null);

  // Counts how many times the order screen has been opened. It is used as the
  // screen's "key": when a key changes, React throws the old copy of the component
  // away and builds a fresh one, with fresh state.
  const [orderScreenVisit, setOrderScreenVisit] = useState(0);

  // Every screen changes view through this. Opening the order screen always starts
  // it fresh - without this, tapping CREATE ORDER while a table's order was on screen
  // kept that table's items and order under a "New Quick Order" heading, and the next
  // items rung in went onto the old table's check.
  const setCurrentView = (view) => {
    if (view === 'createOrder') {
      setOrderScreenVisit(visit => visit + 1);
    }
    setCurrentViewState(view);
  };

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

  return <Home setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />;  
}

export default App;