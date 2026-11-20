import { useState, useEffect, useCallback } from 'react';
import NavBar from '../../components/NavBar';
import { apiFetch, getJson } from '../../services/api';
import './EightySixBoard.css';

// How often the board asks the backend for the latest menu, in milliseconds
const REFRESH_MS = 3000;

/**
 * The 86 board: where the kitchen, bar, and managers mark an item as out
 * ("86'd") and put it back on.
 *
 * An 86'd item stays on the menu. On the order screen it is greyed out and
 * cannot be tapped, so servers can see it is out instead of wondering where
 * it went. That is different from taking an item off the menu (isActive).
 *
 * The top of the board lists what is 86'd right now. Below it is the whole
 * menu by category; tap an item to 86 it, tap again to put it back on.
 */
function EightySixBoard({ setCurrentView, setSelectedTable }) {
  const [menuItems, setMenuItems] = useState([]);
  const [errorMessage, setErrorMessage] = useState('');

  const loadMenu = useCallback(() => {
    getJson('/api/menu-items')
      .then(data => setMenuItems(data))
      .catch(error => console.error('Error fetching menu items:', error));
  }, []);

  useEffect(() => {
    loadMenu();                                                   // Load on mount...
    const interval = setInterval(loadMenu, REFRESH_MS);           // ...then keep in step with changes made on other screens
    return () => clearInterval(interval);
  }, [loadMenu]);

  // 86 an item, or put it back on, then show the change right away
  // instead of waiting for the next refresh
  const toggle86 = async (item) => {
    const action = item.is86d ? 'un-86' : '86';
    try {
      const response = await apiFetch(`/api/menu-items/${item.menuItemId}/${action}`, { method: 'PUT' });

      if (!response.ok) {
        setErrorMessage(`Could not update ${item.name}. Try again.`);
        return;                                                   // Leave the screen as-is if the backend refused
      }

      const updated = await response.json();
      setErrorMessage('');
      setMenuItems(prevItems => prevItems.map(existing =>
        existing.menuItemId === updated.menuItemId ? updated : existing
      ));
    } catch (error) {
      console.error('Error updating 86 status:', error);
      setErrorMessage(`Could not update ${item.name}. Try again.`);
    }
  };

  const eightySixed = menuItems.filter(item => item.is86d);

  // Categories in the order they first appear on the menu
  const categories = [...new Set(menuItems.map(item => item.category))];

  return (
    <div className="page-with-nav">
      <NavBar currentView="eightySix" setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />
      <div className="eighty-six-page">
        <h2>86 BOARD</h2>

        {errorMessage && <p className="eighty-six-error" role="alert">{errorMessage}</p>}

        {/* What is out right now */}
        <section className="eighty-six-now" aria-label="86'd now">
          <h3>86'D NOW ({eightySixed.length})</h3>
          {eightySixed.length === 0 ? (
            <p className="eighty-six-none">Nothing is 86'd</p>
          ) : (
            <div className="eighty-six-now-list">
              {eightySixed.map(item => (
                <button
                  key={item.menuItemId}
                  className="eighty-six-chip"
                  aria-label={`Put ${item.name} back on`}
                  onClick={() => toggle86(item)}
                >
                  <span>{item.name}</span>
                  <small>TAP TO PUT BACK ON</small>
                </button>
              ))}
            </div>
          )}
        </section>

        {/* The whole menu, one block per category */}
        {categories.map(category => (
          <section key={category} className="eighty-six-category" aria-label={category}>
            <h3>{category.toUpperCase()}</h3>
            <div className="eighty-six-grid">
              {menuItems
                .filter(item => item.category === category)
                .map(item => (
                  <button
                    key={item.menuItemId}
                    className={`eighty-six-item ${item.is86d ? 'is-86d' : ''}`}
                    aria-pressed={item.is86d}                     // Tells screen readers (and tests) whether the item is 86'd
                    onClick={() => toggle86(item)}
                  >
                    <span className="eighty-six-item-name">{item.name}</span>
                    <span className="eighty-six-item-status">{item.is86d ? "86'D" : 'AVAILABLE'}</span>
                  </button>
                ))}
            </div>
          </section>
        ))}
      </div>
    </div>
  );
}

export default EightySixBoard;
