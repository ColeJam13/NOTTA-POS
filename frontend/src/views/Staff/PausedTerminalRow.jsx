import { formatTimeLeft } from '../../utils/formatters';
import './Staff.css';

/**
 * One terminal paused for wrong PINs, as a row on the Staff screen: its address, a
 * mark if it is the terminal the manager is standing at, how long is left, and RELEASE.
 *
 * In its own file so that the template gallery can draw the very same row the Staff
 * screen does (views/Gallery). Styles come from Staff.css.
 *
 * @param {Object} terminal - { device, secondsLeft, thisDevice }, as the backend lists it
 * @param {Function} onRelease - Called with the terminal's address when RELEASE is pressed
 */
function PausedTerminalRow({ terminal, onRelease }) {
  return (
    <div className="staff-paused-row">
      <span className="staff-paused-device">{terminal.device}</span>
      {terminal.thisDevice && <span className="staff-paused-here">THIS TERMINAL</span>}
      <span className="staff-paused-left">{formatTimeLeft(terminal.secondsLeft)} left</span>
      <button
        className="staff-btn staff-btn-primary"
        aria-label={`Release ${terminal.device}`}
        onClick={() => onRelease(terminal.device)}
      >
        RELEASE
      </button>
    </div>
  );
}

export default PausedTerminalRow;
