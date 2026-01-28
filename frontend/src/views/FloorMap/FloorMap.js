import { useState, useEffect, useMemo, memo } from 'react';
import NavBar from '../../components/NavBar';
import './FloorMap.css';

/**
 * Memoized Table Component - prevents unnecessary re-renders
 * Only re-renders if table data actually changes
 */
const TableVisual = memo(({ table, onTableClick }) => {
    return (
        <div
            className={`table-visual ${table.shape} ${table.status} ${table.seatCount >= 6 ? 'large' : ''}`}
            style={{
                left: `${table.xposition}px`,
                top: `${table.yposition}px`
            }}
            onClick={() => onTableClick(table)}
        >
            <span className="table-label">{table.tableNumber}</span>
            <span className="table-seats">{table.seatCount} seats</span>
        </div>
    );
});

function FloorMap({ setCurrentView, setSelectedTable }) {
    const [tables, setTables] = useState([]);

    // Initial fetch - only runs once on mount
    useEffect(() => {
        fetch('http://localhost:8080/api/tables?quickOrders=false')
            .then(response => response.json())
            .then(data => setTables(data))
            .catch(error => console.error('Error fetching tables:', error));
    }, []);

    // Polling interval - runs every 3 seconds
    useEffect(() => {
        const interval = setInterval(() => {
            fetch('http://localhost:8080/api/tables?quickOrders=false')
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

    // Memoized table list - only re-renders when tables array actually changes
    const tableComponents = useMemo(() => {
        return tables.map(table => (
            <TableVisual 
                key={table.tableId} 
                table={table} 
                onTableClick={handleTableClick}
            />
        ));
    }, [tables, handleTableClick]);

    return (
        <div className="page-with-nav">
            <NavBar currentView="floorMap" setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />

            <div className="floor-map-page">
                <h2>FLOOR MAP</h2>

                <div className="floor-canvas">
                    {tableComponents}
                </div>
            </div>
        </div>
    );
}

export default FloorMap;