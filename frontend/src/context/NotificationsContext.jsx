import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { apiFetch, getJson, sendJson } from '../services/api';

/**
 * The signed-in person's notifications, for the bell in the top bar.
 *
 * WHAT: The list of notifications the backend holds for this person and they have not
 *       dismissed (an item was 86'd or is back on, their order is up, someone wrote a
 *       message), and the three things that can be done with them: dismiss one,
 *       dismiss them all, write a message.
 * WHY ONE PLACE: every screen has the top bar, and so every screen shows the bell. If
 *       each screen asked the backend itself, a terminal would ask once per screen it
 *       had ever opened. NotificationsProvider below asks, once, every few seconds,
 *       and every screen reads the same answer:
 *
 *           const { notifications, dismiss } = useNotifications();
 *
 * App.jsx puts the provider around the screens once someone is signed in. Signing
 * out removes it, which stops the asking and forgets the list; the next person gets
 * their own.
 *
 * THE BACKEND DECIDES WHOSE A NOTIFICATION IS (NotificationService). This file never
 * filters by person or role: it shows what it was sent.
 *
 * The default value below is what a component gets when it is drawn on its own (in a
 * test): no notifications, and actions that do nothing.
 */

// How often the backend is asked for the list, in milliseconds. This is how long it
// takes "Garden Toast is 86'd" to reach the other terminals.
export const NOTIFICATIONS_REFRESH_MS = 5000;

const noAnswer = async () => {};

export const NotificationsContext = createContext({
  notifications: [],          // [{ notificationId, kind, text, fromName, createdAt }], newest first
  dismiss: noAnswer,          // dismiss(notificationId)
  dismissAll: noAnswer,       // dismissAll()
  sendMessage: noAnswer       // sendMessage(text): resolves when sent, rejects when it was not
});

// Read the notifications:  const { notifications, dismiss, dismissAll, sendMessage } = useNotifications();
export const useNotifications = () => useContext(NotificationsContext);

/**
 * Asks the backend for the signed-in person's notifications when it appears and every
 * few seconds after, and hands them to everything inside it.
 */
export function NotificationsProvider({ children }) {
  const [notifications, setNotifications] = useState([]);

  const load = useCallback(() => {
    getJson('/api/notifications')
      .then(list => setNotifications(list))
      .catch(() => {});             // No answer: keep what is on screen. It is asked again shortly.
  }, []);

  useEffect(() => {
    load();
    const interval = setInterval(load, NOTIFICATIONS_REFRESH_MS);
    return () => clearInterval(interval);
  }, [load]);

  // Dismissing takes the notification off the screen at once, then tells the backend.
  // If the backend could not be told, the list is asked for again, so the screen goes
  // back to the truth (the notification reappears) and never shows something as
  // dismissed that is not.
  const dismiss = useCallback(async (notificationId) => {
    setNotifications(list => list.filter(notification => notification.notificationId !== notificationId));
    try {
      const response = await apiFetch(`/api/notifications/${notificationId}/dismiss`, { method: 'POST' });
      if (!response.ok && response.status !== 404) load();      // 404: it was already gone (too old). Nothing to put back.
    } catch (error) {
      load();
    }
  }, [load]);

  const dismissAll = useCallback(async () => {
    setNotifications([]);
    try {
      const response = await apiFetch('/api/notifications/dismiss-all', { method: 'POST' });
      if (!response.ok) load();
    } catch (error) {
      load();
    }
  }, [load]);

  // The writer is not shown their own message, so there is nothing to add to the list.
  // The caller is told whether it was sent (the promise resolves) or not (it rejects).
  const sendMessage = useCallback((text) => sendJson('POST', '/api/notifications/messages', { text }), []);

  return (
    <NotificationsContext.Provider value={{ notifications, dismiss, dismissAll, sendMessage }}>
      {children}
    </NotificationsContext.Provider>
  );
}
