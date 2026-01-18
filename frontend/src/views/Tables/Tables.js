import { useState, useEffect } from 'react';
import NavBar from '../../components/NavBar/NavBar';
import './Tables.css';

function Tables({ setCurrentView, setSelectedTable }) {
    const [view, setView] = useState('active');
    const [tables, setTables] = useState([]);
    const [orders, setOrders] = useState([]);
    const [orderItems, setOrderItems] = useState([]);
    const [payments, setPayments] = useState([]);
    const [menuItems, setMenuItems] = useState([]);
    const [selectedClosedOrder, setSelectedClosedOrder] = useState(null);

    useEffect(() => {                                                       // Load tables, orders, order items, payments, and menu items on mount
        fetch('http://localhost:8080/api/tables')
            .then(response => response.json())
            .then(data => setTables(data))
            .catch(error => console.error('Error fetching tables:', error));

        fetch('http://localhost:8080/api/orders')
            .then(response => response.json())
            .then(data => setOrders(data))
            .catch(error => console.error('Error fetching orders:', error));

        fetch('http://localhost:8080/api/order-items')
            .then(response => response.json())
            .then(data => setOrderItems(data))
            .catch(error => console.error('Error fetching order items:', error));

        fetch('http://localhost:8080/api/payments')
            .then(response => response.json())
            .then(data => setPayments(data))
            .catch(error => console.error('Error fetching payments:', error));

        fetch('http://localhost:8080/api/menu-items')
            .then(response => response.json())
            .then(data => setMenuItems(data))
            .catch(error => console.error('Error fetching menu items:', error));
    }, []);

    useEffect(() => {                                                       // Auto-refresh every 3 seconds
        const interval = setInterval(() => {
            fetch('http://localhost:8080/api/tables')
                .then(response => response.json())
                .then(data => setTables(data))
                .catch(error => console.error('Error fetching tables:', error));

            fetch('http://localhost:8080/api/orders')
                .then(response => response.json())
                .then(data => setOrders(data))
                .catch(error => console.error('Error fetching orders:', error));

            fetch('http://localhost:8080/api/order-items')
                .then(response => response.json())
                .then(data => setOrderItems(data))
                .catch(error => console.error('Error fetching order items:', error));

            fetch('http://localhost:8080/api/payments')
                .then(response => response.json())
                .then(data => setPayments(data))
                .catch(error => console.error('Error fetching payments:', error));

            fetch('http://localhost:8080/api/menu-items')
                .then(response => response.json())
                .then(data => setMenuItems(data))
                .catch(error => console.error('Error fetching menu items:', error));
        }, 3000);

        return () => clearInterval(interval);
    }, []);

                                                                            // Filter to only occupied tables with open orders
    const occupiedTables = tables.filter(table => {
        const hasOpenOrders = orders.some(o => o.tableId === table.tableId && o.status === 'open');
        return table.status === 'occupied' && hasOpenOrders;
    });

                                                                            // Helper function to get order info for a table (ACTIVE)
    const getTableInfo = (table) => {
        const tableOrders = orders.filter(o => o.tableId === table.tableId && o.status === 'open');
        if (tableOrders.length === 0) return null;

                                                                            // Get all items for all orders for this table
        const allItems = tableOrders.flatMap(order => 
            orderItems.filter(item => item.orderId === order.orderId)
        );

                                                                            // Count items by status
        const limboCount = allItems.filter(item => item.status === 'limbo').length;
        const pendingCount = allItems.filter(item => item.status === 'pending').length;
        const firedCount = allItems.filter(item => item.status === 'fired').length;
        const completedCount = allItems.filter(item => item.status === 'completed').length;

                                                                            // Calculate total bill
        const total = allItems.reduce((sum, item) => sum + item.price, 0);

                                                                            // Get oldest order time
        const oldestOrder = tableOrders.sort((a, b) => a.createdAt - b.createdAt)[0];
        const timeAgo = getTimeAgo(oldestOrder.createdAt);

                                                                            // Determine border color based on status
        let borderColor = 'default';
        if (completedCount === allItems.length) borderColor = 'green';
        else if (limboCount > 0) borderColor = 'purple';
        else if (pendingCount > 0 || firedCount > 0) borderColor = 'yellow';

        return {
            total,
            itemCount: allItems.length,
            limboCount,
            pendingCount,
            firedCount,
            completedCount,
            timeAgo,
            borderColor
        };
    };

                                                                            // Helper function to get closed table info
    const getClosedTableInfo = (table) => {
        const closedOrders = orders.filter(o => o.tableId === table.tableId && o.status === 'closed');
        
        return closedOrders.map(order => {
            const payment = payments.find(p => p.orderId === order.orderId);
            const items = orderItems.filter(item => item.orderId === order.orderId);
            
                                                                            // Join menu item names with order items
            const itemsWithNames = items.map(item => ({
                ...item,
                name: menuItems.find(m => m.menuItemId === item.menuItemId)?.name || 'Unknown Item'
            }));
            
            const subtotal = items.reduce((sum, item) => sum + item.price, 0);
            const timeAgo = getTimeAgo(order.closedAt || order.createdAt);

            return {
                orderId: order.orderId,
                tableNumber: table.tableNumber,
                serverName: order.serverName,
                items: itemsWithNames,
                subtotal,
                tipAmount: payment?.tipAmount || 0,
                paymentMethod: payment?.paymentMethod || 'Unknown',
                total: subtotal + (payment?.tipAmount || 0),
                timeAgo,
                createdAt: order.createdAt,
                closedAt: order.closedAt
            };
        });
    };

                                                                            // Helper to calculate time ago
    const getTimeAgo = (timestamp) => {
        const now = Date.now();
        const created = new Date(timestamp).getTime();
        const diff = Math.floor((now - created) / 1000 / 60); // minutes
        if (diff < 60) return `${diff}m ago`;
        const hours = Math.floor(diff / 60);
        return `${hours}h ${diff % 60}m ago`;
    };

                                                                            // Helper to format timestamp as date/time
    const formatDateTime = (timestamp) => {
        if (!timestamp) return 'N/A';
        const date = new Date(timestamp);
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const year = date.getFullYear();
        let hours = date.getHours();
        const minutes = String(date.getMinutes()).padStart(2, '0');
        const ampm = hours >= 12 ? 'PM' : 'AM';
        hours = hours % 12 || 12;
        return `${month}/${day}/${year} ${hours}:${minutes} ${ampm}`;
    };

                                                                            // Get all tables with closed orders
    const tablesWithClosedOrders = tables.filter(table => 
        orders.some(o => o.tableId === table.tableId && o.status === 'closed')
    );

    return (
        <div className="page-with-nav">
            <NavBar currentView="activeTables" setCurrentView={setCurrentView} />
            <div className="tables-page">
                <h2>TABLES</h2>

                <div className="view-toggle">
                    <button
                        className={`toggle-btn ${view === 'active' ? 'active' : ''}`}
                        onClick={() => setView('active')}
                    >
                        ACTIVE
                    </button>
                    <button
                        className={`toggle-btn ${view === 'closed' ? 'active' : ''}`}
                        onClick={() => setView('closed')}
                    >
                        CLOSED
                    </button>
                </div>

                {view === 'active' && (
                    <div className="tables-grid">
                        {occupiedTables.map(table => {
                            const info = getTableInfo(table);
                            if (!info) return null;

                            return (
                                <div 
                                    key={table.tableId} 
                                    className={`table-card border-${info.borderColor}`}
                                    onClick={() => {
                                        setSelectedTable(table);
                                        setCurrentView('createOrder');
                                    }}
                                >
                                    <h3 className="table-card-number">{table.tableNumber}</h3>
                                    <div className="table-card-total">${info.total.toFixed(2)}</div>
                                    <div className="table-card-items">{info.itemCount} items</div>
                                    <div className="table-card-status">
                                        {info.limboCount > 0 && `${info.limboCount}L `}
                                        {info.pendingCount > 0 && `${info.pendingCount}P `}
                                        {info.firedCount > 0 && `${info.firedCount}F `}
                                        {info.completedCount > 0 && `${info.completedCount}C`}
                                    </div>
                                    <div className="table-card-server">Server: {table.server_name || table.serverName || 'N/A'}</div>
                                    <div className="table-card-time">{info.timeAgo}</div>
                                </div>
                            );
                        })}

                        {occupiedTables.length === 0 && (
                            <p className="no-tables">No active tables</p>
                        )}
                    </div>
                )}

                {view === 'closed' && (
                    <div className="tables-grid">
                        {tablesWithClosedOrders.flatMap(table => {
                            const closedOrders = getClosedTableInfo(table);
                            
                            return closedOrders.map(orderInfo => (
                                <div 
                                    key={orderInfo.orderId} 
                                    className="table-card closed-card"
                                    onClick={() => setSelectedClosedOrder(orderInfo)}
                                >
                                    <h3 className="table-card-number">{orderInfo.tableNumber}</h3>
                                    <div className="table-card-total">${orderInfo.total.toFixed(2)}</div>
                                    <div className="table-card-items">{orderInfo.items.length} items</div>
                                    <div className="table-card-server">Server: {orderInfo.serverName || 'N/A'}</div>
                                    <div className="table-card-payment">{orderInfo.paymentMethod.replace('_', ' ').toUpperCase()}</div>
                                    <div className="table-card-time">{orderInfo.timeAgo}</div>
                                </div>
                            ));
                        })}

                        {tablesWithClosedOrders.length === 0 && (
                            <p className="no-tables">No closed tables</p>
                        )}
                    </div>
                )}
            </div>

            {selectedClosedOrder && (
                <div className="modal-overlay" onClick={() => setSelectedClosedOrder(null)}>
                    <div className="modal-content" onClick={(e) => e.stopPropagation()}>
                        <button className="modal-close" onClick={() => setSelectedClosedOrder(null)}>×</button>
                        
                        <h2>Table {selectedClosedOrder.tableNumber}</h2>
                        <div className="modal-info">
                            <p><strong>Server:</strong> {selectedClosedOrder.serverName || 'N/A'}</p>
                            <p><strong>Order Created:</strong> {formatDateTime(selectedClosedOrder.createdAt)}</p>
                            <p><strong>Order Closed:</strong> {formatDateTime(selectedClosedOrder.closedAt)}</p>
                            <p><strong>Payment Method:</strong> {selectedClosedOrder.paymentMethod.replace('_', ' ').toUpperCase()}</p>
                        </div>

                        <div className="modal-items">
                            <h3>Order Items</h3>
                            {selectedClosedOrder.items.map((item, index) => (
                                <div key={index} className="modal-item">
                                    <span>{item.name}</span>
                                    <span>${item.price.toFixed(2)}</span>
                                </div>
                            ))}
                        </div>

                        <div className="modal-totals">
                            <div className="modal-total-row">
                                <span>Subtotal:</span>
                                <span>${selectedClosedOrder.subtotal.toFixed(2)}</span>
                            </div>
                            <div className="modal-total-row">
                                <span>Tip:</span>
                                <span>${selectedClosedOrder.tipAmount.toFixed(2)}</span>
                            </div>
                            <div className="modal-total-row final">
                                <span>Total:</span>
                                <span>${selectedClosedOrder.total.toFixed(2)}</span>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

export default Tables;