import { useState, useEffect, useRef } from 'react';
import NavBar from '../../components/NavBar';
import { Lock } from 'lucide-react';
import PaymentModal from '../../components/PaymentModal';
import './CreateOrder.css';


function CreateOrder({ setCurrentView, selectedTable }) {
  const [menuItems, setMenuItems] = useState([]);
  const [selectedCategory, setSelectedCategory] = useState('Savory');
  const [orderItems, setOrderItems] = useState([])
  const [currentTableId] = useState(selectedTable?.tableId || 1);
  const [timerExpires, setTimerExpires] = useState(null);
  const [secondsLeft, setSecondsLeft] = useState(null);
  const [currentOrderId, setCurrentOrderId] = useState(null);
  const orderItemsRef = useRef(null);
  const [showDraftSaved, setShowDraftSaved] = useState(false);
  const [showPaymentModal, setShowPaymentModal] = useState(false);

useEffect(() => {
  if (selectedTable && selectedTable.status === 'occupied') {
    fetch(`http://localhost:8080/api/orders?tableId=${selectedTable.tableId}`)
      .then(response => response.json())
      .then(orders => {
        if (orders.length > 0) {
          const openOrders = orders.filter(o => o.status === 'open');
          
          if (openOrders.length > 0) {
                                                                                          // Use the FIRST order as the "current" order for adding new items
            setCurrentOrderId(openOrders[0].orderId);

                                                                                          // Fetch menu items first
            fetch('http://localhost:8080/api/menu-items')
              .then(response => response.json())
              .then(menuData => {
                                                                                          // Fetch items from ALL open orders
                const itemPromises = openOrders.map(order =>
                  fetch(`http://localhost:8080/api/order-items/order/${order.orderId}`)
                    .then(response => response.json())
                );

                                                                                          // Wait for all item fetches to complete
                Promise.all(itemPromises).then(allItemArrays => {
                                                                                          // Flatten all items into one array
                  const allItems = allItemArrays.flat();
                  
                  const formattedItems = allItems.map(item => ({
                    orderItemId: item.orderItemId,
                    menuItemId: item.menuItemId,
                    name: menuData.find(m => m.menuItemId === item.menuItemId)?.name,
                    price: item.price,
                    quantity: item.quantity,
                    status: item.status
                  }));
                  
                  console.log('Loaded all items from all orders:', formattedItems);
                  setOrderItems(formattedItems);
                });
              });
          }
        }
      })
      .catch(error => console.error('Error loading order:', error));
  }
}, [selectedTable]);

useEffect(() => {
  fetch('http://localhost:8080/api/menu-items')                             // fetch menu items
    .then(response => response.json())
    .then(data => {
      console.log('Menu items:', data);
      setMenuItems(data);
    })
    .catch(error => console.error('Error fetching menu items:', error));
  }, []);

  useEffect(() => {
    if (!timerExpires) return;                                       

    const interval = setInterval(() => {                              // timer logic
      const now = new Date();
      const diff = Math.floor((timerExpires - now) / 1000);

      if (diff <= 0) {
        console.log('Timer expired! Current items:', orderItems); 
        setSecondsLeft(0);
        setTimerExpires(null);
        clearInterval(interval);

      setOrderItems(prevItems => prevItems.map(item => {
        if (item.status === 'limbo') return { ...item, status: 'pending' };
        return item;                                                                    // Don't touch draft/pending/fired/completed items
      }));

      } else {
        setSecondsLeft(diff);
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [timerExpires, orderItems]);

    useEffect(() => {
    if (orderItemsRef.current) {
      orderItemsRef.current.scrollTop = orderItemsRef.current.scrollHeight;
    }
  }, [orderItems]);

  const sendOrder = async () => {
    try {
      let orderId = currentOrderId;

                                                                                    // If no current order, create a new one
      if (!orderId) {
        const orderResponse = await fetch('http://localhost:8080/api/orders', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            tableId: currentTableId,
            orderType: 'dine_in',
            status: 'open',
            serverName: selectedTable?.serverName || 'Unknown'
          })
        });
        const order = await orderResponse.json();
        console.log('Order created', order);
        orderId = order.orderId;
        setCurrentOrderId(orderId);

        await fetch(`http://localhost:8080/api/tables/${currentTableId}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json'},
          body: JSON.stringify({ status: 'occupied' })
        });
      }

      // Add only the DRAFT items that aren't already in the database
      const draftItems = orderItems.filter(item => item.status === 'draft');

      // Create all items in parallel and wait for ALL to complete
      const createPromises = draftItems
        .filter(item => !item.orderItemId)
        .map(item =>
          fetch('http://localhost:8080/api/order-items', {
            method: 'POST',
            headers: { 'Content-type': 'application/json' },
            body: JSON.stringify({
              orderId: orderId,
              menuItemId: item.menuItemId,
              quantity: item.quantity,
              price: item.price,
              status: 'draft'
            })
          })
        );

      // Wait for ALL items to be created before sending
      await Promise.all(createPromises);

      // Now send ALL draft items for this order (backend will only send items with status 'draft')
      const sendResponse = await fetch(`http://localhost:8080/api/order-items/order/${orderId}/send`, {
        method: 'POST'
      });
      const sentItems = await sendResponse.json();

      console.log('Sent items response:', sentItems);

      if (sentItems && sentItems.length > 0) {
        const expiresAt = new Date(sentItems[0].delayExpiresAt);
        console.log('Timer expires at:', expiresAt);
        console.log('Current time:', new Date());
        console.log('Seconds until expiration:', Math.floor((expiresAt - new Date()) / 1000));
        
        setTimerExpires(expiresAt);
        setSecondsLeft(15);
      }

                                                                                            // Change only draft items to limbo
      setOrderItems(prevItems => prevItems.map(item => {
        if (item.status === 'draft') return { ...item, status: 'limbo' };
        return item;
      }));

    } catch (error) {
      console.error('Error sending order:', error);
      alert('Failed to send order');
    }
  };

  return (                                                                          // change table in create order tab
    <div className="page-with-nav">
        <NavBar currentView="createOrder" setCurrentView={setCurrentView} />                      
            <div className="app">
            <div className="order-panel">
                <h2>Current Order - Table {selectedTable?.tableNumber || 'F1'}</h2>         

                <div className={`notification-bar ${
                  showDraftSaved ? 'notification-success' : 
                  secondsLeft !== null && secondsLeft > 0 ? 'notification-timer' : 
                  secondsLeft === 0 ? 'notification-locked' : 
                  'notification-default'
                }`}>
                  {showDraftSaved ? 'Draft saved! Items remain editable' :
                  secondsLeft !== null && secondsLeft > 0 ? `${secondsLeft} seconds to edit` :
                  secondsLeft === 0 ? 'Items locked and sent to prep station' :
                  orderItems.length === 0 ? 'Add items to order' :
                  'Ready to send or save as draft'}
                </div>

                <div className="order-items-list" ref={orderItemsRef}>
                {orderItems.map((item, index) => (                                                              // lock item and change status
                    <div key={index} className={`order-item ${
                      (item.status === 'pending' || item.status === 'fired' || item.status === 'completed') ? 'locked' : 
                      (item.status === 'draft' && item.orderItemId) ? 'saved-draft' : 
                      ''
                    }`}>            
                    <span>
                      {(item.status === 'pending' || item.status === 'fired' || item.status === 'completed') && <Lock size={14} className="lock-icon" />}
                      {item.name}
                    </span>
                    <span>${item.price.toFixed(2)}</span>
                    {(item.status === 'draft' || item.status === 'limbo') &&(
                      <button
                          className="btn-remove"                                                      // remove item
                          onClick={async () => {
                                                                                      // If item has an orderItemId, delete it from database
                          if (item.orderItemId) {
                              try {
                                  await fetch(`http://localhost:8080/api/order-items/${item.orderItemId}`, {
                                      method: 'DELETE'
                                  });
                              } catch (error) {
                                  console.error('Error deleting item:', error);
                              }
                          }

                                                                                          // Remove from local state
                          setOrderItems(orderItems.filter((_, i) => i !== index));

                          if (timerExpires) {
                              const newExpires = new Date(Date.now() + 15000);
                              setTimerExpires(newExpires);
                              setSecondsLeft(15);
                          }
                          }}
                      >
                          x
                      </button>
                    )}
                    </div>
                ))}
                </div>
                                                                                                    
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
                  <div className="action-row">
                    <button className="btn-save" onClick={async () => {
                      try {
                        let orderId = currentOrderId;

                                                                                                      // If no current order, create a new one
                        if (!orderId) {
                          const orderResponse = await fetch('http://localhost:8080/api/orders', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({
                              tableId: currentTableId,
                              orderType: 'dine_in',
                              status: 'open',
                              serverName: selectedTable?.serverName || 'Unknown'
                            })
                          });
                          const order = await orderResponse.json();
                          orderId = order.orderId;
                          setCurrentOrderId(orderId);

                                                                                                  // Mark table as occupied
                          await fetch(`http://localhost:8080/api/tables/${currentTableId}`, {
                            method: 'PUT',
                            headers: { 'Content-Type': 'application/json'},
                            body: JSON.stringify({ status: 'occupied' })
                          });
                        }

                                                                                                                        // Save only draft items that don't have an orderItemId (not yet in database)
                        const newDraftItems = orderItems.filter(item => item.status === 'draft' && !item.orderItemId);

                        for (const item of newDraftItems) {
                          const response = await fetch('http://localhost:8080/api/order-items', {
                            method: 'POST',
                            headers: { 'Content-type': 'application/json' },
                            body: JSON.stringify({
                              orderId: orderId,
                              menuItemId: item.menuItemId,
                              quantity: item.quantity,
                              price: item.price,
                              status: 'draft'
                            })
                          });
                          const createdItem = await response.json();
                          
                                                                              // Update the item in state with its orderItemId
                          item.orderItemId = createdItem.orderItemId;
                        }

                                                                              // Show success notification
                        setShowDraftSaved(true);

                                                                              // Hide after 3 seconds
                        setTimeout(() => {
                          setShowDraftSaved(false);
                        }, 3000);
                      } catch (error) {
                        console.error('Error saving draft:', error);
                        alert('Failed to save draft');
                      }
                    }}>SAVE DRAFT</button>

                    <button className="btn-send" onClick={async () => {
                      if (timerExpires && currentOrderId) {
                                                                                                                    // Call backend to send now
                        await fetch(`http://localhost:8080/api/order-items/order/${currentOrderId}/send-now`, {
                          method: 'POST'
                        });

                                                                            // Stop the timer
                        setSecondsLeft(0);
                        setTimerExpires(null);

                                                                                                        // Refetch the order items to get updated status from backend
                        const response = await fetch(`http://localhost:8080/api/order-items/order/${currentOrderId}`);
                        const updatedItems = await response.json();
                        
                                                                                              // Fetch menu items to format properly
                        const menuResponse = await fetch('http://localhost:8080/api/menu-items');
                        const menuData = await menuResponse.json();
                        
                        const formattedItems = updatedItems.map(item => ({
                          orderItemId: item.orderItemId,
                          menuItemId: item.menuItemId,
                          name: menuData.find(m => m.menuItemId === item.menuItemId)?.name,
                          price: item.price,
                          quantity: item.quantity,
                          status: item.status
                        }));
                        
                        setOrderItems(formattedItems);
                      } else {
                        sendOrder();
                      }
                    }}>
                        {timerExpires ? 'SEND NOW?' : 'SEND ORDER'}
                    </button>
                  </div>

                  <button 
                    className="btn-close-order" 
                    onClick={() => setShowPaymentModal(true)}
                    disabled={orderItems.length === 0}
                  >
                    CLOSE ORDER
                  </button>
                </div>
            </div>
                                                                                  
            <div className="menu-panel">                                                          
                <div className="category-tabs">
                <button
                    className={`category-tab ${selectedCategory === 'Savory' ? 'active' : ''}`}
                    onClick={() => setSelectedCategory('Savory')}                                       // Menu Category panel and buttons
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
                    .filter(item => item.category === selectedCategory)                             // maps the items to their buttons
                    .map(item => (
                    <div key ={item.menuItemId} className="menu-item-card" onClick={() => {
                        setOrderItems([...orderItems, {
                        menuItemId: item.menuItemId,
                        name: item.name,
                        price: item.price,
                        quantity: 1,
                        status: 'draft'
                        }]);

                        if (timerExpires) {
                        const newExpires = new Date(Date.now() + 15000);
                        setTimerExpires(newExpires);
                        setSecondsLeft(15);
                        }

                        if (secondsLeft === 0) {
                        setSecondsLeft(null);
                        }
                    }}>
                        <h3>{item.name}</h3>
                        <p className="price">${item.price.toFixed(2)}</p>
                    </div>
                    ))
                }
                </div>
            </div>
        </div>

        {showPaymentModal && (
          <PaymentModal
            order={{ orderId: currentOrderId }}
            orderItems={orderItems}
            onClose={() => setShowPaymentModal(false)}
            onPaymentSuccess={() => {
              setShowPaymentModal(false);
              alert('Payment processed successfully!');
              setOrderItems([]);
              setCurrentView('home');
            }}
          />
        )}
    </div>
  );
}

export default CreateOrder;