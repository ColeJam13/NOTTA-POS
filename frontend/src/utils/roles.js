/**
 * Who may open which screen, and who may do which restricted thing.
 *
 * WHY ONE FILE: the nav bar, the Home screen, the app shell, and a few
 *      buttons all need the same answers. If each kept its own list they
 *      would drift apart.
 *
 * The backend enforces the important ones again (see @PreAuthorize there):
 * hiding a button here is for a tidy screen, not for security.
 *
 * A role of null means "nobody is signed in". In the running app that never
 * reaches these functions, because the PIN screen comes first. It does happen
 * when a single screen is drawn on its own in a test, so null is allowed
 * everything.
 */

// What each role is called on screen, in the order roles are offered when adding staff
export const ROLE_LABELS = {
  server: 'Server',
  bartender: 'Bartender',
  host: 'Host',
  kitchen: 'Kitchen',
  manager: 'Manager'
};

// The floor screens servers and bartenders work from
const FLOOR_SCREENS = ['floorMap', 'activeTables', 'quickOrders', 'activeOrders', 'createOrder', 'eightySix', 'challenges'];

// The screens each role may open (everyone may open 'home')
const SCREENS_BY_ROLE = {
  manager: [...FLOOR_SCREENS, 'financials', 'staff'],     // Managers see everything
  server: FLOOR_SCREENS,
  bartender: FLOOR_SCREENS,
  host: ['floorMap', 'activeTables'],                     // Hosts seat guests; they do not ring in orders
  kitchen: ['activeOrders', 'eightySix']                  // The kitchen display and the 86 board
};

// Restricted things inside a screen, and the roles that may do them
const ROLES_BY_ACTION = {
  'edit-layout': ['manager'],                             // Rearranging the Floor Map
  'change-86': ['manager', 'kitchen', 'bartender']        // 86'ing an item or putting it back on
};

/**
 * @param {string|null} role - The signed-in person's role
 * @param {string} view - A screen name, e.g. 'financials'
 * @returns {boolean} - Whether that role may open that screen
 */
export const canOpenScreen = (role, view) => {
  if (!role || view === 'home') return true;
  return (SCREENS_BY_ROLE[role] || []).includes(view);
};

/**
 * @param {string|null} role - The signed-in person's role
 * @param {string} action - 'edit-layout' or 'change-86'
 * @returns {boolean} - Whether that role may do it
 */
export const canDo = (role, action) => {
  if (!role) return true;
  return (ROLES_BY_ACTION[action] || []).includes(role);
};

/**
 * Initials for the round badge in the nav bar: "Sarah Miller" -> "SM", "Morgan" -> "MO".
 */
export const initialsOf = (name) => {
  const words = (name || '').trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '?';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[words.length - 1][0]).toUpperCase();
};
