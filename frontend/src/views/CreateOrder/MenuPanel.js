/**
 * Right-hand side of the Create Order screen: category tabs and the grid of
 * menu item cards for the selected category.
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
                <div key={item.menuItemId} className="menu-item-card" onClick={() => onAddItem(item)}>
                <h3>{item.name}</h3>
                <p className="price">${item.price.toFixed(2)}</p>
            </div>
            ))
        }
        </div>
    </div>
  );
}

export default MenuPanel;
