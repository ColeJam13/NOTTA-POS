import { useState, useEffect } from 'react';
import NavBar from './NavBar';
import './App.css';

function ActiveOrders({ setCurrentView }) {
  const [view, setView] = useState('BOH');
  const [orders, setOrders] = useState([]);
  const [orderItems, setOrderItems] = useState([]);

  useEffect(() => {
    fetch('http://localhost:8080/api/orders')
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
  }, []);

  return (
    <div className="page-with-nav">
        <NavBar currentView="activeOrders" setCurrentView={setCurrentView} />
            <div className="active-orders-page">
            <h2>ACTIVE ORDERS</h2>

            <div className="view-toggle">
                <button
                className={`toggle-btn ${view === 'FOH' ? 'active' : ''}`}
                onClick={() => setView('FOH')}
                >
                FOH VIEW
                </button>
                <button
                className={`toggle-btn ${view === 'BOH' ? 'active' : ''}`}
                onClick={() => setView('BOH')}
                >
                BOH VIEW
                </button>
            </div>

            <div className="orders-display">
                {orders.length === 0 ? (
                <p className="no-orders">NO ACTIVE ORDERS</p>
                ) : (
                orders.map(order => {
                    const items = orderItems.filter(item => item.orderId === order.orderId);
                    const filteredItems = view === 'BOH'
                    ? items.filter(item => item.status === 'fired' || item.status === 'completed')
                    : items;

                    if (filteredItems.length === 0) return null;

                    return (
                    <div key={order.orderId} className="order-card">
                        <h3>Table {order.tableId} - Order #{order.orderId}</h3>
                        <div className="order-items">
                        {filteredItems.map(item => (
                            <div key={item.orderItemId} className="order-item-row">
                            <span>{item.quantity}x</span>
                            <span>{item.menuItemId}</span>
                            <span className={`status-${item.status}`}>{item.status}</span>
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