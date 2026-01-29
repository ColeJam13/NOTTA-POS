import { useState, useEffect, useRef } from 'react';
import NavBar from '../../components/NavBar';
import { Lock } from 'lucide-react';
import PaymentModal from '../../components/PaymentModal';
import { formatTableName } from '../../utils/formatters';
import './CreateOrder.css';


function CreateOrder({ setCurrentView, selectedTable, setSelectedTable }) {
  const [menuItems, setMenuItems] = useState([]);
  const [selectedCategory, setSelectedCategory] = useState('Savory');
  const [orderItems, setOrderItems] = useState([])
  const [currentTable, setCurrentTable] = useState(selectedTable); // Changed: store entire table object instead of just ID
  const [timerExpires, setTimerExpires] = useState(null);
  const [secondsLeft, setSecondsLeft] = useState(null);
  const [currentOrderId, setCurrentOrderId] = useState(null);
  const orderItemsRef = useRef(null);
  const [showDraftSaved, setShowDraftSaved] = useState(false);
  const [showPaymentModal, setShowPaymentModal] = useState(false);

// Set initial table state - if selectedTable exists, use it. Otherwise null (Quick Order will be created on Save/Send)
useEffect(() => {
  if (selectedTable) {
    setCurrentTable(selectedTable);
  } else {
    // No table selected - will create Quick Order when user saves/sends
    setCurrentTable(null);
  }
}, [selectedTable]);

// Helper function to create a Quick Order table
const createQuickOrderTable = async () => {
  try {
    console.log('Creating Quick Order table...');
    
    // 1. Get all existing tables to find highest QO number
    const tablesResponse = await fetch('http://localhost:8080/api/tables');
    const allTables = await tablesResponse.json();
    
    // 2. Filter for Quick Order tables (starting with "QO")
    const quickOrders = allTables.filter(t => t.tableNumber && t.tableNumber.startsWith('QO'));
    
    // 3. Find the highest number and increment
    let nextQONumber = 1;
    if (quickOrders.length > 0) {
      const qoNumbers = quickOrders.map(t => {
        const num = parseInt(t.tableNumber.substring(2)); // Remove "QO" prefix
        return isNaN(num) ? 0 : num;
      });
      nextQONumber = Math.max(...qoNumbers) + 1;
    }
    
    const newTableNumber = `QO${nextQONumber}`;
    console.log('Creating Quick Order:', newTableNumber);
    
    // 4. Create the new Quick Order table
    const createResponse = await fetch('http://localhost:8080/api/tables', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        tableNumber: newTableNumber,
        section: 'Quick Orders',
        seatCount: 0,
        status: 'available',
        isQuickOrder: true
      })
    });
    
    if (!createResponse.ok) {
      throw new Error('Failed to create Quick Order table');
    }
    
    const newTable = await createResponse.json();
    console.log('Successfully created Quick Order table:', newTable);
    return newTable;
    
  } catch (error) {
    console.error('Error creating Quick Order:', error);
    throw error;
  }
};

// Load existing order if table is occupied
useEffect(() => {
  if (currentTable && currentTable.status === 'occupied') {
    fetch(`http://localhost:8080/api/orders?tableId=${currentTable.tableId}`)
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
}, [currentTable]); // Changed dependency from selectedTable to currentTable

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
      setShowDraftSaved(false);  // Clear the draft notification when sending
      let orderId = currentOrderId;
      let tableToUse = currentTable;

      // If no table exists yet (Quick Order scenario), create one now
      if (!tableToUse) {
        tableToUse = await createQuickOrderTable();
        setCurrentTable(tableToUse);
      }

      // If no current order, create a new one
      if (!orderId) {
        const orderResponse = await fetch('http://localhost:8080/api/orders', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            tableId: tableToUse.tableId, // Changed: use tableToUse instead of currentTable
            orderType: 'dine_in',
            status: 'open',
            serverName: tableToUse.serverName || 'Unknown'
          })
        });
        const order = await orderResponse.json();
        console.log('Order created', order);
        orderId = order.orderId;
        setCurrentOrderId(orderId);

        await fetch(`http://localhost:8080/api/tables/${tableToUse.tableId}`, { // Changed: use tableToUse
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
          
          setTimerExpires(expiresAt);
          setSecondsLeft(15);  // Start at 15, let interval handle countdown
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

  return (
    <div className="page-with-nav">
      <NavBar currentView="createOrder" setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />                    
            <div className="app">
            <div className="order-panel">
                <h2>Current Order - {currentTable ? formatTableName(currentTable) : 'New Quick Order'}</h2>

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
                {orderItems.map((item, index) => (
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
                          className="btn-remove"
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
                                const newExpires = new Date(Date.now() + 16000);
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
                        let tableToUse = currentTable;

                        // If no table exists yet (Quick Order scenario), create one now
                        if (!tableToUse) {
                          tableToUse = await createQuickOrderTable();
                          setCurrentTable(tableToUse);
                        }

                        // If no current order, create a new one
                        if (!orderId) {
                          const orderResponse = await fetch('http://localhost:8080/api/orders', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({
                              tableId: tableToUse.tableId, // Changed: use tableToUse
                              orderType: 'dine_in',
                              status: 'open',
                              serverName: tableToUse.serverName || 'Unknown'
                            })
                          });
                          const order = await orderResponse.json();
                          orderId = order.orderId;
                          setCurrentOrderId(orderId);

                          // Mark table as occupied
                          await fetch(`http://localhost:8080/api/tables/${tableToUse.tableId}`, { // Changed: use tableToUse
                            method: 'PUT',
                            headers: { 'Content-Type': 'application/json'},
                            body: JSON.stringify({ status: 'occupied' })
                          });
                        }
                        // Save only draft items that don't have an orderItemId (not yet in database)
                        const newDraftItems = orderItems.filter(item => item.status === 'draft' && !item.orderItemId);

                        const savedItems = [];
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
                          savedItems.push({ ...item, orderItemId: createdItem.orderItemId });
                        }

                        // Update state properly - map through and replace saved items
                        setOrderItems(prevItems => 
                          prevItems.map(item => {
                            const savedItem = savedItems.find(s => 
                              s.menuItemId === item.menuItemId && !item.orderItemId
                            );
                            return savedItem || item;
                          })
                        );

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
                        <div key ={item.menuItemId} className="menu-item-card" onClick={async () => {
                            // If timer is active and we have an order, create and send immediately
                            if (timerExpires && currentOrderId) {
                              try {
                                // Create item
                                const createRes = await fetch('http://localhost:8080/api/order-items', {
                                  method: 'POST',
                                  headers: { 'Content-type': 'application/json' },
                                  body: JSON.stringify({
                                    orderId: currentOrderId,
                                    menuItemId: item.menuItemId,
                                    quantity: 1,
                                    price: item.price,
                                    status: 'draft'
                                  })
                                });
                                const created = await createRes.json();
                                
                                // Send it immediately
                                await fetch(`http://localhost:8080/api/order-items/order/${currentOrderId}/send`, {
                                  method: 'POST'
                                });
                                
                                // Add to state as 'limbo' with orderItemId
                                setOrderItems([...orderItems, {
                                  orderItemId: created.orderItemId,
                                  menuItemId: item.menuItemId,
                                  name: item.name,
                                  price: item.price,
                                  quantity: 1,
                                  status: 'limbo'
                                }]);
                                
                                // Reset timer
                                const newExpires = new Date(Date.now() + 16000);
                                setTimerExpires(newExpires);
                                setSecondsLeft(15);
                              } catch (error) {
                                console.error('Error adding item during timer:', error);
                              }
                            } else {
                              // No timer - normal behavior (add as draft)
                              setOrderItems([...orderItems, {
                                menuItemId: item.menuItemId,
                                name: item.name,
                                price: item.price,
                                quantity: 1,
                                status: 'draft'
                              }]);
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
            setOrderItems([]);
            setCurrentOrderId(null);
            setCurrentTable(null);
            setSelectedTable(null);
          }}
        />
      )}
    </div>
  );
}

export default CreateOrder;