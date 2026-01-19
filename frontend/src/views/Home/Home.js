import logo from '../../assets/nota-logo.png';
import './Home.css';

function Home({ setCurrentView }) {
  return (
    <div className="home-page">
      <div className="logo-container">
        <img 
          src={logo} 
          alt="NOTA-POS Logo" 
          className="home-logo"
        />
      </div>
      
      <div className="home-buttons">
        <button className="landing-btn" onClick={() => setCurrentView('floorMap')}>
          FLOOR MAP
        </button>
        <button className="landing-btn" onClick={() => setCurrentView('activeTables')}>
          TABLES
        </button>
        <button className="landing-btn" onClick={() => setCurrentView('activeOrders')}>
          ACTIVE ORDERS
        </button>
        <button className="landing-btn" onClick={() => setCurrentView('createOrder')}>
          CREATE ORDER
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