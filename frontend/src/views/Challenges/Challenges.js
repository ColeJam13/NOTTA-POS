import NavBar from '../../components/NavBar';
import { Medal } from 'lucide-react';
import './Challenges.css';

function Challenges({ setCurrentView, setSelectedTable }) {
  // Mock data - will be replaced with API calls later
  const mockData = {
    activeChallenges: [
      {
        id: 1,
        name: 'APPETIZER CHAMPION',
        description: 'Sell 15 appetizers today',
        progress: 12,
        target: 15,
        reward: 20,
        status: 'in_progress',
        timeRemaining: 'End of shift'
      },
      {
        id: 2,
        name: 'WINE PAIRING PRO',
        description: 'Sell 20 glasses of wine this week',
        progress: 17,
        target: 20,
        reward: 50,
        status: 'in_progress',
        timeRemaining: '2 days'
      }
    ],
    completedChallenges: [
      {
        id: 3,
        name: 'HIGH ROLLER',
        description: 'Close a single check over $150',
        reward: 25,
        completedAt: '2 hours ago',
        claimed: true
      }
    ],
    availableChallenges: [
      {
        id: 4,
        name: 'DESSERT CHAMPION',
        description: 'Sell 10 desserts this shift',
        reward: 15
      },
      {
        id: 5,
        name: 'COFFEE CLOSER',
        description: 'Sell 15 coffees/espressos',
        reward: 10
      },
      {
        id: 6,
        name: '5-STAR SERVICE',
        description: 'Get 5 perfect feedback scores',
        reward: 30
      }
    ],
    leaderboard: [
      { rank: 1, name: 'SARAH M.', sales: 4127.50, avgTip: 22.3, streak: 3 },
      { rank: 2, name: 'MARCUS K.', sales: 3892.00, avgTip: 21.8, streak: 2 },
      { rank: 3, name: 'YOU (CJ)', sales: 3764.50, avgTip: 20.1, streak: 1 },
      { rank: 4, name: 'ALEX R.', sales: 3621.75, avgTip: 19.5, streak: 0 },
      { rank: 5, name: 'JAMIE L.', sales: 3480.25, avgTip: 18.9, streak: 0 },
      { rank: 6, name: 'TAYLOR P.', sales: 3294.00, avgTip: 18.2, streak: 0 },
      { rank: 7, name: 'JORDAN C.', sales: 3087.50, avgTip: 17.8, streak: 0 }
    ]
  };

  const getRankIcon = (rank) => {
    if (rank <= 3) {
      return <Medal size={20} />;
    }
    return `${rank}.`;
  };

  const getRankClass = (rank) => {
    if (rank === 1) return 'gold';
    if (rank === 2) return 'silver';
    if (rank === 3) return 'bronze';
    return '';
  };

  return (
    <div className="page-with-nav">
      <NavBar currentView="challenges" setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />
      <div className="challenges-page">
        <h2>CHALLENGES</h2>

        <div className="challenges-grid">
          {/* Active Challenges */}
          <div className="challenges-section">
            <h3 className="section-title">Active Challenges</h3>
            {mockData.activeChallenges.length > 0 ? (
              mockData.activeChallenges.map(challenge => (
                <div key={challenge.id} className="challenge-card">
                  <div className="challenge-header">
                    <div className="challenge-name">{challenge.name}</div>
                    <div className="challenge-status in-progress">IN PROGRESS</div>
                  </div>
                  <div className="challenge-description">{challenge.description}</div>
                  <div className="challenge-progress">
                    <div className="progress-text">
                      <span>Progress: {challenge.progress}/{challenge.target}</span>
                      <span>{Math.round((challenge.progress / challenge.target) * 100)}%</span>
                    </div>
                    <div className="progress-bar-container">
                      <div 
                        className="progress-bar-fill" 
                        style={{ width: `${(challenge.progress / challenge.target) * 100}%` }}
                      >
                        {challenge.progress}/{challenge.target}
                      </div>
                    </div>
                  </div>
                  <div className="challenge-footer">
                    <div className="challenge-reward">Reward: ${challenge.reward}</div>
                    <div className="challenge-time">{challenge.timeRemaining}</div>
                  </div>
                </div>
              ))
            ) : (
              <div className="no-challenges">No active challenges</div>
            )}
          </div>

          {/* Completed Challenges */}
          <div className="challenges-section">
            <h3 className="section-title">Completed Challenges</h3>
            {mockData.completedChallenges.length > 0 ? (
              mockData.completedChallenges.map(challenge => (
                <div key={challenge.id} className="challenge-card completed">
                  <div className="challenge-header">
                    <div className="challenge-name">{challenge.name}</div>
                    <div className="challenge-status completed">COMPLETE</div>
                  </div>
                  <div className="challenge-description">{challenge.description}</div>
                  <div className="challenge-footer">
                    <div className="challenge-reward">Reward: ${challenge.reward}</div>
                    <button 
                      className={`challenge-btn ${challenge.claimed ? 'claimed' : ''}`}
                      disabled={challenge.claimed}
                    >
                      {challenge.claimed ? 'CLAIMED' : 'CLAIM REWARD'}
                    </button>
                  </div>
                </div>
              ))
            ) : (
              <div className="no-challenges">No completed challenges</div>
            )}
          </div>

          {/* Available Challenges */}
          <div className="challenges-section">
            <h3 className="section-title">Available Challenges</h3>
            {mockData.availableChallenges.map(challenge => (
              <div key={challenge.id} className="challenge-card available">
                <div className="challenge-header">
                  <div className="challenge-name">{challenge.name}</div>
                </div>
                <div className="challenge-description">{challenge.description}</div>
                <div className="challenge-footer">
                  <div className="challenge-reward">Reward: ${challenge.reward}</div>
                  <button className="challenge-btn secondary">START CHALLENGE</button>
                </div>
              </div>
            ))}
          </div>

          {/* Leaderboard */}
          <div className="challenges-section">
            <h3 className="section-title">Top Servers - This Week</h3>
            <div className="leaderboard-table">
              {mockData.leaderboard.map(entry => (
                <div 
                  key={entry.rank} 
                  className={`leaderboard-row ${entry.name.includes('YOU') ? 'highlight' : ''}`}
                >
                  <div className={`leaderboard-rank ${getRankClass(entry.rank)}`}>
                    {getRankIcon(entry.rank)}
                  </div>
                  <div className="leaderboard-name">
                    {entry.name}
                  </div>
                  <div className="leaderboard-sales">
                    ${entry.sales.toFixed(2)}
                  </div>
                  <div className="leaderboard-tips">
                    {entry.avgTip.toFixed(1)}% avg tips
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Challenges;