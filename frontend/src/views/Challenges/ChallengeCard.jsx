import { describeGoal, describePrize, describeTime, formatAmount, participantFor, percentOf, statusFor } from './challengeRules';

/**
 * One challenge, as a card.
 *
 * FOR EVERYONE: its name, what has to be done, the signed-in person's own progress
 * toward the target, the time left, and the prize. Once it has ended: whether they
 * completed it, and whether the prize is waiting or has been handed over.
 *
 * FOR A MANAGER, under that: everyone's progress, a button to mark each completed
 * person's prize as handed over, and EDIT, END NOW, and DELETE.
 *
 * Nothing is worked out here. Progress, "completed", and who was awarded all come
 * from the backend in the challenge (ChallengeView); challengeRules.js turns them
 * into words. The card only says what was pressed: the screen sends the request.
 *
 * Used by the Challenges screen and by the template gallery.
 *
 * @param {Object} challenge - One challenge from GET /api/challenges
 * @param {number|null} ownStaffId - The signed-in person's staff ID (their progress is the one shown)
 * @param {boolean} canManage - Whether to show the manager's part
 * @param {function} onEdit - Called with the challenge
 * @param {function} onEnd - Called with the challenge
 * @param {function} onDelete - Called with the challenge
 * @param {function} onAward - Called with the challenge and the participant
 */
function ChallengeCard({ challenge, ownStaffId = null, canManage = false, onEdit, onEnd, onDelete, onAward }) {
  const own = participantFor(challenge, ownStaffId);
  const status = statusFor(challenge, own);
  const prizeNote = describePrize(challenge, own);
  const upcoming = challenge.state === 'upcoming';
  const amount = (value) => formatAmount(challenge, value);

  return (
    <article className={`challenge-card ${challenge.state}`} aria-label={challenge.name}>
      <div className="challenge-header">
        <div className="challenge-name">{challenge.name}</div>
        {/* The word says the state; the class only picks one of three looks */}
        <div className={`challenge-status ${status.look}`}>{status.word}</div>
      </div>

      <div className="challenge-goal">{describeGoal(challenge)}</div>
      {challenge.description && <div className="challenge-description">{challenge.description}</div>}

      {/* Nothing can have been sold toward a challenge that has not started */}
      {!upcoming && (
        <div className="challenge-progress">
          <div className="progress-text">
            <span>Your progress: <span className="challenge-amount">{amount(own.progress)} / {amount(challenge.target)}</span></span>
            <span className="challenge-amount">{percentOf(own.progress, challenge.target)}%</span>
          </div>
          <div
            className="progress-bar-container"
            role="progressbar"
            aria-label={`Your progress on ${challenge.name}`}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={percentOf(own.progress, challenge.target)}
          >
            <div className="progress-bar-fill" style={{ width: `${percentOf(own.progress, challenge.target)}%` }} />
          </div>
          {/* An average only counts once enough checks are behind it */}
          {challenge.measure === 'check_average' && (
            <div className="challenge-checks">{own.checksClosed} of the {challenge.minChecks} checks needed</div>
          )}
        </div>
      )}

      <div className="challenge-footer">
        <div className="challenge-reward">{challenge.prize ? `Prize: ${challenge.prize}` : ''}</div>
        <div className="challenge-time">{describeTime(challenge)}</div>
      </div>

      {prizeNote && <div className="challenge-prize-note">{prizeNote}</div>}

      {canManage && (
        <div className="challenge-manage">
          {!upcoming && (
            <>
              <h4 className="challenge-people-title">Everyone&apos;s progress</h4>
              {challenge.participants.length === 0 ? (
                <p className="challenge-nobody">Nobody has closed a check in this challenge yet.</p>
              ) : (
                <ul className="challenge-people">
                  {challenge.participants.map(person => (
                    <li key={person.staffId} className="challenge-person">
                      <span className="challenge-person-name">{person.name}</span>
                      <span className="challenge-amount">{amount(person.progress)} / {amount(challenge.target)}</span>
                      <span className="challenge-person-status">{statusFor(challenge, person).word}</span>
                      <span className="challenge-person-prize">
                        {person.awardedAt && `Awarded by ${person.awardedByName}`}
                        {/* Only someone who completed it, only when there is a prize to hand over, and only once */}
                        {!person.awardedAt && person.completed && challenge.prize && (
                          <button className="challenge-btn" onClick={() => onAward(challenge, person)}>MARK AWARDED</button>
                        )}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </>
          )}
          <div className="challenge-actions">
            <button className="challenge-btn secondary" onClick={() => onEdit(challenge)}>EDIT</button>
            {challenge.state === 'active' && (
              <button className="challenge-btn secondary" onClick={() => onEnd(challenge)}>END NOW</button>
            )}
            <button className="challenge-btn danger" onClick={() => onDelete(challenge)}>DELETE</button>
          </div>
        </div>
      )}
    </article>
  );
}

export default ChallengeCard;
