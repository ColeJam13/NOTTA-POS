-- ============================================================================
-- Nota-POS: Seed Data for Peach Blossom Eatery (FIXED)
-- ============================================================================
-- Real menu data from: https://www.peachblossomeatery.com/menus/
-- All food items → Kitchen prep station
-- All beverages → Bar prep station
-- ============================================================================

-- ============================================================================
-- PREP STATIONS
-- ============================================================================
INSERT INTO prep_stations (prep_station_id, name, description, is_active, created_at) VALUES
(1, 'Kitchen', 'Main kitchen prep station', true, CURRENT_TIMESTAMP),
(2, 'Bar', 'Bar prep station', true, CURRENT_TIMESTAMP);

-- ============================================================================
-- MODIFIER GROUPS
-- ============================================================================

-- Build Your Own - Protein Choice
INSERT INTO modifier_groups (modifier_group_id, name, description, is_required, max_selections, is_active, created_at) VALUES
(1, 'Protein Choice', 'Choose your protein', true, 1, true, CURRENT_TIMESTAMP);

-- Build Your Own - Bread Choice
INSERT INTO modifier_groups (modifier_group_id, name, description, is_required, max_selections, is_active, created_at) VALUES
(2, 'Bread Choice', 'Choose your bread', true, 1, true, CURRENT_TIMESTAMP);

-- Build Your Own - Side Choice
INSERT INTO modifier_groups (modifier_group_id, name, description, is_required, max_selections, is_active, created_at) VALUES
(3, 'Side Choice', 'Choose your side', true, 1, true, CURRENT_TIMESTAMP);

-- Pancake Flavor
INSERT INTO modifier_groups (modifier_group_id, name, description, is_required, max_selections, is_active, created_at) VALUES
(4, 'Pancake Flavor', 'Choose your pancake flavor', true, 1, true, CURRENT_TIMESTAMP);

-- Add-Ons (Optional)
INSERT INTO modifier_groups (modifier_group_id, name, description, is_required, max_selections, is_active, created_at) VALUES
(5, 'Add-Ons', 'Optional add-ons', false, 5, true, CURRENT_TIMESTAMP);

-- Wrap Additions (Optional)
INSERT INTO modifier_groups (modifier_group_id, name, description, is_required, max_selections, is_active, created_at) VALUES
(6, 'Wrap Additions', 'Optional wrap additions', false, 5, true, CURRENT_TIMESTAMP);

-- Fries Size
INSERT INTO modifier_groups (modifier_group_id, name, description, is_required, max_selections, is_active, created_at) VALUES
(7, 'Fries Size', 'Choose fries size', true, 1, true, CURRENT_TIMESTAMP);

-- Coffee Add-Ins
INSERT INTO modifier_groups (modifier_group_id, name, description, is_required, max_selections, is_active, created_at) VALUES
(8, 'Coffee Flavor', 'Optional coffee flavor', false, 1, true, CURRENT_TIMESTAMP);

-- ============================================================================
-- MODIFIERS
-- ============================================================================

-- Protein Choice modifiers (group 1)
INSERT INTO modifiers (modifier_id, modifier_group_id, name, price_adjustment, is_active, created_at) VALUES
(1, 1, 'Bacon', 0.00, true, CURRENT_TIMESTAMP),
(2, 1, 'Pork Sausage Patties', 0.00, true, CURRENT_TIMESTAMP),
(3, 1, 'Turkey Sausage Links', 0.00, true, CURRENT_TIMESTAMP),
(4, 1, 'Scrapple', 0.00, true, CURRENT_TIMESTAMP),
(5, 1, 'Avocado', 0.00, true, CURRENT_TIMESTAMP),
(6, 1, 'Peach-Glazed Pork Belly', 2.00, true, CURRENT_TIMESTAMP);

-- Bread Choice modifiers (group 2)
INSERT INTO modifiers (modifier_id, modifier_group_id, name, price_adjustment, is_active, created_at) VALUES
(7, 2, 'Plain Pancake', 0.00, true, CURRENT_TIMESTAMP),
(8, 2, 'White Toast', 0.00, true, CURRENT_TIMESTAMP),
(9, 2, 'Rye Toast', 0.00, true, CURRENT_TIMESTAMP),
(10, 2, 'Pumpernickel Toast', 0.00, true, CURRENT_TIMESTAMP),
(11, 2, 'Flour Tortilla', 0.00, true, CURRENT_TIMESTAMP),
(12, 2, 'Biscuit', 1.00, true, CURRENT_TIMESTAMP),
(13, 2, 'Chocolate Chip Pancake', 1.00, true, CURRENT_TIMESTAMP),
(14, 2, 'French Toast', 2.00, true, CURRENT_TIMESTAMP),
(15, 2, 'Seasonal Pancake', 2.00, true, CURRENT_TIMESTAMP);

-- Side Choice modifiers (group 3)
INSERT INTO modifiers (modifier_id, modifier_group_id, name, price_adjustment, is_active, created_at) VALUES
(16, 3, 'Hashbrowns', 0.00, true, CURRENT_TIMESTAMP),
(17, 3, 'Fruit', 0.00, true, CURRENT_TIMESTAMP),
(18, 3, 'Side Salad', 0.00, true, CURRENT_TIMESTAMP);

-- Pancake Flavor modifiers (group 4)
INSERT INTO modifiers (modifier_id, modifier_group_id, name, price_adjustment, is_active, created_at) VALUES
(19, 4, 'Plain', 0.00, true, CURRENT_TIMESTAMP),
(20, 4, 'Chocolate Chip', 2.00, true, CURRENT_TIMESTAMP),
(21, 4, 'Cranberry Orange (Seasonal)', 5.00, true, CURRENT_TIMESTAMP);

-- Add-Ons modifiers (group 5)
INSERT INTO modifiers (modifier_id, modifier_group_id, name, price_adjustment, is_active, created_at) VALUES
(22, 5, 'Add Egg', 2.00, true, CURRENT_TIMESTAMP),
(23, 5, 'Add Avocado', 2.00, true, CURRENT_TIMESTAMP),
(24, 5, 'Add Chicken', 4.00, true, CURRENT_TIMESTAMP),
(25, 5, 'Add Buffalo Chicken', 5.00, true, CURRENT_TIMESTAMP);

-- Wrap Additions modifiers (group 6)
INSERT INTO modifiers (modifier_id, modifier_group_id, name, price_adjustment, is_active, created_at) VALUES
(26, 6, 'Add Chicken', 4.00, true, CURRENT_TIMESTAMP),
(27, 6, 'Add Buffalo Chicken', 5.00, true, CURRENT_TIMESTAMP),
(28, 6, 'Add Avocado', 2.00, true, CURRENT_TIMESTAMP);

-- Fries Size modifiers (group 7)
INSERT INTO modifiers (modifier_id, modifier_group_id, name, price_adjustment, is_active, created_at) VALUES
(29, 7, 'Half', 0.00, true, CURRENT_TIMESTAMP),
(30, 7, 'Full', 4.00, true, CURRENT_TIMESTAMP);

-- Coffee Flavor modifiers (group 8)
INSERT INTO modifiers (modifier_id, modifier_group_id, name, price_adjustment, is_active, created_at) VALUES
(31, 8, 'Vanilla', 1.00, true, CURRENT_TIMESTAMP),
(32, 8, 'Mocha', 1.00, true, CURRENT_TIMESTAMP),
(33, 8, 'Caramel', 1.00, true, CURRENT_TIMESTAMP);

-- ============================================================================
-- MENU ITEMS - BUILD YOUR OWN
-- ============================================================================
INSERT INTO menu_items (menu_item_id, name, description, price, category, prep_station_id, is_active, created_at) VALUES
(1, 'Combo Plate', 'Two eggs any style with protein, bread, and side choice', 16.00, 'Build Your Own', 1, true, CURRENT_TIMESTAMP),
(2, 'Breakfast Sandwich', 'Two eggs & cheese with protein, bread, and side choice', 16.00, 'Build Your Own', 1, true, CURRENT_TIMESTAMP);

-- ============================================================================
-- MENU ITEMS - SWEET
-- ============================================================================
INSERT INTO menu_items (menu_item_id, name, description, price, category, prep_station_id, is_active, created_at) VALUES
(3, 'Pancake Stack', 'Choose between Plain, Chocolate Chip, or Seasonal Cranberry Orange', 10.00, 'Sweet', 1, true, CURRENT_TIMESTAMP),
(4, 'Classic French Toast', 'Classic French toast', 15.00, 'Sweet', 1, true, CURRENT_TIMESTAMP),
(5, 'Papa Tony''s French Toast', 'Crusty baguette with maple butter & poached fruit syrup (V. Option)', 16.76, 'Sweet', 1, true, CURRENT_TIMESTAMP),
(6, 'Daily Pastry', 'Fresh baked pastry - ask server for daily selection', 5.00, 'Sweet', 1, true, CURRENT_TIMESTAMP);

-- ============================================================================
-- MENU ITEMS - SAVORY
-- ============================================================================
INSERT INTO menu_items (menu_item_id, name, description, price, category, prep_station_id, is_active, created_at) VALUES
(7, 'Omelette of the Day', 'Daily omelette with choice of hashbrowns or side salad (GF)', 16.00, 'Savory', 1, true, CURRENT_TIMESTAMP),
(8, 'Mushroom Gravy', 'Mixed mushrooms & cashew milk gravy, hashbrowns, crispy shiitakes, leek ash & herbs (GF, Vegan)', 17.00, 'Savory', 1, true, CURRENT_TIMESTAMP),
(9, 'Biscuits And Gravy', 'Biscuit with sausage gravy & herbs', 17.00, 'Savory', 1, true, CURRENT_TIMESTAMP),
(10, 'Garden Toast', 'Avocado, rosemary-cashew "cheese", roasted acorn squash & fennel-mandarin orange salad on pumpernickel toast (Vegan)', 14.00, 'Savory', 1, true, CURRENT_TIMESTAMP),
(11, 'Chicken Cutty', 'Hot honey glazed chicken cutlet, pickle-cabbage slaw, calabrian chili aioli & cheddar on sesame bun with chips', 17.00, 'Savory', 1, true, CURRENT_TIMESTAMP),
(12, 'Vegan Burrito', 'Fried sweet potato, roasted cauliflower, beans, poblano, onion, avocado & ancho chili salsa with chips (Vegan)', 17.00, 'Savory', 1, true, CURRENT_TIMESTAMP),
(13, 'Chorizo Crunchwrap', 'Chicken chorizo, refried beans, onion, mozzarella, purple cabbage, crispy corn tortilla & lime crema with salsa verde', 17.00, 'Savory', 1, true, CURRENT_TIMESTAMP),
(14, 'Shrimp & Grits', 'Cheesy grits, sauteed shrimp, braised collard greens, crispy onions, paprika oil & scallions', 18.00, 'Savory', 1, true, CURRENT_TIMESTAMP),
(15, 'Pork-A-Fatusa', 'Roasted pork loin, pulled pork, broccoli rabe, provolone & long hot peppers on ciabatta with pork dipping jus & chips', 18.00, 'Savory', 1, true, CURRENT_TIMESTAMP),
(16, 'Rainbow Wrap', 'Carrot, cucumber, bell pepper, purple cabbage, lettuce & za''atar hummus (Vegan)', 14.00, 'Savory', 1, true, CURRENT_TIMESTAMP),
(17, 'Winterberry Salad', 'Kale, purple cabbage, radish, mandarin orange, pomegranate, spiced walnuts, dried cranberries, acorn squash & orange-balsamic vinaigrette', 15.00, 'Savory', 1, true, CURRENT_TIMESTAMP);

-- ============================================================================
-- MENU ITEMS - SNACKS & SIDES
-- ============================================================================
INSERT INTO menu_items (menu_item_id, name, description, price, category, prep_station_id, is_active, created_at) VALUES
(18, 'Scrapple', 'House-made scrapple from local hogs', 5.00, 'Snacks & Sides', 1, true, CURRENT_TIMESTAMP),
(19, 'Bacon', 'Crispy bacon strips', 5.00, 'Snacks & Sides', 1, true, CURRENT_TIMESTAMP),
(20, 'Sausage Patties', 'House-made pork sausage patties', 5.00, 'Snacks & Sides', 1, true, CURRENT_TIMESTAMP),
(21, 'Martin''s Turkey Sausage Links', 'Turkey sausage links', 5.00, 'Snacks & Sides', 1, true, CURRENT_TIMESTAMP),
(22, 'Peach Glazed Pork Belly', 'House-made peach-glazed pork belly', 7.00, 'Snacks & Sides', 1, true, CURRENT_TIMESTAMP),
(23, 'Biscuit', 'Fresh baked biscuit', 4.00, 'Snacks & Sides', 1, true, CURRENT_TIMESTAMP),
(24, 'Hashbrowns', 'Crispy hashbrowns', 4.00, 'Snacks & Sides', 1, true, CURRENT_TIMESTAMP),
(25, 'Fruit Cup', 'Fresh seasonal fruit', 4.00, 'Snacks & Sides', 1, true, CURRENT_TIMESTAMP),
(26, 'Fries', 'Hand-cut fries', 5.00, 'Snacks & Sides', 1, true, CURRENT_TIMESTAMP),
(27, 'Side Salad', 'Fresh mixed greens', 7.00, 'Snacks & Sides', 1, true, CURRENT_TIMESTAMP);

-- ============================================================================
-- MENU ITEMS - BEVERAGES (Non-Alcoholic)
-- ============================================================================
INSERT INTO menu_items (menu_item_id, name, description, price, category, prep_station_id, is_active, created_at) VALUES
(28, 'House-Made Soda', 'House-made syrup sodas - ask for flavors', 4.00, 'Beverages', 2, true, CURRENT_TIMESTAMP),
(29, 'Mama Deb''s Iced Orange Sun Tea', 'Refreshing iced orange tea', 4.50, 'Beverages', 2, true, CURRENT_TIMESTAMP),
(30, 'Lisa B''s Hot Tulsi Tea', 'Hot herbal tulsi tea', 4.50, 'Beverages', 2, true, CURRENT_TIMESTAMP),
(31, 'Lavender Lemonade', 'House-made lavender lemonade', 4.50, 'Beverages', 2, true, CURRENT_TIMESTAMP),
(32, 'Fresh Squeezed Orange Juice', 'Fresh squeezed OJ', 4.50, 'Beverages', 2, true, CURRENT_TIMESTAMP),
(33, 'Fresh Squeezed Grapefruit Juice', 'Fresh squeezed grapefruit juice', 4.50, 'Beverages', 2, true, CURRENT_TIMESTAMP),
(34, 'Unsweetened Iced Tea', 'Classic unsweetened iced tea', 4.50, 'Beverages', 2, true, CURRENT_TIMESTAMP),
(35, 'Matcha Latte', 'Creamy matcha latte', 6.00, 'Beverages', 2, true, CURRENT_TIMESTAMP);

-- ============================================================================
-- MENU ITEMS - COCKTAILS
-- ============================================================================
INSERT INTO menu_items (menu_item_id, name, description, price, category, prep_station_id, is_active, created_at) VALUES
(36, 'Mimosa', 'Bubbles & fresh squeezed OJ', 11.00, 'Cocktails', 2, true, CURRENT_TIMESTAMP),
(37, 'Bellini', 'Bubbles & peach puree', 12.00, 'Cocktails', 2, true, CURRENT_TIMESTAMP),
(38, 'Bloody Mary', 'Vodka & house-made mix', 12.00, 'Cocktails', 2, true, CURRENT_TIMESTAMP),
(39, 'Espresso Martini', 'Vodka, house-made coffee liqueur & espresso', 14.00, 'Cocktails', 2, true, CURRENT_TIMESTAMP),
(40, 'Chef Sam''s Mezcal Old Fashioned', 'Mezcal, tequila, morita chile agave, orange bitters, burnt orange & cherry', 15.00, 'Cocktails', 2, true, CURRENT_TIMESTAMP),
(41, 'Peach Paper Plane', 'Bourbon, amaro, aperol, lemon & peach puree', 14.00, 'Cocktails', 2, true, CURRENT_TIMESTAMP),
(42, 'Lavender French 75', 'Gin, lavender, lemon & bubbles', 13.00, 'Cocktails', 2, true, CURRENT_TIMESTAMP),
(43, 'Wine (Glass)', 'Seasonal wine selection - ask server', 10.00, 'Cocktails', 2, true, CURRENT_TIMESTAMP),
(44, 'Beer', 'Seasonal beer selection - ask server', 7.00, 'Cocktails', 2, true, CURRENT_TIMESTAMP);

-- ============================================================================
-- MENU ITEMS - COFFEE
-- ============================================================================
INSERT INTO menu_items (menu_item_id, name, description, price, category, prep_station_id, is_active, created_at) VALUES
(45, 'Drip Coffee', 'Little Goat Coffee Roasting Co.', 3.00, 'Coffee', 2, true, CURRENT_TIMESTAMP),
(46, 'Espresso', 'Double shot espresso', 3.50, 'Coffee', 2, true, CURRENT_TIMESTAMP),
(47, 'Cortado', 'Espresso with steamed milk', 4.00, 'Coffee', 2, true, CURRENT_TIMESTAMP),
(48, 'Cappuccino', 'Classic cappuccino', 4.50, 'Coffee', 2, true, CURRENT_TIMESTAMP),
(49, 'Hot Latte', 'Hot espresso latte', 5.00, 'Coffee', 2, true, CURRENT_TIMESTAMP),
(50, 'Iced Latte', 'Iced espresso latte', 5.00, 'Coffee', 2, true, CURRENT_TIMESTAMP),
(51, 'Cold Brew Coffee', '16oz cold brew', 4.00, 'Coffee', 2, true, CURRENT_TIMESTAMP),
(52, 'Shelly''s Shaken Espresso', 'Double espresso shaken with brown sugar, cinnamon & milk', 5.50, 'Coffee', 2, true, CURRENT_TIMESTAMP);

-- ============================================================================
-- MENU ITEM MODIFIER GROUPS (Link items to their modifier groups)
-- ============================================================================

-- Build Your Own items (Combo Plate & Breakfast Sandwich)
INSERT INTO menu_item_modifier_groups (menu_item_modifier_group_id, menu_item_id, modifier_group_id, created_at) VALUES
(1, 1, 1, CURRENT_TIMESTAMP),  -- Combo Plate → Protein Choice
(2, 1, 2, CURRENT_TIMESTAMP),  -- Combo Plate → Bread Choice
(3, 1, 3, CURRENT_TIMESTAMP),  -- Combo Plate → Side Choice
(4, 2, 1, CURRENT_TIMESTAMP),  -- Breakfast Sandwich → Protein Choice
(5, 2, 2, CURRENT_TIMESTAMP),  -- Breakfast Sandwich → Bread Choice
(6, 2, 3, CURRENT_TIMESTAMP);  -- Breakfast Sandwich → Side Choice

-- Pancake Stack → Pancake Flavor
INSERT INTO menu_item_modifier_groups (menu_item_modifier_group_id, menu_item_id, modifier_group_id, created_at) VALUES
(7, 3, 4, CURRENT_TIMESTAMP);  -- Pancake Stack → Pancake Flavor

-- Savory items with Add-Ons
INSERT INTO menu_item_modifier_groups (menu_item_modifier_group_id, menu_item_id, modifier_group_id, created_at) VALUES
(8, 9, 5, CURRENT_TIMESTAMP),  -- Biscuits And Gravy → Add-Ons
(9, 10, 5, CURRENT_TIMESTAMP); -- Garden Toast → Add-Ons

-- Rainbow Wrap → Wrap Additions
INSERT INTO menu_item_modifier_groups (menu_item_modifier_group_id, menu_item_id, modifier_group_id, created_at) VALUES
(10, 16, 6, CURRENT_TIMESTAMP); -- Rainbow Wrap → Wrap Additions

-- Fries → Fries Size
INSERT INTO menu_item_modifier_groups (menu_item_modifier_group_id, menu_item_id, modifier_group_id, created_at) VALUES
(11, 26, 7, CURRENT_TIMESTAMP); -- Fries → Fries Size

-- Coffee items → Coffee Flavor
INSERT INTO menu_item_modifier_groups (menu_item_modifier_group_id, menu_item_id, modifier_group_id, created_at) VALUES
(12, 49, 8, CURRENT_TIMESTAMP), -- Hot Latte → Coffee Flavor
(13, 50, 8, CURRENT_TIMESTAMP), -- Iced Latte → Coffee Flavor
(14, 51, 8, CURRENT_TIMESTAMP); -- Cold Brew → Coffee Flavor

-- ============================================================================
-- TABLES
-- ============================================================================
INSERT INTO tables (table_number, section, seat_count, status, x_position, y_position, shape, rotation, created_at) VALUES
('F1', 'Front', 2, 'available', 100, 100, 'circle', 0, datetime('now')),
('F2', 'Front', 2, 'available', 250, 100, 'circle', 0, datetime('now')),
('F3', 'Front', 4, 'available', 400, 100, 'square', 0, datetime('now')),
('F4', 'Front', 4, 'available', 550, 100, 'square', 0, datetime('now')),
('F5', 'Front', 6, 'available', 700, 100, 'square', 0, datetime('now')),
('B1', 'Back', 4, 'available', 100, 300, 'square', 0, datetime('now')),
('B2', 'Back', 4, 'available', 250, 300, 'square', 0, datetime('now')),
('B3', 'Back', 6, 'available', 400, 300, 'square', 0, datetime('now')),
('B4', 'Back', 6, 'available', 550, 300, 'square', 0, datetime('now')),
('B5', 'Back', 8, 'available', 700, 300, 'square', 0, datetime('now')),
('P1', 'Patio', 2, 'available', 100, 500, 'circle', 0, datetime('now')),
('P2', 'Patio', 2, 'available', 250, 500, 'circle', 0, datetime('now')),
('P3', 'Patio', 4, 'available', 400, 500, 'square', 0, datetime('now')),
('P4', 'Patio', 4, 'available', 550, 500, 'square', 0, datetime('now')),
('P5', 'Patio', 6, 'available', 700, 500, 'square', 0, datetime('now'));

-- ============================================================================
-- SUMMARY
-- ============================================================================
-- 
-- PREP STATIONS: 2 (Kitchen, Bar)
-- MODIFIER GROUPS: 8
-- MODIFIERS: 33
-- MENU ITEMS: 52 total
--   - Build Your Own: 2
--   - Sweet: 4
--   - Savory: 11
--   - Snacks & Sides: 10
--   - Beverages: 8
--   - Cocktails: 9
--   - Coffee: 8
-- MENU ITEM MODIFIER LINKS: 14
-- TABLES: 15 (Front, Back, Bar sections)
--
-- ============================================================================
