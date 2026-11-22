import { useId } from 'react';
import { PERIOD_KINDS } from './periods';
import './PeriodPicker.css';

/**
 * The row of period buttons (TODAY, THIS WEEK, THIS MONTH, CUSTOM) and, for a custom
 * period, the two date boxes.
 *
 * WHAT: Draws the choice and reports a new one. It holds nothing itself: the screen
 *       keeps the period (see periods.js for what one is) and asks rangeOf(period)
 *       for the two moments to send the backend.
 * WHY SHARED: Financials had these buttons first. The leaderboard picks its period the
 *       same way, so both screens draw this one component instead of two that would
 *       drift apart.
 *
 * @param {object} period - The period chosen now: { kind, customStart, customEnd }
 * @param {function} onChange - Called with the new period when a button is pressed or a date is picked
 */
function PeriodPicker({ period, onChange }) {
  // Two ids that are unique on the page, to tie each label to its date box: a screen
  // reader then says "Start Date" when the box is focused, and a tap on the label opens it
  const startId = useId();
  const endId = useId();

  return (
    <>
      <div className="time-period-selector">
        {PERIOD_KINDS.map(({ kind, label }) => (
          <button
            key={kind}
            className={`period-btn ${period.kind === kind ? 'active' : ''}`}
            onClick={() => onChange({ ...period, kind })}
          >
            {label}
          </button>
        ))}
      </div>

      {period.kind === 'custom' && (
        <div className="custom-date-range">
          <div className="date-input-group">
            <label htmlFor={startId}>Start Date:</label>
            <input
              id={startId}
              type="date"
              value={period.customStart}
              onChange={(e) => onChange({ ...period, customStart: e.target.value })}
            />
          </div>
          <div className="date-input-group">
            <label htmlFor={endId}>End Date:</label>
            <input
              id={endId}
              type="date"
              value={period.customEnd}
              onChange={(e) => onChange({ ...period, customEnd: e.target.value })}
            />
          </div>
        </div>
      )}
    </>
  );
}

export default PeriodPicker;
