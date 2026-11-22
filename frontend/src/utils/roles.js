/**
 * Who may open which screen, and who may do which restricted thing.
 *
 * WHY ONE FILE: the nav bar, the Home screen, the app shell, and a few
 *      buttons all need the same answers. If each kept its own list they
 *      would drift apart.
 *
 * THE BACKEND IS THE SOURCE. Every rule that matters is enforced there, on the
 * request itself (see Permissions.java and @PreAuthorize): hiding a button here
 * is for a tidy screen, not for security. This file repeats the backend's
 * matrix so that a screen never offers something the backend would refuse.
 * If the two ever disagree, the backend is right and this file is the one to
 * correct. The matrix:
 *
 *   read the menu, tables, orders         anyone signed in
 *   search (tables, checks, the menu)     anyone signed in
 *   read and dismiss own notifications    anyone signed in
 *   write a message to the floor          kitchen, bartender, manager
 *   read what each person sold            anyone signed in (no tips in it)
 *   open orders, ring in, send, take pay  server, bartender, host, manager
 *   start and complete items              kitchen, bartender, manager
 *   86 and un-86                          kitchen, bartender, manager
 *   menu, staff, Financials, floor layout manager
 *   choose the template (Settings)        manager
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
  // Managers see everything. 'gallery' is the template gallery, reached from Settings.
  manager: [...FLOOR_SCREENS, 'financials', 'staff', 'settings', 'gallery'],
  server: FLOOR_SCREENS,
  bartender: FLOOR_SCREENS,
  // Hosts seat guests, and may also open a check, ring in, and take payment (owner's
  // decision D2): the takeout order at the host stand, the check paid at the door.
  host: ['floorMap', 'activeTables', 'quickOrders', 'createOrder'],
  kitchen: ['activeOrders', 'eightySix']                  // The kitchen display and the 86 board
};

// Restricted things inside a screen, and the roles that may do them
const ROLES_BY_ACTION = {
  // The search box in the top bar: every role (Permissions.SEARCH on the backend). What
  // a result DOES still depends on the role: see components/Search.
  'search': ['manager', 'server', 'bartender', 'host', 'kitchen'],
  'edit-layout': ['manager'],                             // Rearranging the Floor Map
  'change-86': ['manager', 'kitchen', 'bartender'],       // 86'ing an item or putting it back on
  'make-items': ['manager', 'kitchen', 'bartender'],      // START and COMPLETE on the kitchen display
  // Writing a message to everyone else from the bell's panel (the owner's decision D5;
  // Permissions.SEND_MESSAGES on the backend). Everyone reads and dismisses their own.
  'send-message': ['manager', 'kitchen', 'bartender'],
  // Reading what each person sold, for the leaderboard: every role (the owner's decision
  // D6; Permissions.SEE_STAFF_SALES on the backend). Which roles are offered the
  // Challenges screen it is drawn on is SCREENS_BY_ROLE above, a separate question.
  'see-staff-sales': ['manager', 'server', 'bartender', 'host', 'kitchen']
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
 * @param {string} action - 'search', 'edit-layout', 'change-86', 'make-items', 'send-message', or 'see-staff-sales'
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
