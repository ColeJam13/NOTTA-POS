import './Home.css';

function Home({ setCurrentView }) {
  return (
    <div className="landing-page">
      <h1 className="landing-logo">NOTA-POS</h1>
      <div className="landing-buttons">
        <button className="landing-btn" onClick={() => setCurrentView('floorMap')}>
          FLOOR MAP
        </button>
        <button className="landing-btn" onClick={() => setCurrentView('activeTables')}>
          ACTIVE TABLES
        </button>
        <button className="landing-btn" onClick={() => setCurrentView('createOrder')}>
          CREATE ORDER
        </button>
        <button className="landing-btn" onClick={() => setCurrentView('activeOrders')}>
          ACTIVE ORDERS
        </button>
        <button className="landing-btn" onClick={() => setCurrentView('financials')}>
          FINANCIALS
        </button>
        <button className="landing-btn" onClick={() => setCurrentView('challenges')}>
          CHALLENGES
        </button>
      </div>
    </div>
  );
}

export default Home;