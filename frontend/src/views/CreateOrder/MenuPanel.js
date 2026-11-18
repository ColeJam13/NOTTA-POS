/**
 * Right-hand side of the Create Order screen: category tabs and the grid of
 * menu item cards for the selected category.
 *
 * An 86'd item (the kitchen is out of it) stays in the grid, greyed out and
 * marked 86'D, and cannot be tapped. It is shown on purpose: a server should
 * see that it is out, not wonder where it went.
 *
 * Styles come from CreateOrder.css (loaded by the parent view).
 */

// Category tabs, in display order. Each must match the "category" text on the menu items.
const CATEGORIES = [
  'Savory',
  'Sweet',
  'Build Your Own',
  'Snacks & Sides',
  'Beverages',
  'Cocktails',
  'Coffee'
];

function MenuPanel({ menuItems, selectedCategory, onSelectCategory, onAddItem }) {
  return (
    <div className="menu-panel">
        <div className="category-tabs">
        {CATEGORIES.map(category => (
            <button
                key={category}
                className={`category-tab ${selectedCategory === category ? 'active' : ''}`}
                onClick={() => onSelectCategory(category)}
            >
                {category.toUpperCase()}
            </button>
        ))}
        </div>

        <div className="menu-grid">
        {menuItems
            .filter(item => item.category === selectedCategory)
            .map(item => (
                <div
                  key={item.menuItemId}
                  className={`menu-item-card ${item.is86d ? 'is-86d' : ''}`}
                  aria-disabled={item.is86d ? 'true' : undefined}
                  onClick={() => { if (!item.is86d) onAddItem(item); }}       // An 86'd item cannot be added
                >
                <h3>{item.name}</h3>
                {item.is86d
                  ? <p className="eighty-six-label">86'D</p>
                  : <p className="price">${item.price.toFixed(2)}</p>}
            </div>
            ))
        }
        </div>
    </div>
  );
}

export default MenuPanel;
