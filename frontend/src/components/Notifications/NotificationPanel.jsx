import { useState } from 'react';
import { Ban, Check, MessageSquare, Utensils, X } from 'lucide-react';
import { formatTimeAgo } from '../../utils/formatters';
import './Notifications.css';

// The longest message a person may write (the backend refuses more: MessageRequest)
export const MESSAGE_MAX_LENGTH = 200;

// What each kind of notification is called, and its icon. A kind is told apart by its
// WORD and its ICON, never by a color: all four are drawn in the same colors.
const KINDS = {
  item_86: { word: "86'D", Icon: Ban },
  item_back: { word: 'BACK ON', Icon: Check },
  order_up: { word: 'ORDER UP', Icon: Utensils },
  message: { word: 'MESSAGE', Icon: MessageSquare }
};
// A kind this version of the screen does not know (a newer backend) is still shown, as a message
const kindOf = (notification) => KINDS[notification.kind] || KINDS.message;

/**
 * The panel under the bell: the signed-in person's notifications, and a box to write
 * a message for those who may.
 *
 * WHAT: Each notification with a word and an icon for its kind, its text, who it came
 *       from and how long ago, and a way to dismiss it. DISMISS ALL above them.
 *       With none: "No notifications."
 * WHY A COMPONENT OF ITS OWN: the bell (NotificationBell.jsx) decides when the panel
 *       is open and where the notifications come from; this only draws them. The
 *       template gallery draws it too, with made-up notifications.
 *
 * "How long ago" is worked out from the backend's clock (formatTimeAgo), like every
 * other time in the app.
 *
 * @param {Array} notifications - [{ notificationId, kind, text, fromName, createdAt }], newest first
 * @param {Function} onDismiss - Called with a notificationId
 * @param {Function} onDismissAll - Called with nothing
 * @param {boolean} canWrite - Whether this person may write a message to the floor
 * @param {Function} onSend - Called with the text; returns a promise that resolves when
 *                            the message was sent and rejects when it was not
 */
function NotificationPanel({ notifications, onDismiss, onDismissAll, canWrite, onSend }) {
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [outcome, setOutcome] = useState(null);         // null, 'sent', or 'failed'

  const send = async (event) => {
    event.preventDefault();                             // A form's own "submit" would reload the page
    const text = draft.trim();
    if (text === '' || sending) return;

    setSending(true);
    setOutcome(null);
    try {
      await onSend(text);
      setDraft('');
      setOutcome('sent');
    } catch (error) {
      setOutcome('failed');                             // The words stay in the box, to try again
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="notification-panel" role="dialog" aria-label="Notifications">
      <div className="notification-panel-header">
        <h4>NOTIFICATIONS</h4>
        {notifications.length > 0 && (
          <button type="button" className="notification-dismiss-all" onClick={onDismissAll}>DISMISS ALL</button>
        )}
      </div>

      <div className="notification-list">
        {notifications.length === 0 && <p className="notification-empty">No notifications.</p>}

        {notifications.map(notification => {
          const { word, Icon } = kindOf(notification);
          return (
            <div key={notification.notificationId} className="notification-row">
              <div className="notification-body">
                <span className="notification-kind"><Icon size={14} aria-hidden="true" /> {word}</span>
                <span className="notification-text">{notification.text}</span>
                <span className="notification-meta">
                  {notification.fromName ? `${notification.fromName} · ` : ''}{formatTimeAgo(notification.createdAt)}
                </span>
              </div>
              <button
                type="button"
                className="notification-dismiss"
                aria-label={`Dismiss: ${notification.text}`}
                title="Dismiss"
                onClick={() => onDismiss(notification.notificationId)}
              >
                <X size={16} aria-hidden="true" />
              </button>
            </div>
          );
        })}
      </div>

      {/* Writing a message to everyone else: the kitchen, bartenders, and managers
          (utils/roles.js; the backend refuses anyone else) */}
      {canWrite && (
        <form className="notification-write" onSubmit={send}>
          <label htmlFor="notification-message">Message to everyone</label>
          <div className="notification-write-row">
            <input
              id="notification-message"
              type="text"
              value={draft}
              maxLength={MESSAGE_MAX_LENGTH}
              placeholder="Write a message"
              autoComplete="off"
              onChange={(event) => { setDraft(event.target.value); setOutcome(null); }}
            />
            <button type="submit" className="notification-send" disabled={draft.trim() === '' || sending}>SEND</button>
          </div>
          <div className="notification-write-foot">
            {outcome === 'sent' && <span className="notification-sent" role="status">Sent to everyone else.</span>}
            {outcome === 'failed' && <span className="notification-failed" role="alert">That was not sent. Try again.</span>}
            <span className="notification-count">{draft.length} / {MESSAGE_MAX_LENGTH}</span>
          </div>
        </form>
      )}
    </div>
  );
}

export default NotificationPanel;
