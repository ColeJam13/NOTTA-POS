import { formatTimeAgo } from '../../utils/formatters';

// Determine border color based on status
const borderColorFor = (table) => {
    if (table.completedCount === table.itemCount) return 'green';
    if (table.limboCount > 0) return 'purple';
    if (table.pendingCount > 0 || table.firedCount > 0) return 'yellow';
    return 'default';
};

/**
 * One open check as a card on the Tables and Quick Orders screens: the table, what it
 * comes to so far, how many items are at each stage, who has it, and how long ago it
 * was opened. The border says what the kitchen is doing: everything made, something
 * still in its edit window, or being worked on.
 *
 * In its own file so that the template gallery can draw the very same card the two
 * screens do (views/Gallery). The card styles come from Tables.css, loaded by the
 * Tables view.
 *
 * @param {Object} table - One entry of activeTables from GET /api/tables/overview
 * @param {Function} onOpen - Called when the card is tapped
 */
function ActiveTableCard({ table, onOpen }) {
    return (
        <div
            className={`table-card border-${borderColorFor(table)}`}
            onClick={onOpen}
        >
            <h3 className="table-card-number">{table.tableNumber}</h3>
            <div className="table-card-total">${table.subtotal.toFixed(2)}</div>
            <div className="table-card-items">{table.itemCount} items</div>
            <div className="table-card-status">
                {table.limboCount > 0 && `${table.limboCount}L `}
                {table.pendingCount > 0 && `${table.pendingCount}P `}
                {table.firedCount > 0 && `${table.firedCount}F `}
                {table.completedCount > 0 && `${table.completedCount}C`}
            </div>
            <div className="table-card-server">Server: {table.serverName || 'N/A'}</div>
            <div className="table-card-time">{formatTimeAgo(table.openedAt)}</div>
        </div>
    );
}

export default ActiveTableCard;
