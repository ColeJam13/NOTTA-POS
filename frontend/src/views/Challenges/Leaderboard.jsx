import { Medal } from 'lucide-react';

/**
 * The leaderboard: one row per person who sold something in the period.
 *
 * WHAT: Draws the rows it is given. Every number, the order of the rows, and each
 *       person's rank come from the backend (GET /api/reports/staff-sales); nothing is
 *       added up, sorted, or ranked here.
 * WHY A COMPONENT OF ITS OWN: the Challenges screen draws it from the backend's answer
 *       and the template gallery draws it from made-up rows, so a template is judged on
 *       the same markup the screen uses.
 *
 * There is no tip in a row and no column for one: the backend does not send any.
 *
 * @param {Array|null} rows - The backend's rows, best sales first. null while the first
 *                            answer for a period has not arrived (nothing is drawn).
 * @param {number|null} ownStaffId - The signed-in person's staff ID. Their row is marked
 *                            as theirs. By ID, not by name: two people can share a name.
 */

const money = (amount) => `$${amount.toFixed(2)}`;

// The three ranks that get a medal, and the color token class of each. The rank's
// NUMBER is always printed beside it: gold and bronze are near-twins in the light
// templates, so the color of a medal is decoration and the number is what says who is where.
const MEDALS = { 1: 'gold', 2: 'silver', 3: 'bronze' };

function Leaderboard({ rows, ownStaffId = null }) {
  if (rows === null) {
    return null;
  }

  if (rows.length === 0) {
    return <p className="leaderboard-empty">No sales yet for this period.</p>;
  }

  return (
    // A window too narrow for seven columns scrolls the table sideways, not the page
    <div className="leaderboard-scroll">
      <table className="leaderboard-table">
        <thead>
          <tr>
            <th scope="col" className="leaderboard-col-rank">RANK</th>
            <th scope="col" className="leaderboard-col-name">NAME</th>
            <th scope="col">SALES</th>
            <th scope="col">CHECKS</th>
            <th scope="col">CHECK AVG</th>
            <th scope="col">BIGGEST CHECK</th>
            <th scope="col">ITEMS</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(row => {
            const own = ownStaffId !== null && row.staffId === ownStaffId;
            const medal = MEDALS[row.rank];
            return (
              <tr key={row.staffId} className={`leaderboard-row ${own ? 'own' : ''}`}>
                <td className={`leaderboard-rank ${medal || ''}`}>
                  {medal && <Medal size={18} aria-hidden="true" />}
                  <span className="leaderboard-rank-number">{row.rank}</span>
                </td>
                <td className="leaderboard-name">
                  {row.name}
                  {/* A word as well as a color, so the row is theirs in every template */}
                  {own && <span className="leaderboard-you">YOU</span>}
                </td>
                <td className="leaderboard-sales">{money(row.sales)}</td>
                <td className="leaderboard-number">{row.checksClosed}</td>
                <td className="leaderboard-number">{money(row.checkAverage)}</td>
                <td className="leaderboard-number">{money(row.biggestCheck)}</td>
                <td className="leaderboard-number">{row.itemsSold}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

export default Leaderboard;
