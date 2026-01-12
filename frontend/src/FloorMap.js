import { useState, useEffect } from 'react';
import NavBar from './NavBar';
import './App.css';

function FloorMap({ setCurrentView }) {
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

    return (
        <div className="page-with-nav">
            <NavBar currentView="floor-map-page" setCurrentView={setCurrentView} />

            <div className="floor-map-page">
                <h2>FLOOR MAP</h2>

                <div className="floor-canvas">
                    {tables.map(table => (
                        <div
                            key={table.tableId}
                            className={`table-visual ${table.shape} ${table.status}`}
                            style={{
                                left: `${table.xPosition}px`,
                                top: `${table.yPosition}px`
                            }}
                            onClick={() => {
                                console.log('Clicked table:', table.tableNumber);

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