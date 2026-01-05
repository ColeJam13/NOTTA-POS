import { useState, useEffect } from 'react';
import './App.css';


function App() {
  const [menuItems, setMenuItems] = useState([]);
  const [selectedCategory, setSelectedCategory] = useState('Savory');
  const [orderItems, setOrderItems] = useState([])
  const [currentTableId, setCurrentTableId] = useState(1);
  const [timerExpires, setTimerExpires] = useState(null);
  const [secondsLeft, setSecondsLeft] = useState(null);

useEffect(() => {
  fetch('http://localhost:8080/api/menu-items')
    .then(response => response.json())
    .then(data => {
      console.log('Menu items:', data);
      setMenuItems(data);
    })
    .catch(error => console.error('Error fetching menu items:', error));
  }, []);

  useEffect(() => {
    if (!timerExpires) return;

    const interval = setInterval(() => {
      const now = new Date();
      const diff = Math.floor((timerExpires - now) / 1000);

      if (diff <= 0) {
        setSecondsLeft(0);
        setTimerExpires(null);
        clearInterval(interval);
        alert('Timer expired! Items locked and sent to kitchen.');
      } else {
        setSecondsLeft(diff);
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [timerExpires]);

  const sendOrder = async () => {
    try {

      const orderResponse = await fetch('http://localhost:8080/api/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          tableId: currentTableId,
          orderType: 'dine_in',
          status: 'open'
        })
      });
      const order = await orderResponse.json();
      console.log('Order created', order);

      for (const item of orderItems) {
        await fetch('http://localhost:8080/api/order-items', {
          method: 'POST',
          headers: { 'Content-type': 'application/json' },
          body: JSON.stringify({
            orderId: order.orderId,
            menuItemId: item.menuItemId,
            quantity: item.quantity,
            price: item.price
          })
        });
      }

      const sendResponse = await fetch(`http://localhost:8080/api/order-items/order/${order.orderId}/send`, {
        method: 'POST'
      });
      const sentItems = await sendResponse.json();

      const expiresAt = new Date(sentItems[0].delayExpiresAt);
      setTimerExpires(expiresAt);
      setSecondsLeft(15);

      alert('Order sent! Timer started.');
    } catch (error) {
      console.error('Error sending order:', error);
      alert('Failed to send order');
    }
  };

  return (
    <div className="app">
      <header className="app-header">
        <h1 className="logo">NOTA-POS</h1>
      </header>

    <div className="order-panel">
      <h2>Current Order - Table F1</h2>

      {secondsLeft !== null && secondsLeft > 0 && (
        <div className="timer-display">
          {secondsLeft} seconds to edit
        </div>
      )}

      {secondsLeft === 0 && (
        <div className="timer-locked">
          Items locked and sent to the kitchen
        </div>
      )}

        <div className="order-items-list">
          {orderItems.map((item, index) => (
            <div key={index} className="order-item">
              <span>{item.name}</span>
              <span>${item.price.toFixed(2)}</span>
              <button
                className="btn-remove"
                onClick={() => {
                  setOrderItems(orderItems.filter((_, i) => i !== index))
                }}
              >
                x
              </button>
            </div>
          ))}
        </div>

        {orderItems.length === 0 && (
          <p className="empty-order">No items added yet</p>
        )}

      <div className="order-totals">
        <div className="total-row">
          <span>Subtotal:</span>
          <span>${orderItems.reduce((sum, item) => sum + item.price, 0).toFixed(2)}</span>
        </div>
        <div className="total-row">
          <span>Tax (3%):</span>
          <span>${(orderItems.reduce((sum, item) => sum + item.price, 0) * 0.03).toFixed(2)}</span>
        </div>
        <div className="total-row total">
          <span>TOTAL:</span>
          <span>${(orderItems.reduce((sum, item) => sum + item.price, 0) * 1.03).toFixed(2)}</span>
        </div>
      </div>
        <div className="order-actions">
          <button className="btn-save">SAVE DRAFT</button>
          <button className="btn-send" onClick={sendOrder}>SEND TO KITCHEN</button>
        </div>
      </div>

      <div className="menu-panel">
        <div className="category-tabs">
          <button
            className={`category-tab ${selectedCategory === 'Savory' ? 'active' : ''}`}
            onClick={() => setSelectedCategory('Savory')}
          >
            SAVORY
          </button>
          <button
            className={`category-tab ${selectedCategory === 'Sweet' ? 'active' : ''}`}
            onClick={() => setSelectedCategory('Sweet')}
          >
            SWEET
          </button>
          <button
            className={`category-tab ${selectedCategory === 'Build Your Own' ? 'active' : ''}`}
            onClick={() => setSelectedCategory('Build Your Own')}
          >
            BUILD YOUR OWN
          </button>
          <button
            className={`category-tab ${selectedCategory === 'Snacks & Sides' ? 'active' : ''}`}
            onClick={() => setSelectedCategory('Snacks & Sides')}
          >
            SNACKS & SIDES
          </button>
          <button
            className={`category-tab ${selectedCategory === 'Beverages' ? 'active' : ''}`}
            onClick={() => setSelectedCategory('Beverages')}
          >
            BEVERAGES
          </button>
          <button
            className={`category-tab ${selectedCategory === 'Cocktails' ? 'active' : ''}`}
            onClick={() => setSelectedCategory('Cocktails')}
          >
            COCKTAILS
          </button>
          <button
            className={`category-tab ${selectedCategory === 'Coffee' ? 'active' : ''}}`}
            onClick={() => setSelectedCategory('Coffee')}
          >
            COFFEE
          </button>
        </div>
        <div className="menu-grid">
          {menuItems
            .filter(item => item.category === selectedCategory)
            .map(item => (
              <div key ={item.menuItemId} className="menu-item-card" onClick={() => {
                setOrderItems([...orderItems, {
                  menuItemId: item.menuItemId,
                  name: item.name,
                  price: item.price,
                  quantity: 1
                }]);
              }}>
                <h3>{item.name}</h3>
                <p className="price">${item.price.toFixed(2)}</p>
              </div>
            ))
          }
        </div>
      </div>
    </div>
  );
}

export default App;