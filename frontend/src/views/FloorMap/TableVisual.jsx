import { memo } from 'react';
import './FloorMap.css';

/**
 * One table on the Floor Map: its number and seats, placed and turned where the
 * layout says, and colored by its status (available, occupied).
 *
 * Memoized Table Component - prevents unnecessary re-renders
 * Only re-renders if table data actually changes
 *
 * In edit mode a table can be picked up (onDragStart) instead of opened, and
 * the selected one is outlined.
 *
 * In its own file so that the template gallery can draw the very same table the
 * Floor Map does (views/Gallery). Styles come from FloorMap.css.
 */
const TableVisual = memo(({ table, onTableClick, editing, selected, onDragStart }) => {
    return (
        <div
            className={`table-visual ${table.shape} ${table.status} ${table.seatCount >= 6 ? 'large' : ''} ${editing ? 'editing' : ''} ${selected ? 'selected' : ''}`}
            style={{
                left: `${table.xposition}px`,
                top: `${table.yposition}px`,
                // Only set when the table is turned, so an unturned table keeps its hover effect
                transform: table.rotation ? `rotate(${table.rotation}deg)` : undefined
            }}
            data-testid={`table-${table.tableNumber}`}
            onClick={() => { if (!editing) onTableClick(table); }}                 // In edit mode a tap must not open the table's order
            onMouseDown={editing ? (event) => onDragStart(event, table) : undefined}
            onTouchStart={editing ? (event) => onDragStart(event, table) : undefined}
        >
            <span className="table-label">{table.tableNumber}</span>
            <span className="table-seats">{table.seatCount} seats</span>
        </div>
    );
});

export default TableVisual;
