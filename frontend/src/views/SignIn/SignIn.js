import { useState, useEffect, useCallback } from 'react';
import { Delete } from 'lucide-react';
import logo from '../../assets/NOTTA LOGO.png';
import { apiFetch } from '../../services/api';
import './SignIn.css';

// A PIN is this many digits
const PIN_LENGTH = 4;

/**
 * The PIN pad. It is the first thing anyone sees: nothing else in the app can
 * be reached until a PIN is accepted.
 *
 * Tap four digits (or type them on a keyboard). The fourth digit sends the PIN
 * by itself - there is no "enter" to press, which matters when a server signs
 * in dozens of times a shift. A wrong PIN clears the dots and says so.
 *
 * @param {Function} onSignedIn - Called with { staff, token } when a PIN is accepted
 */
function SignIn({ onSignedIn }) {
  const [pin, setPin] = useState('');
  const [message, setMessage] = useState('');
  const [checking, setChecking] = useState(false);

  // Ask the backend whose PIN this is
  const submit = useCallback(async (fullPin) => {
    setChecking(true);
    try {
      const response = await apiFetch('/api/staff/sign-in', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ pin: fullPin })
      });

      if (response.ok) {
        onSignedIn(await response.json());
        return;
      }

      // The backend says why in "message": the PIN is not recognized, or there were too many wrong tries
      const error = await response.json().catch(() => null);
      setMessage(error?.message || 'Could not sign in. Try again.');
    } catch (error) {
      console.error('Sign-in error:', error);
      setMessage('Cannot reach the server. Check that the backend is running.');
    }
    setPin('');
    setChecking(false);
  }, [onSignedIn]);

  const addDigit = useCallback((digit) => {
    if (checking || pin.length >= PIN_LENGTH) return;

    const next = pin + digit;
    setMessage('');
    setPin(next);
    if (next.length === PIN_LENGTH) {
      submit(next);                                       // The last digit sends the PIN
    }
  }, [checking, pin, submit]);

  const removeDigit = useCallback(() => {
    if (checking) return;
    setPin(current => current.slice(0, -1));
  }, [checking]);

  // A keyboard works too: digits type, Backspace removes one
  useEffect(() => {
    const onKeyDown = (event) => {
      if (/^[0-9]$/.test(event.key)) addDigit(event.key);
      if (event.key === 'Backspace') removeDigit();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [addDigit, removeDigit]);

  return (
    <div className="sign-in-page">
      <img src={logo} alt="NOTTA-POS Logo" className="sign-in-logo" />

      <h2>ENTER YOUR PIN</h2>

      {/* One dot per digit: filled as digits are typed. The digits themselves are never shown. */}
      <div className="pin-dots" role="status" aria-label={`${pin.length} of ${PIN_LENGTH} digits entered`}>
        {Array.from({ length: PIN_LENGTH }, (_, index) => (
          <span key={index} className={`pin-dot ${index < pin.length ? 'filled' : ''}`} />
        ))}
      </div>

      {/* Always present so the pad does not jump when a message appears */}
      <p className="sign-in-message" role="alert">{checking ? 'Checking...' : message}</p>

      <div className="pin-pad">
        {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map(digit => (
          <button key={digit} className="pin-key" onClick={() => addDigit(digit)} disabled={checking}>
            {digit}
          </button>
        ))}
        <button className="pin-key pin-key-action" onClick={() => { setPin(''); setMessage(''); }} disabled={checking}>
          CLEAR
        </button>
        <button className="pin-key" onClick={() => addDigit('0')} disabled={checking}>
          0
        </button>
        <button className="pin-key pin-key-action" aria-label="Delete last digit" onClick={removeDigit} disabled={checking}>
          <Delete size={24} />
        </button>
      </div>
    </div>
  );
}

export default SignIn;
