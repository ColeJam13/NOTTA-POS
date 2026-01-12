import { useState } from 'react';
import CreateOrder from './CreateOrder';
import ActiveOrders from './ActiveOrders';
import FloorMap from './FloorMap';
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

  return (
    <div className="landing-page">
      <h1 className="landing-logo">NOTA-POS</h1>

      <div className="landing-buttons">
                <button
          className="landing-btn"
          onClick={() => setCurrentView('createOrder')}
        >
          FLOOR MAP
        </button>
        <button
          className="landing-btn"
          onClick={() => setCurrentView('createOrder')}
        >
          CREATE ORDER
        </button>
        <button
          className="landing-btn"
          onClick={() => setCurrentView('activeOrders')}
        >
          ACTIVE ORDERS
        </button>
      </div>
    </div>
  );
}

export default App;