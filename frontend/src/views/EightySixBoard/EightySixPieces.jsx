import './EightySixBoard.css';

/**
 * The two things the 86 board is made of. They only draw what they are given and
 * report a press; the board (EightySixBoard) decides what happens.
 *
 * In their own file so that the template gallery can draw the very same pieces the
 * board does (views/Gallery). Styles come from EightySixBoard.css.
 */

/**
 * One 86'd item in the "86'd now" strip at the top of the board. Pressing it puts the
 * item back on.
 *
 * @param {Object} item - The menu item: { menuItemId, name, ... }
 * @param {boolean} canChange - Whether this person may change what is 86'd
 * @param {Function} onToggle - Called with the item when it is pressed
 */
export function EightySixChip({ item, canChange, onToggle }) {
  return (
    <button
      className="eighty-six-chip"
      aria-label={`Put ${item.name} back on`}
      disabled={!canChange}
      onClick={() => onToggle(item)}
    >
      <span>{item.name}</span>
      {canChange && <small>TAP TO PUT BACK ON</small>}
    </button>
  );
}

/**
 * One menu item on the board's grid: its name and whether it is available or 86'd.
 * Pressing it switches between the two.
 *
 * @param {Object} item - The menu item: { menuItemId, name, is86d, ... }
 * @param {boolean} canChange - Whether this person may change what is 86'd
 * @param {Function} onToggle - Called with the item when it is pressed
 */
export function EightySixTile({ item, canChange, onToggle }) {
  return (
    <button
      className={`eighty-six-item ${item.is86d ? 'is-86d' : ''}`}
      aria-pressed={item.is86d}                     // Tells screen readers (and tests) whether the item is 86'd
      disabled={!canChange}
      onClick={() => onToggle(item)}
    >
      <span className="eighty-six-item-name">{item.name}</span>
      <span className="eighty-six-item-status">{item.is86d ? "86'D" : 'AVAILABLE'}</span>
    </button>
  );
}
