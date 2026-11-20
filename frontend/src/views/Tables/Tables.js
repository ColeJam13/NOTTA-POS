import TableChecks from '../../components/TableChecks';
import './Tables.css';

/**
 * Tables screen: open and closed checks for the restaurant's tables
 * (Quick Orders have their own screen).
 *
 * Everything on screen is drawn by TableChecks, which this screen shares with
 * Quick Orders. This file only says which tables to ask for and what the
 * screen is called.
 */
function Tables({ setCurrentView, setSelectedTable }) {
    return (
        <TableChecks
            quickOrders={false}
            title="TABLES"
            navKey="activeTables"
            emptyActiveText="No active tables"
            emptyClosedText="No closed tables"
            setCurrentView={setCurrentView}
            setSelectedTable={setSelectedTable}
        />
    );
}

export default Tables;
