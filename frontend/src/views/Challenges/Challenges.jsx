import { useState, useEffect } from 'react';
import NavBar from '../../components/NavBar';
import PeriodPicker, { rangeOf, periodOf } from '../../components/PeriodPicker';
import Leaderboard from './Leaderboard';
import { getJson } from '../../services/api';
import { useStaff } from '../../context/StaffContext';
import './Challenges.css';

// How often the leaderboard is asked for again while the screen is open. Sales change
// when a check is paid, which is a few times a minute on a busy night.
export const LEADERBOARD_REFRESH_MS = 15000;

/**
 * Challenges screen. For now it holds the leaderboard: what each person sold in a period.
 *
 * The numbers come from the backend (GET /api/reports/staff-sales), which adds up the
 * checks closed in the period and gives each to the person who opened it: the same
 * sales the Financials screen reports, split by person. This screen only picks the
 * period and draws the answer. Everyone sees everyone's sales and rank (the owner's
 * decision D6); nobody's tips are here.
 *
 * It used to be invented names and invented challenges typed into this file. The
 * challenges come back, on real data, in step N5 of Documentation/nothing-fake-brief.md;
 * until then there are none on the screen rather than made-up ones.
 */
function Challenges({ setCurrentView, setSelectedTable }) {
  const { staff } = useStaff();

  // The period on screen. The buttons, and what each period means, are the ones the
  // Financials screen uses (components/PeriodPicker).
  const [period, setPeriod] = useState(periodOf('week'));
  // The backend's rows for that period. null until the first answer for it arrives.
  const [rows, setRows] = useState(null);
  const [failed, setFailed] = useState(false);

  // The period as text, so the effect below starts again only when it really changes
  const periodKey = `${period.kind}|${period.customStart}|${period.customEnd}`;

  useEffect(() => {
    // An answer can arrive after another period has been picked (or after the screen
    // has been left). "current" lets a late answer see that it is no longer wanted.
    let current = true;

    const load = () => {
      // Worked out on every ask, not once: "today" must become tomorrow at midnight
      // on a screen that has been open all night
      const { from, to } = rangeOf(period);
      getJson(`/api/reports/staff-sales?from=${from}&to=${to}`)
        .then(report => {
          if (!current) return;
          setRows(report.staff);
          setFailed(false);
        })
        .catch(error => {
          if (!current) return;
          console.error('Error fetching the leaderboard:', error);
          setFailed(true);
        });
    };

    setRows(null);                  // The last period's rows are not this period's: draw nothing until the answer comes
    setFailed(false);
    load();
    const timer = setInterval(load, LEADERBOARD_REFRESH_MS);

    return () => {
      current = false;
      clearInterval(timer);
    };
    // periodKey stands for the three fields of period that matter
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [periodKey]);

  return (
    <div className="page-with-nav">
      <NavBar currentView="challenges" setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />
      <div className="challenges-page">
        <h2>CHALLENGES</h2>

        <PeriodPicker period={period} onChange={setPeriod} />

        <div className="challenges-section">
          <h3 className="section-title">Leaderboard</h3>
          <p className="leaderboard-about">Sales before tax, on checks closed in this period, by who opened the check.</p>
          {failed && <p className="leaderboard-failed" role="alert">The leaderboard could not be loaded. It will be tried again in a moment.</p>}
          <Leaderboard rows={rows} ownStaffId={staff ? staff.staffId : null} />
        </div>
      </div>
    </div>
  );
}

export default Challenges;
