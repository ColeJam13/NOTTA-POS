import { useState, useCallback } from 'react';
import logo from '../../assets/NOTTA LOGO.png';
import PinPad from '../../components/PinPad';
import { apiFetch } from '../../services/api';
import './ChangePin.css';

// The three things the pad asks for, in order
const STEPS = {
  current: 'ENTER YOUR CURRENT PIN',
  choose: 'CHOOSE A NEW PIN',
  confirm: 'ENTER THE NEW PIN AGAIN'
};

/**
 * The "choose a new PIN" screen.
 *
 * WHEN IT IS SHOWN: when the signed-in person must change their PIN before
 * doing anything else (staff.mustChangePin). Today that is the first manager
 * of a new database, who signs in with the starter PIN 1234. App.js shows this
 * screen in place of the whole app, and the backend refuses every other
 * request from that person until a new PIN is set, so this is not just a
 * screen in the way.
 *
 * HOW: the same pad as sign-in, three times.
 *   1. The current PIN. Asked again even though they just signed in: the
 *      backend wants proof that the person choosing the new PIN is the person
 *      who owns the old one, not someone at a terminal that was left signed in.
 *   2. The new PIN.
 *   3. The new PIN again, because the digits are never shown and a slip here
 *      would lock the person out of their own sign-in.
 * Only then is anything sent. If the backend refuses (the current PIN was
 * wrong, the new one is taken), its message is shown and the screen starts
 * again from step 1.
 *
 * The PINs typed in steps 1 and 2 are held in this component only until the
 * request is sent or the screen starts again.
 *
 * @param {Object} staff - The signed-in person: { name, mustChangePin, ... }
 * @param {Function} onPinChanged - Called with the person as the backend now has them (mustChangePin false)
 * @param {Function} onSignOut - Called when they tap SIGN OUT instead
 */
function ChangePin({ staff, onPinChanged, onSignOut }) {
  const [step, setStep] = useState('current');
  const [currentPin, setCurrentPin] = useState('');
  const [newPin, setNewPin] = useState('');
  const [message, setMessage] = useState('');

  // Back to step 1, forgetting what was typed, with a reason shown under the dots
  const startAgain = useCallback((reason) => {
    setCurrentPin('');
    setNewPin('');
    setStep('current');
    setMessage(reason);
  }, []);

  // Send both PINs to the backend
  const sendChange = useCallback(async (current, chosen) => {
    try {
      const response = await apiFetch('/api/staff/me/pin', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ currentPin: current, newPin: chosen })
      });

      if (response.ok) {
        onPinChanged(await response.json());
        return;
      }

      // The backend says why in "message": wrong current PIN, PIN already in use, too many tries
      const error = await response.json().catch(() => null);
      startAgain(error?.message || 'Could not change the PIN. Try again.');
    } catch (error) {
      console.error('Change PIN error:', error);
      startAgain('Cannot reach the server. Check that the backend is running.');
    }
  }, [onPinChanged, startAgain]);

  // The pad has four digits: what they mean depends on the step. Only the last step asks
  // the backend anything; it hands the pad the promise, so the pad shows "Saving..." and
  // waits. The first two return nothing and the pad simply empties for the next PIN.
  const handlePin = useCallback((pin) => {
    if (step === 'current') {
      setCurrentPin(pin);
      setStep('choose');
      return;
    }

    if (step === 'choose') {
      if (pin === currentPin) {
        // No need to ask the backend: the same PIN again is not a change
        setMessage('The new PIN must be different from the current one.');
        return;
      }
      setNewPin(pin);
      setStep('confirm');
      return;
    }

    // step === 'confirm'
    if (pin !== newPin) {
      setNewPin('');
      setStep('choose');
      setMessage('Those two did not match. Choose a new PIN.');
      return;
    }
    return sendChange(currentPin, pin);
  }, [step, currentPin, newPin, sendChange]);

  const clearMessage = useCallback(() => setMessage(''), []);

  return (
    <div className="change-pin-page">
      <img src={logo} alt="NOTTA-POS Logo" className="change-pin-logo" />

      {staff?.mustChangePin && (
        <p className="change-pin-why">
          You signed in with the starter PIN. Choose a PIN of your own to continue.
        </p>
      )}

      <h2>{STEPS[step]}</h2>

      <PinPad message={message} onComplete={handlePin} onEdit={clearMessage} busyText="Saving..." />

      {/* The way out for someone who is not the person signed in */}
      <button className="change-pin-sign-out" onClick={onSignOut}>
        SIGN OUT
      </button>
    </div>
  );
}

export default ChangePin;
