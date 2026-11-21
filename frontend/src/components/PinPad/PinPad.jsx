import { useState, useEffect, useCallback } from 'react';
import { Delete } from 'lucide-react';
import './PinPad.css';

// A PIN is this many digits
const PIN_LENGTH = 4;

/**
 * The PIN pad itself: four dots, a line for a message, and the keys.
 *
 * Shared by the two screens that ask for a PIN: signing in (views/SignIn) and
 * choosing a new PIN (views/ChangePin). It only collects four digits and hands
 * them over; what the PIN is FOR is the screen's business.
 *
 * Tap four digits (or type them on a keyboard). The fourth digit hands the PIN
 * over by itself - there is no "enter" to press, which matters when a server
 * signs in dozens of times a shift. The digits are never shown, only dots.
 *
 * What happens after the fourth digit depends on what the screen does with it:
 *   - If onComplete returns a promise (the screen is asking the backend), the
 *     keys are switched off, the dots stay filled, and busyText is shown until
 *     it finishes. Then the pad empties itself.
 *   - If it returns nothing (the screen only noted the PIN and moved to its
 *     next step), the pad empties itself straight away, with no "busy" flash.
 * The screen deals with its own errors; the pad only waits.
 *
 * @param {string} message - What to show under the dots (an error, an instruction), or ''
 * @param {Function} onComplete - Called with the four digits. May return a promise: the pad waits for it.
 * @param {Function} [onEdit] - Called when the person starts typing again or taps CLEAR, so the screen can clear its message
 * @param {string} [busyText] - Shown in place of the message while onComplete is running
 */
function PinPad({ message, onComplete, onEdit, busyText = 'Checking...' }) {
  const [pin, setPin] = useState('');
  const [busy, setBusy] = useState(false);

  // Four digits are in: hand them to the screen, wait for it if it is working, then empty the pad
  const complete = useCallback((fullPin) => {
    const result = onComplete(fullPin);
    const screenIsWorking = result && typeof result.then === 'function';       // A promise: something is under way

    if (!screenIsWorking) {
      setPin('');
      return;
    }

    setBusy(true);
    result.finally(() => {
      setPin('');
      setBusy(false);
    });
  }, [onComplete]);

  const addDigit = useCallback((digit) => {
    if (busy || pin.length >= PIN_LENGTH) return;

    const next = pin + digit;
    if (onEdit) onEdit();
    setPin(next);
    if (next.length === PIN_LENGTH) {
      complete(next);                                     // The last digit sends the PIN
    }
  }, [busy, pin, complete, onEdit]);

  const removeDigit = useCallback(() => {
    if (busy) return;
    setPin(current => current.slice(0, -1));
  }, [busy]);

  const clear = () => {
    setPin('');
    if (onEdit) onEdit();
  };

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
    <>
      {/* One dot per digit: filled as digits are typed. The digits themselves are never shown. */}
      <div className="pin-dots" role="status" aria-label={`${pin.length} of ${PIN_LENGTH} digits entered`}>
        {Array.from({ length: PIN_LENGTH }, (_, index) => (
          <span key={index} className={`pin-dot ${index < pin.length ? 'filled' : ''}`} />
        ))}
      </div>

      {/* Always present so the pad does not jump when a message appears */}
      <p className="pin-message" role="alert">{busy ? busyText : message}</p>

      <div className="pin-pad">
        {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map(digit => (
          <button key={digit} className="pin-key" onClick={() => addDigit(digit)} disabled={busy}>
            {digit}
          </button>
        ))}
        <button className="pin-key pin-key-action" onClick={clear} disabled={busy}>
          CLEAR
        </button>
        <button className="pin-key" onClick={() => addDigit('0')} disabled={busy}>
          0
        </button>
        <button className="pin-key pin-key-action" aria-label="Delete last digit" onClick={removeDigit} disabled={busy}>
          <Delete size={24} />
        </button>
      </div>
    </>
  );
}

export default PinPad;
