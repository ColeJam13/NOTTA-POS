import logo from '../../assets/NOTTA LOGO.png';
import { useStaff } from '../../context/StaffContext';
import { canOpenScreen, ROLE_LABELS } from '../../utils/roles';
import './Home.css';

// The landing buttons, in reading order
const HOME_BUTTONS = [
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

function Home({ setCurrentView }) {
  const { staff, signOut } = useStaff();          // Who is signed in

  return (
    <div className="home-page">
      {/* Home has no nav bar, so it says who is signed in and offers SIGN OUT itself */}
      {staff && (
        <div className="home-signed-in">
          <span>Signed in as <strong>{staff.name}</strong> ({ROLE_LABELS[staff.role] || staff.role})</span>
          <button className="home-sign-out" onClick={signOut}>SIGN OUT</button>
        </div>
      )}

      <div className="logo-container">
        <img 
          src={logo} 
          alt="NOTTA-POS Logo"
          className="home-logo"
        />
      </div>
      
      <div className="home-buttons">
        {/* Only the screens this person's role may open are offered (see utils/roles.js) */}
        {HOME_BUTTONS.filter(button => canOpenScreen(staff?.role, button.view)).map(button => (
          <button key={button.view} className="landing-btn" onClick={() => setCurrentView(button.view)}>
            {button.label}
          </button>
        ))}
      </div>
    </div>
  );
}

export default Home;