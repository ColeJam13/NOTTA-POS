import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { getJson } from '../../services/api';
import { useStaff } from '../../context/StaffContext';
import { canDo, canOpenScreen } from '../../utils/roles';
import ClosedCheck from '../ClosedCheck';
import SearchResults, { rowsOf } from './SearchResults';
import { longEnough, MAX_QUERY_LENGTH, SEARCH_DELAY_MS } from './searchRules';

/**
 * The search box in the top bar: type a table, a check number, a server's name, or a
 * dish, and go straight to it.
 *
 * WHAT: Asks the backend (GET /api/search) for what was typed and shows the answer in
 *       a panel under the box, in four groups: tables, open checks, closed checks,
 *       and the menu.
 * HOW:  - It waits for a pause in typing before asking (debouncing: searchRules.js).
 *       - Each answer is checked against what is in the box NOW. Answers can come back
 *         out of order; one for "w" arriving after one for "w7" is thrown away.
 *       - Arrow keys move through the results, Enter opens one (the first, if the
 *         arrows have not been used), Escape closes the panel. So does a click
 *         anywhere else.
 *
 * WHAT CHOOSING A RESULT DOES depends on what the signed-in person may open
 * (utils/roles.js):
 *   - a table or an open check opens the order screen for that table, for those who
 *     take orders. For the kitchen it is a row of information.
 *   - a closed check opens the same pop-up the Tables screen shows. Whether its tip
 *     is in it was decided by the backend, check by check.
 *   - a menu item shows its price and whether it is 86'd. For those who may change
 *     the 86 board, choosing it opens the board.
 *
 * @param {Function} onOpenTable - Called with a table to open its order screen. Returns
 *                                 false if the person chose to stay where they are.
 * @param {Function} onOpenScreen - Called with a screen's name ('eightySix') to go there
 */
function Search({ onOpenTable, onOpenScreen }) {
  const { staff } = useStaff();
  const may = {
    openOrders: canOpenScreen(staff?.role, 'createOrder'),
    change86: canDo(staff?.role, 'change-86')
  };

  const [text, setText] = useState('');
  const [period, setPeriod] = useState('today');        // How far back to look for paid checks
  const [results, setResults] = useState(null);         // The backend's answer for `answered`, or null
  const [answered, setAnswered] = useState('');         // The text the answer on screen is for
  const [failed, setFailed] = useState(false);
  const [panelOpen, setPanelOpen] = useState(false);
  const [highlightedKey, setHighlightedKey] = useState(null);
  const [closedCheck, setClosedCheck] = useState(null);  // A paid check being shown in its pop-up

  const box = useRef(null);                             // The box and its panel, to tell a click inside from one outside
  const latestRequest = useRef(0);                      // Counts requests, so an answer can tell whether it is still wanted

  // Ask the backend a moment after the typing stops, and again when the period changes
  useEffect(() => {
    const query = text.trim();
    if (!longEnough(query)) {
      latestRequest.current += 1;                       // Whatever is still on its way is no longer wanted
      setResults(null);
      setFailed(false);
      setPanelOpen(false);
      setHighlightedKey(null);
      return undefined;
    }

    const timer = setTimeout(() => {
      const thisRequest = ++latestRequest.current;
      getJson(`/api/search?q=${encodeURIComponent(query)}&closed=${period}`)
        .then(answer => {
          if (thisRequest !== latestRequest.current) return;    // The box has moved on: drop this answer
          setResults(answer);
          setAnswered(query);
          setFailed(false);
          setHighlightedKey(null);
          setPanelOpen(true);
        })
        .catch(error => {
          if (thisRequest !== latestRequest.current) return;
          console.error('Error searching:', error);
          setResults(null);
          setFailed(true);
          setPanelOpen(true);
        });
    }, SEARCH_DELAY_MS);

    return () => clearTimeout(timer);                   // A new key within the delay: start the wait again
  }, [text, period]);

  // A click anywhere outside the box and its panel closes the panel
  useEffect(() => {
    if (!panelOpen) return undefined;
    const closeIfOutside = (event) => {
      if (box.current && !box.current.contains(event.target)) setPanelOpen(false);
    };
    document.addEventListener('mousedown', closeIfOutside);
    return () => document.removeEventListener('mousedown', closeIfOutside);
  }, [panelOpen]);

  const rows = rowsOf(results, may);
  const choosable = rows.filter(row => row.choosable);

  // Put the box back to empty: after a choice, there is nothing left to look for
  const clear = () => {
    setText('');
    setPanelOpen(false);
  };

  const choose = (row) => {
    if (!row.choosable) return;

    if (row.kind === 'closed') {
      setClosedCheck(row.item);                         // The pop-up; the screen underneath stays where it is
      clear();
      return;
    }
    if (row.kind === 'menu') {
      if (onOpenScreen('eightySix') !== false) clear();
      return;
    }
    // A table, or an open check: both carry their table's fields, which is what the
    // order screen needs. If the person chose to stay (unsaved items), nothing changes.
    const { tableId, tableNumber, status, isQuickOrder, serverName } = row.item;
    if (onOpenTable({ tableId, tableNumber, status, isQuickOrder, serverName }) !== false) clear();
  };

  // Arrow keys step through the results that can be chosen, wrapping at either end
  const step = (by) => {
    if (choosable.length === 0) return;
    const at = choosable.findIndex(row => row.key === highlightedKey);
    const next = at === -1 ? (by > 0 ? 0 : choosable.length - 1) : (at + by + choosable.length) % choosable.length;
    setHighlightedKey(choosable[next].key);
    // Keep the highlighted row in view when the list is longer than the panel
    document.getElementById(`search-${choosable[next].key}`)?.scrollIntoView?.({ block: 'nearest' });
  };

  const onKeyDown = (event) => {
    if (event.key === 'Escape') {
      setPanelOpen(false);
    } else if (!panelOpen) {
      return;                                           // Nothing on screen to move through
    } else if (event.key === 'ArrowDown') {
      event.preventDefault();                           // Otherwise the typing cursor jumps to the end of the text
      step(1);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      step(-1);
    } else if (event.key === 'Enter') {
      const row = choosable.find(candidate => candidate.key === highlightedKey) || choosable[0];
      if (row) choose(row);
    }
  };

  return (
    <div className="navbar-search" ref={box}>
      {/* role="combobox": a text box with a list of choices under it. The aria-...
          attributes tell a screen reader whether the list is showing and which choice
          the arrow keys are on. */}
      <input
        type="text"
        role="combobox"
        aria-label="Search tables, checks, and the menu"
        aria-expanded={panelOpen}
        aria-controls="search-results"
        aria-autocomplete="list"
        aria-activedescendant={panelOpen && highlightedKey ? `search-${highlightedKey}` : undefined}
        placeholder="Find table, order, item..."
        autoComplete="off"
        maxLength={MAX_QUERY_LENGTH}
        value={text}
        onChange={(event) => setText(event.target.value)}
        onKeyDown={onKeyDown}
        onFocus={() => { if (results || failed) setPanelOpen(true); }}      // Coming back to the box shows the last answer again
      />

      {panelOpen && (
        <SearchResults
          results={results}
          query={answered}
          rows={rows}
          highlightedKey={highlightedKey}
          onChoose={choose}
          onHighlight={setHighlightedKey}
          period={period}
          onPeriod={setPeriod}
          failed={failed}
        />
      )}

      {/* The paid check that was chosen. It is drawn at the end of the page (a "portal"),
          not inside the top bar, so that it covers the whole screen whatever the bar's
          own layout is. */}
      {closedCheck && createPortal(
        <ClosedCheck check={closedCheck} onClose={() => setClosedCheck(null)} />,
        document.body
      )}
    </div>
  );
}

export default Search;
