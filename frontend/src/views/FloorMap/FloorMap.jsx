import { useState, useEffect, useMemo, useRef } from 'react';
import NavBar from '../../components/NavBar';
import TableVisual from './TableVisual';
import './FloorMap.css';
import { apiFetch } from '../../services/api';
import { useStaff } from '../../context/StaffContext';
import { canDo } from '../../utils/roles';
import WideScreenNote from '../../components/WideScreenNote';

// In edit mode a dragged table snaps to this many pixels, so tables line up in rows
const GRID_SIZE = 10;

// How far one press of ROTATE turns the selected table, in degrees
const ROTATE_STEP = 45;

// Round a pixel position to the nearest grid line
const snapToGrid = (value) => Math.round(value / GRID_SIZE) * GRID_SIZE;

// Where a mouse or touch event happened on the screen. A touch event keeps its
// position in a list of touches; a mouse event has it directly.
const pointerPosition = (event) => {
    const point = event.touches && event.touches.length > 0 ? event.touches[0] : event;
    return { x: point.clientX, y: point.clientY };
};

// The same position measured from the top-left corner of the floor itself.
// Tables are placed inside the floor's border, so the border's thickness
// (clientLeft / clientTop) is taken off as well.
const positionOnFloor = (event, floorElement) => {
    const floor = floorElement.getBoundingClientRect();
    const pointer = pointerPosition(event);
    return {
        x: pointer.x - floor.left - floorElement.clientLeft,
        y: pointer.y - floor.top - floorElement.clientTop
    };
};

/**
 * Floor Map: every table where it sits in the room. Tap a table to open its order.
 *
 * EDIT LAYOUT switches to edit mode, where tables are dragged to new spots and
 * turned. Nothing is saved until SAVE LAYOUT is pressed; CANCEL puts every
 * table back. While editing, the screen works on its own copy of the tables
 * (draftTables) and stops refreshing from the backend, so a refresh cannot
 * snap a table back while it is being dragged.
 */
function FloorMap({ setCurrentView, setSelectedTable }) {
    const [tables, setTables] = useState([]);

    const { staff } = useStaff();
    const canEditLayout = canDo(staff?.role, 'edit-layout');

    // --- Edit mode ---
    const [editing, setEditing] = useState(false);
    const [draftTables, setDraftTables] = useState([]);          // The copy being rearranged
    const [selectedTableId, setSelectedTableId] = useState(null);
    const [saving, setSaving] = useState(false);
    const [errorMessage, setErrorMessage] = useState('');

    // The table being dragged right now: { tableId, grabX, grabY, width, height }, or null.
    // A ref, not state: it changes on every mouse move and nothing on screen reads it.
    const dragRef = useRef(null);
    const canvasRef = useRef(null);

    // The polling interval below reads this to know whether to skip a refresh.
    // (A ref always holds the latest value; the interval's function is created once.)
    const editingRef = useRef(false);
    useEffect(() => {
        editingRef.current = editing;
    }, [editing]);

    // Initial fetch - only runs once on mount
    useEffect(() => {
        apiFetch('/api/tables?quickOrders=false')
            .then(response => response.json())
            .then(data => setTables(data))
            .catch(error => console.error('Error fetching tables:', error));
    }, []);

    // Polling interval - runs every 3 seconds
    useEffect(() => {
        const interval = setInterval(() => {
            if (editingRef.current) return;                      // Do not refresh under someone who is rearranging the floor

            apiFetch('/api/tables?quickOrders=false')
                .then(response => response.json())
                .then(data => setTables(data))
                .catch(error => console.error('Error fetching tables:', error));
        }, 3000);

        return () => clearInterval(interval);
    }, []);

    // Memoized click handler - prevents function recreation on every render
    const handleTableClick = useMemo(() => {
        return (table) => {
            setSelectedTable(table);
            setCurrentView('createOrder');
        };
    }, [setSelectedTable, setCurrentView]);

    // ------------------------------------------------------------------
    // Edit mode
    // ------------------------------------------------------------------

    const startEditing = () => {
        setDraftTables(tables.map(table => ({ ...table })));     // Work on a copy, so CANCEL has the originals to go back to
        setSelectedTableId(null);
        setErrorMessage('');
        setEditing(true);
    };

    const cancelEditing = () => {
        dragRef.current = null;
        setEditing(false);
        setDraftTables([]);
        setSelectedTableId(null);
        setErrorMessage('');
    };

    // A table was pressed in edit mode: select it and remember where on the table it was grabbed,
    // so the table does not jump to put its corner under the finger.
    const handleDragStart = useMemo(() => {
        return (event, table) => {
            const pointer = positionOnFloor(event, canvasRef.current);

            dragRef.current = {
                tableId: table.tableId,
                grabX: pointer.x - table.xposition,
                grabY: pointer.y - table.yposition,
                width: event.currentTarget.offsetWidth,
                height: event.currentTarget.offsetHeight
            };
            setSelectedTableId(table.tableId);
        };
    }, []);

    // The mouse or finger moved over the floor: if a table is being dragged, move it
    const handleDragMove = (event) => {
        const drag = dragRef.current;
        if (!drag) return;

        const floor = canvasRef.current;
        const pointer = positionOnFloor(event, floor);

        let x = snapToGrid(pointer.x - drag.grabX);
        let y = snapToGrid(pointer.y - drag.grabY);

        // Keep the table on the floor. clientWidth / clientHeight are the floor's size
        // inside its border. (A floor with no size has not been laid out yet - only the
        // top and left edges can be enforced then.)
        if (floor.clientWidth > 0) x = Math.min(x, floor.clientWidth - drag.width);
        if (floor.clientHeight > 0) y = Math.min(y, floor.clientHeight - drag.height);
        x = Math.max(0, x);
        y = Math.max(0, y);

        setDraftTables(prevTables => prevTables.map(table =>
            table.tableId === drag.tableId && (table.xposition !== x || table.yposition !== y)
                ? { ...table, xposition: x, yposition: y }
                : table
        ));
    };

    const handleDragEnd = () => {
        dragRef.current = null;
    };

    // Turn the selected table one step clockwise
    const rotateSelected = () => {
        setDraftTables(prevTables => prevTables.map(table =>
            table.tableId === selectedTableId
                ? { ...table, rotation: ((table.rotation || 0) + ROTATE_STEP) % 360 }
                : table
        ));
    };

    // The tables whose position or rotation differs from what the backend has
    const changedTables = editing
        ? draftTables.filter(draft => {
            const original = tables.find(table => table.tableId === draft.tableId);
            return !original ||
                original.xposition !== draft.xposition ||
                original.yposition !== draft.yposition ||
                (original.rotation || 0) !== (draft.rotation || 0);
        })
        : [];

    const saveLayout = async () => {
        if (changedTables.length === 0) {
            cancelEditing();                                     // Nothing moved - nothing to save
            return;
        }

        setSaving(true);
        try {
            // One request for the whole layout. The backend saves all of it or none of it.
            const response = await apiFetch('/api/tables/layout', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    tables: changedTables.map(table => ({
                        tableId: table.tableId,
                        x: table.xposition,
                        y: table.yposition,
                        rotation: table.rotation || 0
                    }))
                })
            });

            if (!response.ok) {
                setErrorMessage('The layout could not be saved. Your changes are still on screen - try again.');
                return;                                          // Stay in edit mode so nothing is lost
            }

            setTables(draftTables);                              // Show the new layout right away, without waiting for the next refresh
            cancelEditing();
        } catch (error) {
            console.error('Error saving layout:', error);
            setErrorMessage('The layout could not be saved. Your changes are still on screen - try again.');
        } finally {
            setSaving(false);
        }
    };

    const shownTables = editing ? draftTables : tables;

    // Memoized table list - only re-renders when tables array actually changes
    const tableComponents = useMemo(() => {
        return shownTables.map(table => (
            <TableVisual
                key={table.tableId}
                table={table}
                onTableClick={handleTableClick}
                editing={editing}
                selected={editing && table.tableId === selectedTableId}
                onDragStart={handleDragStart}
            />
        ));
    }, [shownTables, handleTableClick, editing, selectedTableId, handleDragStart]);

    return (
        <div className="page-with-nav">
            <NavBar currentView="floorMap" setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />

            <div className="floor-map-page">
                <h2>FLOOR MAP</h2>
                {/* Drawn on a phone only, where EDIT LAYOUT is not offered (FloorMap.css) */}
                <WideScreenNote>The floor is drawn at its full size: scroll to see all of it, or use TABLES for a list. Rearranging the floor needs a tablet or a wider screen.</WideScreenNote>

                <div className="floor-toolbar">
                    {editing ? (
                        <>
                            <span className="floor-edit-hint">
                                Drag to move. Tap a table, then ROTATE to turn it.
                            </span>
                            <button className="floor-btn" onClick={rotateSelected} disabled={selectedTableId === null || saving}>
                                ROTATE
                            </button>
                            <button className="floor-btn" onClick={cancelEditing} disabled={saving}>
                                CANCEL
                            </button>
                            <button className="floor-btn floor-btn-save" onClick={saveLayout} disabled={saving || changedTables.length === 0}>
                                {saving ? 'SAVING...' : 'SAVE LAYOUT'}
                            </button>
                        </>
                    ) : (
                        // Only a manager may rearrange the floor (the backend refuses anyone else too)
                        canEditLayout && <button className="floor-btn" onClick={startEditing}>EDIT LAYOUT</button>
                    )}
                </div>

                {errorMessage && <p className="floor-error" role="alert">{errorMessage}</p>}

                <div
                    className={`floor-canvas ${editing ? 'editing' : ''}`}
                    ref={canvasRef}
                    data-testid="floor-canvas"
                    onMouseMove={editing ? handleDragMove : undefined}
                    onMouseUp={editing ? handleDragEnd : undefined}
                    onMouseLeave={editing ? handleDragEnd : undefined}
                    onTouchMove={editing ? handleDragMove : undefined}
                    onTouchEnd={editing ? handleDragEnd : undefined}
                >
                    {tableComponents}
                </div>
            </div>
        </div>
    );
}

export default FloorMap;
