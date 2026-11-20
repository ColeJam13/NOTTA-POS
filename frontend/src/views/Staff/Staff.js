import { useState, useEffect, useCallback } from 'react';
import NavBar from '../../components/NavBar';
import { apiFetch } from '../../services/api';
import { ROLE_LABELS } from '../../utils/roles';
import './Staff.css';

// An empty "add staff" form
const EMPTY_NEW_STAFF = { name: '', role: 'server', pin: '' };

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
 *
 * The backend enforces the rules (one PIN per person, the last manager cannot
 * be removed) and this screen shows its explanation when it refuses.
 */
function Staff({ setCurrentView, setSelectedTable }) {
  const [staffList, setStaffList] = useState([]);
  const [loadError, setLoadError] = useState('');

  // The "add staff" form
  const [newStaff, setNewStaff] = useState(EMPTY_NEW_STAFF);
  const [addMessage, setAddMessage] = useState('');

  // The person being edited: { staffId, name, role, pin, isActive }, or null when no editor is open
  const [editing, setEditing] = useState(null);
  const [editMessage, setEditMessage] = useState('');

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

    setEditing(null);
    setEditMessage('');
    loadStaff();
  };

  return (
    <div className="page-with-nav">
      <NavBar currentView="staff" setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />
      <div className="staff-page">
        <h2>STAFF</h2>

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
                onChange={(event) => setNewStaff({ ...newStaff, name: event.target.value })}
              />
            </label>
            <label>
              Role
              <select value={newStaff.role} onChange={(event) => setNewStaff({ ...newStaff, role: event.target.value })}>
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
                onChange={(event) => setNewStaff({ ...newStaff, pin: pinDigits(event.target.value) })}
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
