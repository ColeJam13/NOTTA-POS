import { useState } from 'react';
import CreateOrder from './CreateOrder';
import ActiveOrders from './ActiveOrders';
import './App.css';

function App() {
  const [currentView, setCurrentView] = useState('home');

  if (currentView === 'createOrder') {
    return <CreateOrder setCurrentView={setCurrentView} />;
  }

  if (currentView === 'activeOrders') {
    return <ActiveOrders setCurrentView={setCurrentView} />;
  }

  return (
    <div className="landing-page">
      <h1 className="landing-logo">NOTA-POS</h1>

      <div className="landing-buttons">
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