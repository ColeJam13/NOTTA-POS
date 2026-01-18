import { useState } from 'react';
import Home from './views/Home';
import CreateOrder from './views/CreateOrder';
import ActiveOrders from './views/ActiveOrders';
import FloorMap from './views/FloorMap';
import Tables from './views/Tables';
import Financials from './views/Financials';
import Challenges from './views/Challenges';
import './App.css';

function App() {
  const [currentView, setCurrentView] = useState('home');
  const [selectedTable, setSelectedTable] = useState(null);

  if (currentView === 'createOrder') {
    return <CreateOrder setCurrentView={setCurrentView} selectedTable={selectedTable} />;
  }

  if (currentView === 'activeOrders') {
    return <ActiveOrders setCurrentView={setCurrentView} />;
  }

  if (currentView === 'floorMap') {
    return <FloorMap setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />;
  }

  if (currentView === 'activeTables') {
    return <Tables setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />;
  }

  if (currentView === 'financials') {
  return <Financials setCurrentView={setCurrentView} />;
  }

  if (currentView === 'challenges') {
  return <Challenges setCurrentView={setCurrentView} />;
  }

  return <Home setCurrentView={setCurrentView} />;
}

export default App;