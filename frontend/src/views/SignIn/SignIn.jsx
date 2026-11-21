import { useState, useCallback } from 'react';
import logo from '../../assets/NOTTA LOGO.png';
import PinPad from '../../components/PinPad';
import { apiFetch } from '../../services/api';
import './SignIn.css';

/**
 * The sign-in screen. It is the first thing anyone sees: nothing else in the
 * app can be reached until a PIN is accepted.
 *
 * The pad (components/PinPad) collects the four digits; the fourth digit sends
 * the PIN by itself. A wrong PIN clears the dots and says so. After too many
 * wrong PINs the backend pauses sign-in on this device and its message says
 * for how long, and that a manager can release the terminal (on the Staff
 * screen) so nobody has to wait the pause out.
 *
 * @param {Function} onSignedIn - Called with { staff, token } when a PIN is accepted
 */
function SignIn({ onSignedIn }) {
  const [message, setMessage] = useState('');

  // Ask the backend whose PIN this is
  const submit = useCallback(async (fullPin) => {
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
  }, [onSignedIn]);

  // Typing again (or CLEAR) removes the last message
  const clearMessage = useCallback(() => setMessage(''), []);

  return (
    <div className="sign-in-page">
      <img src={logo} alt="NOTTA-POS Logo" className="sign-in-logo" />

      <h2>ENTER YOUR PIN</h2>

      <PinPad message={message} onComplete={submit} onEdit={clearMessage} />
    </div>
  );
}

export default SignIn;
