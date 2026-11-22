import { useState, useEffect, useCallback, useRef } from 'react';
import NavBar from '../../components/NavBar';
import PeriodPicker, { rangeOf, periodOf } from '../../components/PeriodPicker';
import Leaderboard from './Leaderboard';
import ChallengeCard from './ChallengeCard';
import ChallengeForm from './ChallengeForm';
import { getJson, sendForAnswer } from '../../services/api';
import { useStaff } from '../../context/StaffContext';
import { canDo } from '../../utils/roles';
import './Challenges.css';

// How often the leaderboard is asked for again while the screen is open. Sales change
// when a check is paid, which is a few times a minute on a busy night.
export const LEADERBOARD_REFRESH_MS = 15000;

// How often the challenges are asked for again. Progress moves when a check is paid,
// like the leaderboard, so the two keep the same pace.
export const CHALLENGES_REFRESH_MS = 15000;

/**
 * Challenges screen: the challenges a manager has set, and the leaderboard.
 *
 * CHALLENGES come from GET /api/challenges: each with the signed-in person's progress,
 * the time left, and the prize. Progress is worked out by the backend from paid checks
 * every time it is asked for; this screen draws the answer and adds nothing up. Nobody
 * joins a challenge: everyone who opens checks in its period is in it.
 *
 * A MANAGER also sees everyone's progress on each challenge, and can make one, change
 * it, end it early, delete it, and mark a prize as handed over. The backend enforces
 * who may; the buttons are only left off for everyone else (utils/roles.js).
 *
 * THE LEADERBOARD (GET /api/reports/staff-sales) is what each person sold in a period:
 * the Financials screen's sales, split by person. Everyone sees everyone's sales and
 * rank (the owner's decision D6); nobody's tips are on this screen.
 *
 * Every name and number here used to be invented and typed into this file.
 */
function Challenges({ setCurrentView, setSelectedTable }) {
  const { staff } = useStaff();
  const ownStaffId = staff ? staff.staffId : null;
  const canManage = canDo(staff?.role, 'manage-challenges');

  // ----- Challenges -----
  // The backend's list. null until the first answer arrives.
  const [challenges, setChallenges] = useState(null);
  const [challengesFailed, setChallengesFailed] = useState(false);
  // The form: undefined when closed, null for a new challenge, or the challenge being changed
  const [formFor, setFormFor] = useState(undefined);
  const [menuItems, setMenuItems] = useState([]);
  // The backend's words when it refused something a manager pressed
  const [refusal, setRefusal] = useState('');

  // False once the screen has been left, so an answer that arrives late is dropped
  const onScreen = useRef(true);
  useEffect(() => {
    onScreen.current = true;
    return () => { onScreen.current = false; };
  }, []);

  const loadChallenges = useCallback(() =>
    getJson('/api/challenges')
      .then(list => {
        if (!onScreen.current) return;
        setChallenges(list);
        setChallengesFailed(false);
      })
      .catch(error => {
        if (!onScreen.current) return;
        console.error('Error fetching challenges:', error);
        setChallengesFailed(true);
      }), []);

  useEffect(() => {                                                 // Load the challenges on mount, and again every 15 seconds
    loadChallenges();
    const timer = setInterval(loadChallenges, CHALLENGES_REFRESH_MS);
    return () => clearInterval(timer);
  }, [loadChallenges]);

  // Open the form (for a new challenge, or to change one) and read the menu for its
  // "which items count" list. The form can be filled in before the menu arrives.
  const openForm = (challenge) => {
    setRefusal('');
    setFormFor(challenge);
    getJson('/api/menu-items')
      .then(items => { if (onScreen.current) setMenuItems(items); })
      .catch(error => console.error('Error fetching the menu for the challenge form:', error));
  };

  // SAVE on the form. The answer goes back to the form, which shows a refusal in the
  // backend's own words and stays open.
  const saveChallenge = async (request) => {
    const answer = formFor
      ? await sendForAnswer('PUT', `/api/challenges/${formFor.challengeId}`, request)
      : await sendForAnswer('POST', '/api/challenges', request);
    if (answer.ok && onScreen.current) {
      setFormFor(undefined);
      await loadChallenges();
    }
    return answer;
  };

  // A button on a card: ask first (none of these can be undone), send, and either show
  // the challenges as they now are or the backend's reason for refusing
  const act = async (question, method, path) => {
    if (!window.confirm(question)) return;
    const answer = await sendForAnswer(method, path);
    if (!onScreen.current) return;
    setRefusal(answer.ok ? '' : answer.message);
    await loadChallenges();
  };

  const endChallenge = (challenge) =>
    act(`End "${challenge.name}" now? Checks paid after this will not count toward it.`, 'POST', `/api/challenges/${challenge.challengeId}/end`);
  const deleteChallenge = (challenge) =>
    act(`Delete "${challenge.name}"? This cannot be undone.`, 'DELETE', `/api/challenges/${challenge.challengeId}`);
  const markAwarded = (challenge, person) =>
    act(`Mark the prize of "${challenge.name}" as handed to ${person.name}? This cannot be undone.`,
      'POST', `/api/challenges/${challenge.challengeId}/awards/${person.staffId}`);

  const inState = (state) => (challenges || []).filter(challenge => challenge.state === state);
  const cardsFor = (list) => list.map(challenge => (
    <ChallengeCard
      key={challenge.challengeId}
      challenge={challenge}
      ownStaffId={ownStaffId}
      canManage={canManage}
      onEdit={openForm}
      onEnd={endChallenge}
      onDelete={deleteChallenge}
      onAward={markAwarded}
    />
  ));

  // ----- The leaderboard -----
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

  const active = inState('active');
  const upcoming = inState('upcoming');
  const finished = inState('finished');

  return (
    <div className="page-with-nav">
      <NavBar currentView="challenges" setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />
      <div className="challenges-page">
        <h2>CHALLENGES</h2>

        {canManage && (
          <div className="challenges-toolbar">
            <button className="challenge-btn" onClick={() => openForm(null)}>NEW CHALLENGE</button>
          </div>
        )}

        {refusal && <p className="challenges-refusal" role="alert">{refusal}</p>}
        {challengesFailed && <p className="challenges-refusal" role="alert">The challenges could not be loaded. They will be tried again in a moment.</p>}

        {challenges !== null && challenges.length === 0 && (
          <div className="challenges-section challenges-none">
            <p className="no-challenges">No challenges yet.</p>
          </div>
        )}

        {challenges !== null && challenges.length > 0 && (
          <div className="challenges-grid">
            <section className="challenges-section" aria-label="Active challenges">
              <h3 className="section-title">Active</h3>
              {active.length > 0 ? cardsFor(active) : <p className="no-challenges">No active challenges.</p>}
            </section>

            <section className="challenges-section" aria-label="Finished challenges">
              <h3 className="section-title">Finished</h3>
              {finished.length > 0 ? cardsFor(finished) : <p className="no-challenges">No finished challenges.</p>}
            </section>

            {/* Only there when a manager has set one for later */}
            {upcoming.length > 0 && (
              <section className="challenges-section full-width" aria-label="Challenges coming up">
                <h3 className="section-title">Coming up</h3>
                {cardsFor(upcoming)}
              </section>
            )}
          </div>
        )}

        <PeriodPicker period={period} onChange={setPeriod} />

        <div className="challenges-section">
          <h3 className="section-title">Leaderboard</h3>
          <p className="leaderboard-about">Sales before tax, on checks closed in this period, by who opened the check.</p>
          {failed && <p className="leaderboard-failed" role="alert">The leaderboard could not be loaded. It will be tried again in a moment.</p>}
          <Leaderboard rows={rows} ownStaffId={ownStaffId} />
        </div>

        {formFor !== undefined && (
          <ChallengeForm
            challenge={formFor}
            menuItems={menuItems}
            onSave={saveChallenge}
            onCancel={() => setFormFor(undefined)}
          />
        )}
      </div>
    </div>
  );
}

export default Challenges;
