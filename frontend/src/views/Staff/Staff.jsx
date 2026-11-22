import { useState, useEffect, useCallback } from 'react';
import NavBar from '../../components/NavBar';
import { apiFetch } from '../../services/api';
import { ROLE_LABELS } from '../../utils/roles';
import PausedTerminalRow from './PausedTerminalRow';
import { useStaff } from '../../context/StaffContext';
import WideScreenNote from '../../components/WideScreenNote';
import './Staff.css';

// An empty "add staff" form
const EMPTY_NEW_STAFF = { name: '', role: 'server', pin: '' };

// How often the screen asks which terminals are paused, in milliseconds. A pause is five
// minutes at most and the time left is shown in words, so this does not need to be quick.
const PAUSED_REFRESH_MS = 5000;

// Keep only digits, at most four: what a PIN box may hold
const pinDigits = (text) => text.replace(/\D/g, '').slice(0, 4);

/**
 * Send JSON to the backend and report back in one shape, so the screen can
 * show the backend's own explanation when something is refused.
 *
 * @returns {Promise<{ok: boolean, data: Object|null, message: string}>}
 */
const sendStaffRequest = async (method, path, body) => {
  try {
    const response = await apiFetch(path, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    const data = await response.json().catch(() => null);

    if (response.ok) return { ok: true, data, message: '' };

    // Validation errors come back as a list per field; anything else as one message
    const fieldMessages = data?.fieldErrors ? Object.values(data.fieldErrors).join(' ') : '';
    return { ok: false, data: null, message: fieldMessages || data?.message || 'That could not be saved.' };
  } catch (error) {
    console.error('Staff request failed:', error);
    return { ok: false, data: null, message: 'Cannot reach the server.' };
  }
};

/**
 * Staff screen (managers only): everyone who works here, with a form to add
 * someone and an editor for each person.
 *
 * What a manager can do:
 *   - add a person with a name, a role, and a 4-digit PIN
 *   - change a name or role
 *   - set a new PIN (PINs cannot be looked up - only replaced)
 *   - deactivate someone who has left, so their PIN stops working. They stay
 *     on the list, because old orders still carry their name. Bringing them
 *     back needs a new PIN.
 *   - see which terminals are paused after too many wrong PINs, and release
 *     one so people can sign in there again without waiting the pause out.
 *     A terminal is known by its network address. Releasing signs nobody in.
 *
 * The backend enforces the rules (one PIN per person, the last manager cannot
 * be removed) and this screen shows its explanation when it refuses.
 */
function Staff({ setCurrentView, setSelectedTable }) {
  const { staff: signedInStaff, signOut } = useStaff();   // The manager using this screen

  const [staffList, setStaffList] = useState([]);
  const [loadError, setLoadError] = useState('');

  // The "add staff" form
  const [newStaff, setNewStaff] = useState(EMPTY_NEW_STAFF);
  const [addMessage, setAddMessage] = useState('');

  // The person being edited: { staffId, name, role, pin, isActive }, or null when no editor is open
  const [editing, setEditing] = useState(null);
  const [editMessage, setEditMessage] = useState('');

  // Terminals where sign-in is paused: [{ device, secondsLeft, thisDevice }].
  // null means "not known" (not loaded yet, or the request failed), which the screen
  // must not show as "none paused".
  const [pausedTerminals, setPausedTerminals] = useState(null);
  const [pausedLoadFailed, setPausedLoadFailed] = useState(false);
  const [releaseMessage, setReleaseMessage] = useState('');

  const loadPausedTerminals = useCallback(() => {
    apiFetch('/api/staff/paused-devices')
      .then(response => {
        if (!response.ok) throw new Error(`GET /api/staff/paused-devices failed with status ${response.status}`);
        return response.json();
      })
      .then(data => { setPausedTerminals(data); setPausedLoadFailed(false); })
      .catch(error => {
        console.error('Error fetching paused terminals:', error);
        setPausedTerminals(null);
        setPausedLoadFailed(true);
      });
  }, []);

  useEffect(() => {
    loadPausedTerminals();                                                    // Load on mount...
    const interval = setInterval(loadPausedTerminals, PAUSED_REFRESH_MS);     // ...then keep the list and the time left current
    return () => clearInterval(interval);
  }, [loadPausedTerminals]);

  // Let a paused terminal sign people in again. The backend answers with the list as it
  // now stands, so the screen redraws from that one answer.
  const releaseTerminal = async (device) => {
    const result = await sendStaffRequest('POST', '/api/staff/paused-devices/release', { device });

    if (!result.ok) {
      setReleaseMessage(result.message);
      return;
    }
    setReleaseMessage('');
    setPausedTerminals(result.data);
  };

  const loadStaff = useCallback(() => {
    apiFetch('/api/staff')
      .then(response => {
        if (!response.ok) throw new Error(`GET /api/staff failed with status ${response.status}`);
        return response.json();
      })
      .then(data => { setStaffList(data); setLoadError(''); })
      .catch(error => {
        console.error('Error fetching staff:', error);
        setLoadError('The staff list could not be loaded.');
      });
  }, []);

  useEffect(() => {
    loadStaff();
  }, [loadStaff]);

  const addStaff = async (event) => {
    event.preventDefault();                               // Stop the browser reloading the page, which is what a form does by default

    const result = await sendStaffRequest('POST', '/api/staff', {
      name: newStaff.name,
      role: newStaff.role,
      pin: newStaff.pin
    });

    if (!result.ok) {
      setAddMessage(result.message);
      return;
    }

    setNewStaff(EMPTY_NEW_STAFF);
    setAddMessage('');
    loadStaff();
  };

  const startEditing = (person) => {
    setEditing({ ...person, pin: '' });                   // The PIN box starts empty: empty means "leave the PIN as it is"
    setEditMessage('');
  };

  const saveEdit = async (event) => {
    event.preventDefault();

    // Send only what can change. A PIN is only sent when a new one was typed.
    const changes = { name: editing.name, role: editing.role, isActive: editing.isActive };
    if (editing.pin !== '') {
      changes.pin = editing.pin;
    }

    const result = await sendStaffRequest('PUT', `/api/staff/${editing.staffId}`, changes);

    if (!result.ok) {
      setEditMessage(result.message);
      return;
    }

    // A manager who changes their OWN PIN or role (or switches themselves off) is signed out
    // by the backend, the same as anyone else whose sign-in changes. Say so and go to the PIN
    // pad now, instead of letting the next request fail without explanation.
    const original = staffList.find(person => person.staffId === editing.staffId);
    const ownSignInChanged = signedInStaff && editing.staffId === signedInStaff.staffId &&
      (editing.pin !== '' || editing.role !== original?.role || editing.isActive !== original?.isActive);
    if (ownSignInChanged) {
      alert('Your own sign-in was changed. Sign in again to continue.');
      signOut();
      return;
    }

    setEditing(null);
    setEditMessage('');
    loadStaff();
  };

  // Change one field of the "add staff" form. Editing the form also clears the last
  // error, so an old message never sits under new input.
  const changeNewStaff = (field, value) => {
    setNewStaff({ ...newStaff, [field]: value });
    setAddMessage('');
  };

  return (
    <div className="page-with-nav">
      <NavBar currentView="staff" setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />
      <div className="staff-page">
        <h2>STAFF</h2>
        {/* Drawn on a phone only */}
        <WideScreenNote>This screen is laid out for a tablet or a wider screen. On a phone everything is here, one thing under another.</WideScreenNote>

        {/* --- Terminals paused for wrong PINs --- */}
        {/* A <section> with a name is a "region": a screen reader can jump to it, and the tests find it by that name */}
        <section className="staff-paused" aria-label="Paused terminals">
          <h3>PAUSED TERMINALS</h3>
          <p className="staff-paused-help">
            Too many wrong PINs pause sign-in on that terminal, for five minutes at most.
            Releasing one lets people sign in there again straight away. It signs nobody in.
          </p>

          {pausedLoadFailed && <p className="staff-message" role="alert">The paused terminals could not be loaded.</p>}
          {pausedTerminals && pausedTerminals.length === 0 && <p className="staff-paused-none">No terminal is paused.</p>}

          {pausedTerminals && pausedTerminals.map(terminal => (
            <PausedTerminalRow key={terminal.device} terminal={terminal} onRelease={releaseTerminal} />
          ))}

          {releaseMessage && <p className="staff-message" role="alert">{releaseMessage}</p>}
        </section>

        {/* --- Add someone --- */}
        <form className="staff-add" onSubmit={addStaff} aria-label="Add staff">
          <h3>ADD STAFF</h3>
          <div className="staff-form-row">
            <label>
              Name
              <input
                type="text"
                value={newStaff.name}
                maxLength={50}
                onChange={(event) => changeNewStaff('name', event.target.value)}
              />
            </label>
            <label>
              Role
              <select value={newStaff.role} onChange={(event) => changeNewStaff('role', event.target.value)}>
                {Object.entries(ROLE_LABELS).map(([role, label]) => (
                  <option key={role} value={role}>{label}</option>
                ))}
              </select>
            </label>
            <label>
              PIN (4 digits)
              <input
                type="password"                           // Shows dots, so a PIN is not read over a shoulder
                inputMode="numeric"                       // Brings up the number keys on a tablet
                autoComplete="off"
                value={newStaff.pin}
                onChange={(event) => changeNewStaff('pin', pinDigits(event.target.value))}
              />
            </label>
            <button type="submit" className="staff-btn staff-btn-primary" disabled={newStaff.name.trim() === '' || newStaff.pin.length !== 4}>
              ADD
            </button>
          </div>
          {addMessage && <p className="staff-message" role="alert">{addMessage}</p>}
        </form>

        {loadError && <p className="staff-message" role="alert">{loadError}</p>}

        {/* --- Everyone --- */}
        <div className="staff-list">
          {staffList.map(person => (
            <div key={person.staffId} className={`staff-row ${person.isActive ? '' : 'inactive'}`} data-testid={`staff-${person.staffId}`}>
              {editing && editing.staffId === person.staffId ? (
                // --- The editor for this person ---
                <form className="staff-edit" onSubmit={saveEdit} aria-label={`Edit ${person.name}`}>
                  <div className="staff-form-row">
                    <label>
                      Name
                      <input
                        type="text"
                        value={editing.name}
                        maxLength={50}
                        onChange={(event) => setEditing({ ...editing, name: event.target.value })}
                      />
                    </label>
                    <label>
                      Role
                      <select value={editing.role} onChange={(event) => setEditing({ ...editing, role: event.target.value })}>
                        {Object.entries(ROLE_LABELS).map(([role, label]) => (
                          <option key={role} value={role}>{label}</option>
                        ))}
                      </select>
                    </label>
                    <label>
                      New PIN (leave empty to keep)
                      <input
                        type="password"
                        inputMode="numeric"
                        autoComplete="off"
                        value={editing.pin}
                        onChange={(event) => setEditing({ ...editing, pin: pinDigits(event.target.value) })}
                      />
                    </label>
                    <label className="staff-active-toggle">
                      <input
                        type="checkbox"
                        checked={editing.isActive}
                        onChange={(event) => setEditing({ ...editing, isActive: event.target.checked })}
                      />
                      Can sign in
                    </label>
                  </div>
                  <div className="staff-edit-actions">
                    <button
                      type="submit"
                      className="staff-btn staff-btn-primary"
                      disabled={editing.name.trim() === '' || (editing.pin !== '' && editing.pin.length !== 4)}
                    >
                      SAVE
                    </button>
                    <button type="button" className="staff-btn" onClick={() => setEditing(null)}>CANCEL</button>
                  </div>
                  {editMessage && <p className="staff-message" role="alert">{editMessage}</p>}
                </form>
              ) : (
                // --- One line per person ---
                <>
                  <span className="staff-name">{person.name}</span>
                  <span className="staff-role">{ROLE_LABELS[person.role] || person.role}</span>
                  <span className="staff-status">{person.isActive ? 'ACTIVE' : 'INACTIVE'}</span>
                  <button className="staff-btn" aria-label={`Edit ${person.name}`} onClick={() => startEditing(person)}>EDIT</button>
                </>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

export default Staff;
