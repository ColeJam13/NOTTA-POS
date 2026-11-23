import { useState } from 'react';
import NavBar from '../../components/NavBar';
import Logo from '../../components/Logo';
import PinPad from '../../components/PinPad';
import PaymentForm from '../../components/PaymentModal/PaymentForm';
import { SearchResults } from '../../components/Search';
import { NotificationPanel } from '../../components/Notifications';
import PeriodPicker, { periodOf } from '../../components/PeriodPicker';
import { Bell } from 'lucide-react';
import { rowsOf } from '../../components/Search/SearchResults';
import ActiveTableCard from '../../components/TableChecks/ActiveTableCard';
import OrderItemsList from '../CreateOrder/OrderItemsList';
import MenuPanel from '../CreateOrder/MenuPanel';
import OrderTicket from '../ActiveOrders/OrderTicket';
import TableVisual from '../FloorMap/TableVisual';
import PausedTerminalRow from '../Staff/PausedTerminalRow';
import Leaderboard from '../Challenges/Leaderboard';
import ChallengeCard from '../Challenges/ChallengeCard';
import ChallengeForm from '../Challenges/ChallengeForm';
import { EightySixChip, EightySixTile } from '../EightySixBoard/EightySixPieces';
import { TEMPLATES, useTemplates, templateOnScreen, previewTemplate, stopPreview } from '../../templates';
import { serverNow, asBackendStamp } from '../../services/serverClock';
import WideScreenNote from '../../components/WideScreenNote';
// The stylesheets of the screens the pieces below come from. (In the built app every
// stylesheet is loaded anyway; naming them here says where each look is defined.)
import '../CreateOrder/CreateOrder.css';
import '../ActiveOrders/ActiveOrders.css';
import '../Tables/Tables.css';
import '../FloorMap/FloorMap.css';
import '../EightySixBoard/EightySixBoard.css';
import '../Staff/Staff.css';
import '../Challenges/Challenges.css';
import '../Home/Home.css';
import './Gallery.css';

/**
 * The template gallery (managers only, reached from Settings): every piece of the
 * interface, in every state, on one page, drawn in the template that is on screen.
 *
 * WHY: A template is judged by questions like "can I tell a locked item from a saved
 *      draft?" and "is a late ticket unmistakable?". Answering them in the real app
 *      means ringing in orders and waiting fifteen minutes, on every one of thirteen
 *      templates. Here every state sits side by side, and the buttons at the top
 *      switch the template under them.
 *
 * WHAT IS REAL AND WHAT IS NOT
 *   The pieces are the app's OWN components, the same files the screens use:
 *   OrderItemsList, MenuPanel, OrderTicket, ActiveTableCard, TableVisual,
 *   EightySixChip and EightySixTile, PinPad, PaymentForm, PausedTerminalRow, Logo.
 *   When a screen's piece changes, the gallery changes with it.
 *   The DATA is made up, below. Nothing here reads from or writes to the backend.
 *   BUTTONS and the one-line MESSAGES are not components anywhere in the app: a screen
 *   writes <button className="btn-send">. The gallery writes the same class names, so
 *   they are drawn by the screens' own stylesheets; if a screen renames a class, the
 *   button here must be renamed too.
 *
 * The switcher at the top PREVIEWS a template, exactly as PREVIEW on the Settings
 * screen does: this terminal only, with the preview bar above the page to choose it
 * for everyone or stop.
 */

// ---------------------------------------------------------------------------
// Made-up data, in the shapes the backend sends
// ---------------------------------------------------------------------------

const SIDES = [{ modifierId: 1, name: 'Hashbrowns', priceAdjustment: 0 }, { modifierId: 2, name: 'French Toast', priceAdjustment: 2 }];

// One order line in each state. A line with no orderItemId has not been saved yet.
const ORDER_LINES = [
  { name: 'New, not saved yet', price: 16, status: 'draft', modifiers: [], specialInstructions: null },
  { orderItemId: 2, name: 'Saved draft', price: 18, status: 'draft', modifiers: SIDES, specialInstructions: 'no onions' },
  { orderItemId: 3, name: 'In the edit window', price: 14, status: 'limbo', modifiers: [], specialInstructions: null },
  { orderItemId: 4, name: 'Locked: with the kitchen', price: 17, status: 'pending', modifiers: SIDES, specialInstructions: 'nut allergy' },
  { orderItemId: 5, name: 'Locked: made', price: 15, status: 'completed', modifiers: [], specialInstructions: null }
];

const MENU = [
  { menuItemId: 1, name: 'Omelette of the Day', price: 16, category: 'Savory', is86d: false },
  { menuItemId: 2, name: 'Garden Toast', price: 14, category: 'Savory', is86d: true },
  { menuItemId: 3, name: 'Chicken Cutty', price: 17, category: 'Savory', is86d: false },
  { menuItemId: 4, name: 'French Toast', price: 15, category: 'Sweet', is86d: false }
];

const ticketItem = (orderItemId, name, status, extras = {}) =>
  ({ orderItemId, name, quantity: 1, status, modifiers: [], specialInstructions: null, ...extras });

// Kitchen tickets: on time, at the 10-minute warning, late, and finished
const TICKETS = [
  { order: { orderId: 101, tableNumber: 'W1', isQuickOrder: false }, waitingMinutes: 3, isDone: false,
    items: [ticketItem(1, 'Omelette of the Day', 'pending', { modifiers: SIDES, specialInstructions: 'no onions' }), ticketItem(2, 'Chicken Cutty', 'fired')] },
  { order: { orderId: 102, tableNumber: 'W2', isQuickOrder: false }, waitingMinutes: 12, isDone: false,
    items: [ticketItem(3, 'French Toast', 'fired'), ticketItem(4, 'Garden Toast', 'completed')] },
  { order: { orderId: 103, tableNumber: 'QO4', isQuickOrder: true }, waitingMinutes: 20, isDone: false,
    items: [ticketItem(5, 'Chicken Cutty', 'pending', { specialInstructions: 'nut allergy' })] },
  { order: { orderId: 104, tableNumber: 'W4', isQuickOrder: false }, waitingMinutes: null, isDone: true,
    items: [ticketItem(6, 'Omelette of the Day', 'completed')] }
];

const tableCard = (tableNumber, counts) => ({
  tableId: tableNumber, tableNumber, serverName: 'Sarah', subtotal: 48.5, itemCount: 3,
  limboCount: 0, pendingCount: 0, firedCount: 0, completedCount: 0, ...counts
});

// An open check's card in each of the four borders it can have. Opened "12 minutes ago"
// by the backend's clock, which is the clock the card measures from (serverClock.js).
const tableCards = () => {
  const openedAt = asBackendStamp(serverNow() - 12 * 60 * 1000);
  return [
    tableCard('W1', { openedAt, limboCount: 1, pendingCount: 2 }),            // Something is still in its edit window
    tableCard('W2', { openedAt, pendingCount: 2, firedCount: 1 }),            // The kitchen is working on it
    tableCard('W3', { openedAt, completedCount: 3 }),                         // Everything has been made
    tableCard('W4', { openedAt })                                             // Nothing sent yet (drafts only)
  ];
};

const floorTable = (tableId, tableNumber, status, shape, xposition) =>
  ({ tableId, tableNumber, status, shape, seatCount: 4, xposition, yposition: 30, rotation: 0 });

const FLOOR_TABLES = [
  floorTable(1, 'A1', 'available', 'square', 30),
  floorTable(2, 'A2', 'occupied', 'square', 180),
  floorTable(3, 'A3', 'available', 'circle', 330),
  floorTable(4, 'A4', 'occupied', 'circle', 480)
];

const EIGHTY_SIX_ITEMS = [
  { menuItemId: 1, name: 'Omelette of the Day', is86d: false },
  { menuItemId: 2, name: 'Garden Toast', is86d: true }
];

const PAUSED_TERMINALS = [
  { device: '192.168.1.21', secondsLeft: 272, thisDevice: false },
  { device: '192.168.1.22', secondsLeft: 45, thisDevice: true }
];

const nothing = () => {};       // The gallery's buttons do nothing: there is no order behind them

// What the bell holds, made up: one notification of each kind, in the shape the backend
// answers with (NotificationView). Their times are filled in when the gallery is drawn.
const NOTICES = [
  { notificationId: 4, kind: 'item_back', text: 'Garden Toast is back on', fromName: 'Dan', minutesAgo: 1 },
  { notificationId: 3, kind: 'order_up', text: 'Table W5 is up', fromName: 'Kit', minutesAgo: 4 },
  { notificationId: 2, kind: 'message', text: 'Fryer is down for 10 minutes. Fries and wings will be slow.', fromName: 'Kit', minutesAgo: 12 },
  { notificationId: 1, kind: 'item_86', text: "Garden Toast is 86'd", fromName: 'Kit', minutesAgo: 40 }
];

// Challenges, made up, in the shape the backend answers with (ChallengeView): one card
// for each thing a card can say. The viewer is staff 3 (OWN_ROW), so "your progress" is
// that person's line. Times are filled in when the gallery is drawn (see challengeCards).
const anyone = (staffId, name, progress, fields = {}) =>
  ({ staffId, name, progress, checksClosed: 4, meetsTarget: false, completed: false, awardedAt: null, awardedByName: null, ...fields });
const DONE = { meetsTarget: true, completed: true };
const CHALLENGES = [
  { challengeId: 1, name: 'Sweet tooth', description: 'Pie, cake, and the daily pastry', measure: 'items_sold', target: 15, menuCategory: 'Sweet',
    state: 'active', endsInHours: 3.26, prize: '$20', participants: [anyone(3, 'Morgan', 12)] },
  { challengeId: 2, name: 'Big night', measure: 'total_sales', target: 500,
    state: 'active', endsInHours: 5.1, prize: 'First cut Friday', participants: [anyone(3, 'Morgan', 612.5, DONE)] },
  { challengeId: 3, name: 'High average', measure: 'check_average', target: 40, minChecks: 5,
    state: 'active', endsInHours: 49, prize: '$25', participants: [anyone(3, 'Morgan', 45.2, { checksClosed: 6, meetsTarget: true })] },
  { challengeId: 4, name: 'Weekend push', measure: 'biggest_check', target: 150,
    state: 'upcoming', startsInHours: 26.1, endsInHours: 74, prize: null, participants: [] },
  { challengeId: 5, name: 'Pastry push', measure: 'items_sold', target: 20, menuItemId: 41, menuItemName: 'Daily Pastry',
    state: 'finished', endedMinutesAgo: 35, prize: '$20', participants: [anyone(3, 'Morgan', 23, { ...DONE, awardedMinutesAgo: 10, awardedByName: 'Alex' })] },
  { challengeId: 6, name: 'Lunch rush', measure: 'total_sales', target: 300,
    state: 'finished', endedMinutesAgo: 190, prize: '$10', participants: [anyone(3, 'Morgan', 212)] }
];
// The same kind of card as a manager sees it: everyone's progress, and the buttons
const MANAGED_CHALLENGE = { challengeId: 7, name: 'Big night', description: 'Friday dinner only', measure: 'total_sales', target: 500,
  state: 'active', endsInHours: 5.1, prize: 'First cut Friday', participants: [
    anyone(1, 'Sarah Miller', 612.5, DONE),
    anyone(2, 'Dan', 540, { ...DONE, awardedMinutesAgo: 10, awardedByName: 'Alex' }),
    anyone(3, 'Morgan', 212)
  ] };
// Menu items for the form's "which items count" list
const CHALLENGE_MENU = [
  { menuItemId: 41, name: 'Daily Pastry', category: 'Sweet' },
  { menuItemId: 12, name: 'Burger', category: 'Savory' },
  { menuItemId: 42, name: 'Cake', category: 'Sweet' }
];

// A leaderboard, made up, in the shape the backend answers with (StaffSalesView): two
// people tied for first (so the next rank is 3), someone below the medals, and the row
// of the person looking at it (staff 3, see OWN_ROW).
const BOARD = [
  { staffId: 1, name: 'Sarah Miller', rank: 1, checksClosed: 14, sales: 1240.5, checkAverage: 88.61, biggestCheck: 310, itemsSold: 96 },
  { staffId: 2, name: 'Dan', rank: 1, checksClosed: 11, sales: 1240.5, checkAverage: 112.77, biggestCheck: 240.25, itemsSold: 71 },
  { staffId: 3, name: 'Morgan', rank: 3, checksClosed: 9, sales: 884, checkAverage: 98.22, biggestCheck: 180, itemsSold: 58 },
  { staffId: 4, name: 'Harper', rank: 4, checksClosed: 2, sales: 61.75, checkAverage: 30.88, biggestCheck: 45, itemsSold: 5 }
];
const OWN_ROW = 3;

// What the search box found, made up: one or two results of each kind, in the shape the
// backend answers with (SearchResultsView). The second closed check has no tip in it,
// as a check does when the person asking did not open it.
const FOUND = {
  tables: { results: [
    { tableId: 1, tableNumber: 'W7', section: 'Window', seatCount: 4, status: 'available', isQuickOrder: false },
    { tableId: 2, tableNumber: 'W17', section: 'Window', seatCount: 2, status: 'occupied', isQuickOrder: false }
  ], more: false },
  openChecks: { results: [
    { orderId: 31, tableId: 2, tableNumber: 'W17', status: 'occupied', isQuickOrder: false, serverName: 'Sarah', itemCount: 3, subtotal: 48.5, openedAt: null }
  ], more: false },
  closedChecks: { results: [
    { orderId: 21, tableId: 1, tableNumber: 'W7', isQuickOrder: false, serverName: 'Sarah', createdAt: null, closedAt: null, subtotal: 20, tax: 0.6, total: 20.6, tipAmount: 5, paymentMethod: 'credit_card', items: [] },
    { orderId: 22, tableId: 3, tableNumber: 'QO7', isQuickOrder: true, serverName: 'Dan', createdAt: null, closedAt: null, subtotal: 12.5, tax: 0.38, total: 12.88, items: [] }
  ], more: true },
  menuItems: { results: [
    { menuItemId: 1, name: 'Omelette of the Day', category: 'Savory', price: 16, is86d: false },
    { menuItemId: 2, name: 'Garden Toast', category: 'Savory', price: 12.5, is86d: true }
  ], more: false }
};
// A made-up challenge with its start, end, and award times worked out from "now" by the
// backend's clock, as the real answers have them
const challengeAt = ({ startsInHours, endsInHours, endedMinutesAgo, participants, ...rest }) => {
  const ago = (minutes) => asBackendStamp(serverNow() - minutes * 60 * 1000);
  return {
    description: null, menuCategory: null, menuItemId: null, menuItemName: null, minChecks: null, createdByName: 'Alex',
    ...rest,
    startsAt: startsInHours ? ago(-startsInHours * 60) : ago(26 * 60),
    endsAt: endedMinutesAgo ? ago(endedMinutesAgo) : ago(-endsInHours * 60),
    participants: participants.map(({ awardedMinutesAgo, ...person }) => ({ ...person, awardedAt: awardedMinutesAgo ? ago(awardedMinutesAgo) : null }))
  };
};

// The made-up search results with their times filled in: opened 12 minutes ago, paid 30
// and 95 minutes ago, by the backend's clock, as every "ago" in the app is worked out
const withTimes = (found) => {
  const ago = (minutes) => asBackendStamp(serverNow() - minutes * 60 * 1000);
  return {
    ...found,
    openChecks: { ...found.openChecks, results: found.openChecks.results.map(check => ({ ...check, openedAt: ago(12) })) },
    closedChecks: { ...found.closedChecks, results: found.closedChecks.results.map((check, index) => ({ ...check, createdAt: ago(150), closedAt: ago(index === 0 ? 30 : 95) })) }
  };
};
const NOTHING_FOUND = {
  tables: { results: [], more: false }, openChecks: { results: [], more: false },
  closedChecks: { results: [], more: false }, menuItems: { results: [], more: false }
};

// One titled block of the page
function Piece({ title, note, wide, children }) {
  return (
    <section className={`gallery-piece ${wide ? 'wide' : ''}`} aria-label={title}>
      <h3>{title}</h3>
      {note && <p className="gallery-note">{note}</p>}
      {children}
    </section>
  );
}

function Gallery({ setCurrentView, setSelectedTable }) {
  const templates = useTemplates();
  const onScreen = templateOnScreen(templates);

  // The few things on this page that can be pressed, so their "selected" look can be seen
  const [category, setCategory] = useState('Savory');
  const [paymentMethod, setPaymentMethod] = useState('credit_card');
  const [tipPercent, setTipPercent] = useState(18);
  const [searchPeriod, setSearchPeriod] = useState('today');   // The search panel's "closed checks from" buttons really switch
  const [boardPeriod, setBoardPeriod] = useState(periodOf('custom'));   // So do the period buttons; CUSTOM shows the two date boxes

  // Show a template: a preview, unless it is the restaurant's own (then no preview is needed)
  const show = (id) => {
    if (id === templates.chosen) stopPreview();
    else previewTemplate(id);
  };

  return (
    <div className="page-with-nav">
      <NavBar currentView="settings" setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />
      <div className="gallery-page">
        <h2>TEMPLATE GALLERY</h2>
        {/* Drawn on a phone only */}
        <WideScreenNote>The gallery is laid out for a tablet or a wider screen: its pieces are drawn at the size they have there, and on a phone it scrolls sideways.</WideScreenNote>

        <div className="gallery-top">
          <button className="gallery-back" onClick={() => setCurrentView('settings')}>BACK TO SETTINGS</button>
          <p className="gallery-note">
            Every piece of the app in every state, drawn in <strong>{onScreen.name}</strong>. Nothing
            here is a real order. Choosing another template below previews it on this terminal only.
          </p>
        </div>

        {/* One button per template. The one on screen is lit; the restaurant's own is marked. */}
        <div className="gallery-switcher" role="group" aria-label="Template on screen">
          {TEMPLATES.map(template => (
            <button
              key={template.id}
              className={`gallery-switch ${onScreen.id === template.id ? 'active' : ''}`}
              aria-pressed={onScreen.id === template.id}
              onClick={() => show(template.id)}
            >
              {template.name.toUpperCase()}{templates.chosen === template.id ? ' (IN USE)' : ''}
            </button>
          ))}
        </div>

        <div className="gallery-pieces">
          <Piece title="LOGO AND TEXT" note="The logo as the Home and PIN screens show it, and the kinds of text.">
            <Logo pictureClassName="gallery-logo" size="medium" />
            <p className="gallery-text">Ordinary text: Omelette of the Day</p>
            <p className="gallery-text muted">Muted: explanations and form labels</p>
            <p className="gallery-text subtle">Subtle: secondary labels</p>
            <p className="gallery-text faint">Faint: placeholders, times, &quot;nothing here&quot;</p>
            <p className="gallery-text accent">Accent: the server&apos;s name on a card</p>
            <p className="gallery-text primary">Primary: $48.50</p>
          </Piece>

          <Piece title="BUTTONS" note="The main action, a neutral one, a destructive one, one that is switched off, and the ones that switch between views.">
            <div className="order-actions">
              <div className="action-row">
                <button className="btn-save" onClick={nothing}>SAVE DRAFT</button>
                <button className="btn-send" onClick={nothing}>SEND ORDER</button>
              </div>
              <button className="btn-close-order" onClick={nothing}>CLOSE ORDER</button>
              <button className="btn-close-order" disabled>CLOSE ORDER (OFF)</button>
            </div>
            <div className="view-toggle">
              <button className="toggle-btn active" onClick={nothing}>ACTIVE</button>
              <button className="toggle-btn" onClick={nothing}>CLOSED</button>
            </div>
            <div className="kitchen-controls">
              <button className="station-btn active" onClick={nothing}>ALL</button>
              <button className="station-btn" onClick={nothing}>KITCHEN</button>
              <button className="station-btn done-toggle" onClick={nothing}>SHOW DONE</button>
              <button className="station-btn done-toggle active" onClick={nothing}>SHOW DONE</button>
            </div>
            <div className="gallery-row">
              <button className="staff-btn" onClick={nothing}>EDIT</button>
              <button className="staff-btn staff-btn-primary" onClick={nothing}>SAVE</button>
              <button className="floor-btn" onClick={nothing}>EDIT LAYOUT</button>
              <button className="floor-btn floor-btn-save" onClick={nothing}>SAVE LAYOUT</button>
            </div>
            <button className="landing-btn" onClick={nothing}>FLOOR MAP</button>
          </Piece>

          <Piece title="AN ORDER" note="The message bar in its four kinds, then one line in each state: new, saved draft, in the edit window, locked with the kitchen, and made.">
            <div className="notification-bar notification-default">Ready to send or save as draft</div>
            <div className="notification-bar notification-success">Draft saved! Items remain editable</div>
            <div className="notification-bar notification-timer">12 seconds to edit</div>
            <div className="notification-bar notification-locked">Items locked and sent to prep station</div>
            <OrderItemsList items={ORDER_LINES} onRemoveItem={nothing} onEditItem={nothing} />
            <div className="order-totals">
              <div className="total-row"><span>Subtotal:</span><span>$80.00</span></div>
              <div className="total-row"><span>Tax (3%):</span><span>$2.40</span></div>
              <div className="total-row total"><span>TOTAL:</span><span>$82.40</span></div>
            </div>
          </Piece>

          <Piece title="THE MENU" note="Category tabs, items that can be rung in, and one that is 86'd.">
            <MenuPanel menuItems={MENU} selectedCategory={category} onSelectCategory={setCategory} onAddItem={nothing} />
          </Piece>

          <Piece title="KITCHEN TICKETS" wide note="On time, at the 10-minute warning, late, and finished.">
            <div className="gallery-tickets">
              {TICKETS.map(ticket => (
                <OrderTicket
                  key={ticket.order.orderId}
                  order={ticket.order}
                  items={ticket.items}
                  isDone={ticket.isDone}
                  waitingMinutes={ticket.waitingMinutes}
                  showButtons
                  onStart={nothing}
                  onComplete={nothing}
                />
              ))}
            </div>
          </Piece>

          <Piece title="OPEN CHECKS" wide note="A table's card by what its items are doing: something still in its edit window, the kitchen working on it, everything made, and nothing sent yet.">
            <div className="tables-grid">
              {tableCards().map(table => <ActiveTableCard key={table.tableNumber} table={table} onOpen={nothing} />)}
            </div>
          </Piece>

          <Piece title="THE FLOOR" note="Tables that are available and occupied, square and round, and one selected in the layout editor.">
            <div className="floor-canvas">
              {FLOOR_TABLES.map(table => (
                <TableVisual key={table.tableId} table={table} onTableClick={nothing} editing={table.tableId === 4} selected={table.tableId === 4} onDragStart={nothing} />
              ))}
            </div>
          </Piece>

          <Piece title="THE 86 BOARD" note="What is out right now, an item that is available, and one that is 86'd.">
            <section className="eighty-six-now">
              <h3>86&apos;D NOW (1)</h3>
              <div className="eighty-six-now-list">
                <EightySixChip item={EIGHTY_SIX_ITEMS[1]} canChange onToggle={nothing} />
              </div>
            </section>
            <div className="eighty-six-grid">
              {EIGHTY_SIX_ITEMS.map(item => <EightySixTile key={item.menuItemId} item={item} canChange onToggle={nothing} />)}
            </div>
          </Piece>

          <Piece title="THE PIN PAD" note="With a refusal showing. Typing digits fills the dots; nothing is checked.">
            <div className="gallery-pin-pad">
              <PinPad message="PIN not recognized." onComplete={nothing} />
            </div>
          </Piece>

          <Piece title="TAKING PAYMENT" note="The inside of the Payment window.">
            <div className="modal-content">
              <div className="modal-header"><h2>CLOSE ORDER</h2></div>
              <div className="modal-body">
                <PaymentForm
                  subtotal={80} tax={2.4} total={82.4} taxRateLabel="3%"
                  paymentMethod={paymentMethod} onPaymentMethod={setPaymentMethod}
                  tipPercent={tipPercent} onTipPercent={setTipPercent}
                  tipAmount="" onTipAmount={nothing}
                  grandTotal={82.4 + 82.4 * tipPercent / 100}
                  isProcessing={false}
                  onSubmit={(event) => event.preventDefault()}      // Pressing PROCESS PAYMENT here must not reload the page
                  onCancel={nothing}
                />
              </div>
            </div>
          </Piece>

          <Piece title="NOTIFICATIONS" wide note="The bell in the top bar with a count and with none, its panel with one notification of each kind and the box for writing a message, and the panel with nothing in it.">
            <div className="gallery-bells">
              <button className="notifications-btn" aria-label="Notifications: 4">
                <Bell size={20} aria-hidden="true" /><span className="notifications-badge" aria-hidden="true">4</span>
              </button>
              <button className="notifications-btn" aria-label="Notifications: none"><Bell size={20} aria-hidden="true" /></button>
            </div>
            <div className="gallery-notifications">
              <NotificationPanel
                notifications={NOTICES.map(notice => ({ ...notice, createdAt: asBackendStamp(serverNow() - notice.minutesAgo * 60 * 1000) }))}
                onDismiss={nothing} onDismissAll={nothing}
                canWrite onSend={async () => {}}
              />
            </div>
            <div className="gallery-notifications">
              <NotificationPanel notifications={[]} onDismiss={nothing} onDismissAll={nothing} canWrite={false} onSend={async () => {}} />
            </div>
          </Piece>

          <Piece title="CHALLENGES" wide note="A challenge's card in everything it can say: in progress, complete with the prize waiting, a check average on track, not started, finished and awarded, finished and not completed. Then the same card as a manager sees it, with everyone's progress and the buttons, and the form for a new challenge.">
            <div className="gallery-challenges">
              {CHALLENGES.map(challenge => (
                <ChallengeCard key={challenge.challengeId} challenge={challengeAt(challenge)} ownStaffId={OWN_ROW} />
              ))}
            </div>
            <div className="gallery-challenge-manager">
              <ChallengeCard
                challenge={challengeAt(MANAGED_CHALLENGE)} ownStaffId={OWN_ROW} canManage
                onEdit={nothing} onEnd={nothing} onDelete={nothing} onAward={nothing}
              />
            </div>
            <div className="gallery-challenge-form">
              <ChallengeForm menuItems={CHALLENGE_MENU} onSave={async () => ({ ok: true, message: '' })} onCancel={nothing} />
            </div>
          </Piece>

          <Piece title="LEADERBOARD AND PERIOD BUTTONS" wide note="The period buttons Financials and the leaderboard share, with the date boxes of a custom period; the leaderboard with a tie for first, a medal for the first three ranks, and your own row; and what it says when nobody has sold anything.">
            <PeriodPicker period={boardPeriod} onChange={setBoardPeriod} />
            <div className="challenges-section gallery-leaderboard">
              <h3 className="section-title">Leaderboard</h3>
              <Leaderboard rows={BOARD} ownStaffId={OWN_ROW} />
            </div>
            <div className="challenges-section gallery-leaderboard">
              <h3 className="section-title">Leaderboard</h3>
              <Leaderboard rows={[]} />
            </div>
          </Piece>

          <Piece title="SEARCH" wide note="The panel under the search box in the top bar: a table, an open check, paid checks, and menu items, with one result under the arrow keys; then what it says when nothing is found.">
            <div className="gallery-search">
              <SearchResults
                results={withTimes(FOUND)} query="7"
                rows={rowsOf(withTimes(FOUND), { openOrders: true, change86: false })}
                highlightedKey="table-1"
                onChoose={nothing} onHighlight={nothing}
                period={searchPeriod} onPeriod={setSearchPeriod}
                failed={false}
              />
            </div>
            <div className="gallery-search">
              <SearchResults
                results={NOTHING_FOUND} query="zucchini" rows={[]}
                highlightedKey={null} onChoose={nothing} onHighlight={nothing}
                period={searchPeriod} onPeriod={setSearchPeriod}
                failed={false}
              />
            </div>
          </Piece>

          <Piece title="MESSAGES AND PAUSED TERMINALS" note="An error box, a refusal under a form, and terminals paused for wrong PINs (Staff screen).">
            <div className="floor-error">The layout could not be saved.</div>
            <div className="eighty-six-error">That could not be changed. Try again.</div>
            <p className="staff-message">That PIN is already in use.</p>
            <div className="staff-paused">
              {PAUSED_TERMINALS.map(terminal => <PausedTerminalRow key={terminal.device} terminal={terminal} onRelease={nothing} />)}
            </div>
          </Piece>
        </div>
      </div>
    </div>
  );
}

export default Gallery;
