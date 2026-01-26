import { useState, useEffect } from 'react';
import NavBar from '../../components/NavBar';
import './FloorMap.css';

function FloorMap({ setCurrentView, setSelectedTable }) {
    const [tables, setTables] = useState([]);

    useEffect(() => {
        fetch('http://localhost:8080/api/tables')
            .then(response => response.json())
            .then(data => {
                console.log('Table:', data);
                setTables(data);
            })
            .catch(error => console.error('Error fetching tables:', error));
    },      []);

    useEffect(() => {
        const interval = setInterval(() => {
            fetch('http://localhost:8080/api/tables')
                .then(response => response.json())
                .then(data => setTables(data))
                .catch(error => console.error('Error fetching tables:', error));
        }, 3000);

        return () => clearInterval(interval);
    }, []);

    return (
        <div className="page-with-nav">
            <NavBar currentView="floorMap" setCurrentView={setCurrentView} />

            <div className="floor-map-page">
                <h2>FLOOR MAP</h2>

                <div className="floor-canvas">
                    {tables
                        .filter(table => table.section !== 'Quick Orders' && !table.tableNumber?.startsWith('QO')) // Filter out Quick Orders
                        .map(table => (
                        <div
                            key={table.tableId}
                            className={`table-visual ${table.shape} ${table.status} ${table.seatCount >= 6 ? 'large' : ''}`}
                            style={{
                                left: `${table.xposition}px`,
                                top: `${table.yposition}px`
                            }}
                            onClick={() => {
                                setSelectedTable(table);
                                setCurrentView('createOrder');
                            }}
                        >
                            <span className="table-label">{table.tableNumber}</span>
                            <span className="table-seats">{table.seatCount} seats</span>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
}

export default FloorMap;