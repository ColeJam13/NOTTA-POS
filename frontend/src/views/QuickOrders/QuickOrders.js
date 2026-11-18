import TableChecks from '../../components/TableChecks';
import './QuickOrders.css';

/**
 * Quick Orders screen: open and closed checks for orders rung in without a
 * table (counter and to-go orders).
 *
 * Everything on screen is drawn by TableChecks, which this screen shares with
 * Tables. This file only says which checks to ask for and what the screen is
 * called.
 */
function QuickOrders({ setCurrentView, setSelectedTable }) {
    return (
        <TableChecks
            quickOrders={true}
            title="QUICK ORDERS"
            navKey="quickOrders"
            emptyActiveText="No active Quick Orders"
            emptyClosedText="No closed Quick Orders"
            setCurrentView={setCurrentView}
            setSelectedTable={setSelectedTable}
        />
    );
}

export default QuickOrders;
