/**
 * Central API module for NOTTA-POS.
 *
 * WHAT: The single place the frontend learns where the backend lives.
 * WHY:  The backend address used to be typed out in 52 separate fetch() calls
 *       across 7 files. Pointing the app at a different server (a demo
 *       machine, a tablet on the restaurant network, a deployed backend) meant
 *       editing every one of them. Now it is one setting.
 *
 * WHERE THE BACKEND IS (see backendAddressFrom below):
 *   - While developing ("npm start"), the frontend and the backend are two
 *     programs on two ports, and the backend is at http://localhost:8080.
 *   - In the packaged build, the backend serves the frontend itself, so the
 *     backend is wherever the page was loaded from. A phone that opened
 *     http://192.168.1.50:8080 sends its requests to that same address, with
 *     nothing to set.
 *
 * HOW TO CHANGE THE BACKEND ADDRESS:
 *   Create a file named ".env.local" in the frontend folder containing:
 *       VITE_API_URL=http://192.168.1.50:8080
 *   then restart "npm start". The setting wins over both cases above.
 *   (Vite, the build tool, only shows the app environment variables whose
 *   names start with VITE_, and the app reads them from import.meta.env.
 *   The name was REACT_APP_API_URL, read from process.env, while the app
 *   was built with Create React App.)
 */

import { noteServerTime } from './serverClock';

// ----------------------------------------------------------------------------
// Sign-in
// ----------------------------------------------------------------------------
// After a PIN is accepted the backend hands back a "token": a long random text
// that stands for the signed-in person. It is sent with every request in this
// header, and the backend uses it to decide what the request is allowed to do.
const SESSION_TOKEN_HEADER = 'X-Staff-Token';

let sessionToken = null;        // The signed-in person's token, or null when nobody is signed in
let onSessionEnded = null;      // Called when the backend says the token is no longer good

// Called by App.jsx when someone signs in (with their token) or out (with null)
export const setSessionToken = (token) => {
  sessionToken = token;
};

// Lets App.jsx say what should happen when a session ends on the backend's side
export const setOnSessionEnded = (handler) => {
  onSessionEnded = handler;
};

// The code the backend puts on its answer when an order is no longer open
// (the check was paid or cleared, usually on another terminal). Screens check for
// this code, never for the wording of the message. It lives here, beside the
// request helpers, because both the order screen and the payment window need it.
export const ORDER_NOT_OPEN = 'ORDER_NOT_OPEN';

/**
 * Work out the backend's address from the app's settings.
 *
 *   1. A VITE_API_URL setting, if there is one.
 *   2. Otherwise, while developing (and in the tests): http://localhost:8080.
 *   3. Otherwise (a built app with no setting): '' - nothing in front of the
 *      path, so '/api/tables' goes to the address the page itself came from.
 *      That is the packaged build, where the backend serves the frontend.
 *
 * It is a function of its own, and not one line below, so the three cases can
 * be tested: the real settings are fixed when the app is built.
 *
 * @param {Object} env - import.meta.env: { VITE_API_URL, DEV, ... }. DEV is true
 *                       under "npm start" and in the tests, false in a built app.
 * @returns {string} - The address, with no trailing slash ('' means "same address")
 */
export const backendAddressFrom = ({ VITE_API_URL, DEV }) =>
  VITE_API_URL || (DEV ? 'http://localhost:8080' : '');

// Base address of the Spring Boot backend - no trailing slash
export const API_BASE_URL = backendAddressFrom(import.meta.env);

/**
 * Drop-in replacement for fetch() that prefixes the backend address.
 *
 * @param {string} path - Path beginning with a slash, e.g. '/api/tables'
 * @param {Object} [options] - Same options object fetch() accepts (method, headers, body)
 * @returns {Promise<Response>} - The same Response promise fetch() returns
 *
 * Example:
 *   apiFetch('/api/tables')                          // GET
 *   apiFetch(`/api/order-items/${id}/start`, { method: 'PUT' })
 */
export const apiFetch = (path, options) => {
  // Nobody signed in: send the request exactly as it was given
  if (!sessionToken) {
    return fetch(`${API_BASE_URL}${path}`, options).then(response => {
      noteServerTime(response);       // Every answer says what time the backend thinks it is (see serverClock.js)
      return response;
    });
  }

  // Someone is signed in: add their token, so the backend knows who is asking
  const tokenAtSend = sessionToken;
  const withToken = {
    ...options,
    headers: { ...(options && options.headers), [SESSION_TOKEN_HEADER]: tokenAtSend }
  };

  return fetch(`${API_BASE_URL}${path}`, withToken).then(response => {
    noteServerTime(response);         // Every answer says what time the backend thinks it is (see serverClock.js)

    // 401 = the backend no longer knows this token (the session was not used for 12 hours,
    // it was signed out, or a manager changed this person's PIN or role). Go back to the
    // PIN screen. A backend restart does not cause this: sessions are kept in the database.
    if (response.status === 401 && sessionToken === tokenAtSend && onSessionEnded) {
      onSessionEnded();
    }
    return response;
  });
};

/**
 * GET a path and parse the JSON response.
 * Throws an Error if the server answers with a non-2xx status, so callers
 * can handle every failure in one catch block.
 *
 * @param {string} path - Path beginning with a slash
 * @returns {Promise<any>} - Parsed JSON body
 */
export const getJson = async (path) => {
  const response = await apiFetch(path);
  if (!response.ok) {
    throw new Error(`GET ${path} failed with status ${response.status}`);
  }
  return response.json();
};

/**
 * Send a JSON body with POST or PUT and parse the JSON response.
 *
 * @param {string} method - 'POST' or 'PUT'
 * @param {string} path - Path beginning with a slash
 * @param {Object} [body] - Object to send as JSON (omit for requests with no body)
 * @returns {Promise<any>} - Parsed JSON body
 */
export const sendJson = async (method, path, body) => {
  const response = await apiFetch(path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  if (!response.ok) {
    throw new Error(`${method} ${path} failed with status ${response.status}`);
  }
  return response.json();
};

/**
 * Send JSON to the backend and report back in one shape, for a form that has to show
 * the backend's own words when something is refused.
 *
 * sendJson (above) throws on a refusal and says only the status. A form needs more:
 * the backend explains a refusal in words written for the person at the screen ("The
 * end has to be after the start.", "Dan has not completed this challenge."), and
 * names each field that failed. This never throws.
 *
 * @param {string} method - 'POST', 'PUT', or 'DELETE'
 * @param {string} path - e.g. '/api/challenges'
 * @param {Object} [body] - Sent as JSON; leave out for a request with no body
 * @returns {Promise<{ok: boolean, data: Object|null, message: string}>} - message is ''
 *          when it worked
 */
export const sendForAnswer = async (method, path, body) => {
  try {
    const response = await apiFetch(path, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    // An answer with no body (204 after a delete) is not an error
    const data = await response.json().catch(() => null);

    if (response.ok) return { ok: true, data, message: '' };

    // Validation errors come back as one message per field; anything else as one message
    const fieldMessages = data?.fieldErrors ? Object.values(data.fieldErrors).join('. ') : '';
    return { ok: false, data: null, message: fieldMessages || data?.message || 'That could not be saved.' };
  } catch (error) {
    console.error(`${method} ${path} failed:`, error);
    return { ok: false, data: null, message: 'Cannot reach the server.' };
  }
};
