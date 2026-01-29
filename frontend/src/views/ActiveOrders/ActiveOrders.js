import { useState, useEffect } from 'react';
import NavBar from '../../components/NavBar/NavBar';
import { formatTableName } from '../../utils/formatters';
import './ActiveOrders.css';

function ActiveOrders({ setCurrentView, setSelectedTable }) {
  const [view, setView] = useState('FOH');
  const [orders, setOrders] = useState([]);
  const [orderItems, setOrderItems] = useState([]);
  const [menuItems, setMenuItems] = useState([]);
  const [tables, setTables] = useState([]);

  
  useEffect(() => {
    fetch('http://localhost:8080/api/orders')                     // First useEffect - loads data on mount
      .then(response => response.json())
      .then(data => {
        console.log('Orders:', data);
        setOrders(data);
      })
      .catch(error => console.error('Error fetching orders:', error));

    fetch('http://localhost:8080/api/order-items')
      .then(response => response.json())
      .then(data => {
        console.log('Order items:', data);
        setOrderItems(data);
      })
      .catch(error => console.error('Error fetching order items:', error));

    fetch('http://localhost:8080/api/menu-items')
      .then(response => response.json())
      .then(data => setMenuItems(data))
      .catch(error => console.error('Error fetching menu items:', error));

    fetch('http://localhost:8080/api/tables')
      .then(response => response.json())
      .then(data => setTables(data))
      .catch(error => console.error('Error fetching tables:', error));
  }, []);

  
  useEffect(() => {
    const interval = setInterval(() => {
      fetch('http://localhost:8080/api/orders')                           // Second useEffect - polls every 3 seconds
        .then(response => response.json())
        .then(data => setOrders(data))
        .catch(error => console.error('Error fetching orders:', error));

      fetch('http://localhost:8080/api/order-items')
        .then(response => response.json())
        .then(data => setOrderItems(data))
        .catch(error => console.error('Error fetching order items:', error));
    }, 3000);

    return () => clearInterval(interval);
  }, []);

  return (
    <div className="page-with-nav">
      <NavBar currentView="activeOrders" setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />
            <div className="active-orders-page">
            <h2>ACTIVE ORDERS</h2>

            <div className="view-toggle">
                <button
                className={`toggle-btn ${view === 'FOH' ? 'active' : ''}`}              // FOH Toggle button for active orders
                onClick={() => setView('FOH')}
                >
                FOH VIEW
                </button>
                <button
                className={`toggle-btn ${view === 'BOH' ? 'active' : ''}`}              // BOH toggle button for active orders
                onClick={() => setView('BOH')}
                >
                BOH VIEW
                </button>
            </div>

            <div className="orders-display">
                {orders.length === 0 ? (
                <p className="no-orders">NO ACTIVE ORDERS</p>
                ) : (
                orders.filter(order => order.status === 'open').map(order => {
                    const items = orderItems.filter(item => item.orderId === order.orderId);
                    const filteredItems = view === 'BOH'
                    ? items.filter(item => item.status === 'pending' || item.status === 'fired' || item.status === 'completed')         // FOH and BOH available state views
                    : items.filter(item => item.status !== 'draft');

                    if (filteredItems.length === 0) return null;

                    const table = tables.find(t => t.tableId === order.tableId);

                    return (
                    <div key={order.orderId} className="order-card">
                        <h3>{formatTableName(table)} - Order #{order.orderId}</h3>
                        <div className="order-items">
                        {filteredItems.map(item => (
                            <div key={item.orderItemId} className="order-item-row">
                            <span>{item.quantity}x</span>                                                                                  
                            <span>{menuItems.find(m => m.menuItemId === item.menuItemId)?.name || `Item ${item.menuItemId}`}</span>
                            <span className={`status-${item.status}`}>{item.status}</span>

                            {view === 'BOH' && item.status === 'pending' && (
                              <button
                                className="btn-start"                                                   // changes status from pending to fired
                                onClick={async () => {
                                  await fetch(`http://localhost:8080/api/order-items/${item.orderItemId}/start`, {
                                    method: 'PUT'
                                  });

                                  setOrderItems(prevItems =>
                                    prevItems.map(i =>
                                      i.orderItemId === item.orderItemId
                                      ? { ...i, status: 'fired' }
                                      : i
                                    )
                                  );
                                }}
                              >
                                START
                              </button>
                            )}

                            {view === 'BOH' && item.status === 'fired' && (
                              <button
                                className="btn-complete"                                                                  // Complete order button appears when status is fired
                                onClick={async () => {
                                  await fetch(`http://localhost:8080/api/order-items/${item.orderItemId}/complete`, {
                                    method: 'PUT'
                                  });

                                  setOrderItems(prevItems =>
                                    prevItems.map(i =>
                                      i.orderItemId === item.orderItemId                                // changed state to completed
                                        ? {...i, status: 'completed' }
                                        : i
                                    )
                                  );
                                }}
                              >
                                COMPLETE
                              </button>
                            )}
                            </div>
                        ))}
                        </div>
                    </div>
                    );
                })
                )}
            </div>
        </div>
    </div>
  );
}

export default ActiveOrders;