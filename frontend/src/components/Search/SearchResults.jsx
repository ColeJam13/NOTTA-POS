import { formatTableName, formatTimeAgo } from '../../utils/formatters';
import { amountOn } from '../ClosedCheck';
import { SEARCH_PERIODS } from './searchRules';
import './Search.css';

/**
 * Every result as one flat list, top to bottom as drawn, each with what choosing it
 * does. The arrow keys walk this list; the panel draws from it.
 *
 * "choosable" is false for a result that is only information for this person: a table
 * or open check for someone who does not take orders, a menu item for someone who may
 * not change the 86 board. Those are drawn as plain rows and the arrow keys skip them.
 *
 * @param {Object} results - The backend's answer (SearchResultsView)
 * @param {Object} may - { openOrders, change86 }: what the signed-in person may do
 * @returns {Array} - [{ key, kind, item, choosable }]
 */
export const rowsOf = (results, may) => {
  if (!results) return [];
  return [
    ...results.tables.results.map(table => ({ key: `table-${table.tableId}`, kind: 'table', item: table, choosable: may.openOrders })),
    ...results.openChecks.results.map(check => ({ key: `open-${check.orderId}`, kind: 'open', item: check, choosable: may.openOrders })),
    ...results.closedChecks.results.map(check => ({ key: `closed-${check.orderId}`, kind: 'closed', item: check, choosable: true })),
    ...results.menuItems.results.map(item => ({ key: `menu-${item.menuItemId}`, kind: 'menu', item, choosable: may.change86 }))
  ];
};

// The four groups, in the order they are drawn, with the words over each
const GROUPS = [
  { kind: 'table', group: 'tables', title: 'TABLES' },
  { kind: 'open', group: 'openChecks', title: 'OPEN CHECKS' },
  { kind: 'closed', group: 'closedChecks', title: 'CLOSED CHECKS' },
  { kind: 'menu', group: 'menuItems', title: 'MENU' }
];

const money = (amount) => `$${amount.toFixed(2)}`;

// What one row says: the main words, the smaller words under them, and what sits at the right
const describe = (row) => {
  const { kind, item } = row;
  if (kind === 'table') {
    return {
      name: formatTableName(item),
      detail: [item.section, item.seatCount != null ? `${item.seatCount} seats` : null].filter(Boolean).join(' · '),
      state: item.status === 'occupied' ? 'OCCUPIED' : 'AVAILABLE'
    };
  }
  if (kind === 'open') {
    return {
      name: formatTableName(item),
      detail: [`Check #${item.orderId}`, item.serverName, `${item.itemCount} ${item.itemCount === 1 ? 'item' : 'items'}`, `opened ${formatTimeAgo(item.openedAt)}`].filter(Boolean).join(' · '),
      amount: money(item.subtotal)
    };
  }
  if (kind === 'closed') {
    return {
      name: formatTableName(item),
      detail: [`Check #${item.orderId}`, item.serverName, `closed ${formatTimeAgo(item.closedAt || item.createdAt)}`].filter(Boolean).join(' · '),
      amount: money(amountOn(item))                 // Check + tip when the tip was sent; the check alone when it was not
    };
  }
  return {
    name: item.name,
    detail: item.category,
    amount: money(item.price),
    state: item.is86d ? "86'D" : 'AVAILABLE',
    out: item.is86d
  };
};

/**
 * The panel under the search box: what was found, in four groups.
 *
 * WHAT: Draws the backend's answer. A group with nothing in it is not drawn. With
 *       nothing in any group it says so in words. Under the results, the choice of
 *       how far back to look for paid checks.
 * WHY A COMPONENT OF ITS OWN: the box (Search.jsx) decides WHEN to ask and what a
 *       choice does; this only draws. The template gallery draws it too, with
 *       made-up results, to show it in every template.
 *
 * A result the person can act on is a button. One that is only information (see
 * rowsOf) is a plain row with the same words.
 *
 * @param {Object} results - The backend's answer, or null while there is none
 * @param {string} query - What was searched for, for the "nothing found" line
 * @param {Array} rows - rowsOf(results, may)
 * @param {string|null} highlightedKey - The row the arrow keys are on
 * @param {Function} onChoose - Called with a row when it is chosen
 * @param {Function} onHighlight - Called with a row's key when the pointer is on it
 * @param {string} period - How far back paid checks are looked for ('today', ...)
 * @param {Function} onPeriod - Called with a period's value when one is chosen
 * @param {boolean} failed - True when the backend could not be asked
 */
function SearchResults({ results, query, rows, highlightedKey, onChoose, onHighlight, period, onPeriod, failed }) {
  return (
    <div className="search-panel">
      {/* role="listbox": to a screen reader this is a list to choose from, tied to the box above it */}
      <div className="search-results" id="search-results" role="listbox" aria-label="Search results">
        {failed && <p className="search-error" role="alert">Search is not available right now. Try again in a moment.</p>}

        {!failed && results && rows.length === 0 && (
          <p className="search-empty">Nothing found for &ldquo;{query}&rdquo;.</p>
        )}

        {!failed && results && GROUPS.map(({ kind, group, title }) => {
          const groupRows = rows.filter(row => row.kind === kind);
          if (groupRows.length === 0) return null;                 // A group with nothing in it is not drawn

          return (
            <div key={kind} className="search-group" role="group" aria-label={title}>
              <h4 className="search-group-title">{title}</h4>

              {groupRows.map(row => {
                const { name, detail, amount, state, out } = describe(row);
                // The row read out as one sentence. Without this a screen reader runs the
                // pieces together: "Table W7Window".
                const spoken = [name, detail, amount, state].filter(Boolean).join(', ');
                const words = (
                  <>
                    <span className="search-row-main">
                      <span className="search-row-name">{name}</span>
                      {detail && <span className="search-row-detail">{detail}</span>}
                    </span>
                    {amount && <span className="search-row-amount">{amount}</span>}
                    {state && <span className={`search-row-state ${out ? 'out' : ''}`}>{state}</span>}
                  </>
                );

                // Something this person can open: a button. The arrow keys can land on it.
                if (row.choosable) {
                  return (
                    <button
                      key={row.key}
                      id={`search-${row.key}`}
                      type="button"
                      role="option"
                      aria-label={spoken}
                      aria-selected={row.key === highlightedKey}
                      className={`search-row ${row.key === highlightedKey ? 'highlighted' : ''}`}
                      onClick={() => onChoose(row)}
                      onMouseEnter={() => onHighlight(row.key)}
                    >
                      {words}
                    </button>
                  );
                }
                // Information only: the same words, nothing to press
                return (
                  <div key={row.key} id={`search-${row.key}`} role="option" aria-label={spoken} aria-selected="false" aria-disabled="true" className="search-row">
                    {words}
                  </div>
                );
              })}

              {results[group].more && (
                <p className="search-more">Showing the first {groupRows.length}. Type more to narrow it down.</p>
              )}
            </div>
          );
        })}
      </div>

      {/* How far back to look for paid checks. Today is the default: most searches are
          about the shift in progress. */}
      <div className="search-footer" role="group" aria-label="Closed checks from">
        <span className="search-footer-label">Closed checks from:</span>
        {SEARCH_PERIODS.map(({ value, label }) => (
          <button
            key={value}
            type="button"
            className={`search-period ${period === value ? 'active' : ''}`}
            aria-pressed={period === value}
            onMouseDown={(event) => event.preventDefault()}      // Keeps the typing cursor in the search box
            onClick={() => onPeriod(value)}
          >
            {label}
          </button>
        ))}
      </div>
    </div>
  );
}

export default SearchResults;
