import { useEffect, useRef, useState } from 'react';
import { Bell } from 'lucide-react';
import { useNotifications } from '../../context/NotificationsContext';
import { useStaff } from '../../context/StaffContext';
import { canDo } from '../../utils/roles';
import NotificationPanel from './NotificationPanel';

/**
 * The bell in the top bar: how many notifications the signed-in person has, and the
 * panel that lists them.
 *
 * THE NUMBER IS THE BACKEND'S. It is the length of the list the backend sent for this
 * person (context/NotificationsContext). At zero there is no badge at all: it never
 * shows 0, and it never shows a number that was not sent. (It used to show a 2 that
 * was typed into the code.)
 *
 * Pressing the bell opens the panel; pressing it again, Escape, or a click anywhere
 * else closes it.
 */
function NotificationBell() {
  const { notifications, dismiss, dismissAll, sendMessage } = useNotifications();
  const { staff } = useStaff();
  const [open, setOpen] = useState(false);
  const bell = useRef(null);                            // The button and its panel, to tell a click inside from one outside

  // A click anywhere else, or Escape, closes the panel
  useEffect(() => {
    if (!open) return undefined;
    const closeIfOutside = (event) => {
      if (bell.current && !bell.current.contains(event.target)) setOpen(false);
    };
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', closeIfOutside);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('mousedown', closeIfOutside);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [open]);

  const count = notifications.length;

  return (
    <div className="notifications" ref={bell}>
      <button
        type="button"
        className={`notifications-btn ${open ? 'active' : ''}`}
        aria-label={count === 0 ? 'Notifications: none' : `Notifications: ${count}`}
        aria-expanded={open}
        title="Notifications"
        onClick={() => setOpen(wasOpen => !wasOpen)}
      >
        <Bell size={20} aria-hidden="true" />
        {count > 0 && <span className="notifications-badge" aria-hidden="true">{count}</span>}
      </button>

      {open && (
        <NotificationPanel
          notifications={notifications}
          onDismiss={dismiss}
          onDismissAll={dismissAll}
          canWrite={canDo(staff?.role, 'send-message')}
          onSend={sendMessage}
        />
      )}
    </div>
  );
}

export default NotificationBell;
