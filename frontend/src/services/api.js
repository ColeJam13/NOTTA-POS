/**
 * Central API module for NOTTA-POS.
 *
 * WHAT: The single place the frontend learns where the backend lives.
 * WHY:  The backend address used to be typed out in 52 separate fetch() calls
 *       across 7 files. Pointing the app at a different server (a demo
 *       machine, a tablet on the restaurant network, a deployed backend) meant
 *       editing every one of them. Now it is one setting.
 *
 * HOW TO CHANGE THE BACKEND ADDRESS:
 *   Create a file named ".env.local" in the frontend folder containing:
 *       REACT_APP_API_URL=http://192.168.1.50:8080
 *   then restart "npm start". With no setting, localhost:8080 is used.
 *   (Create React App only exposes environment variables whose names start
 *   with REACT_APP_.)
 */

// ----------------------------------------------------------------------------
// Sign-in
// ----------------------------------------------------------------------------
// After a PIN is accepted the backend hands back a "token": a long random text
// that stands for the signed-in person. It is sent with every request in this
// header, and the backend uses it to decide what the request is allowed to do.
const SESSION_TOKEN_HEADER = 'X-Staff-Token';

let sessionToken = null;        // The signed-in person's token, or null when nobody is signed in
let onSessionEnded = null;      // Called when the backend says the token is no longer good

// Called by App.js when someone signs in (with their token) or out (with null)
export const setSessionToken = (token) => {
  sessionToken = token;
};

// Lets App.js say what should happen when a session ends on the backend's side
export const setOnSessionEnded = (handler) => {
  onSessionEnded = handler;
};

// Base address of the Spring Boot backend - no trailing slash
export const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

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
    return fetch(`${API_BASE_URL}${path}`, options);
  }

  // Someone is signed in: add their token, so the backend knows who is asking
  const tokenAtSend = sessionToken;
  const withToken = {
    ...options,
    headers: { ...(options && options.headers), [SESSION_TOKEN_HEADER]: tokenAtSend }
  };

  return fetch(`${API_BASE_URL}${path}`, withToken).then(response => {
    // 401 = the backend no longer knows this token (it was restarted, the session timed
    // out, or a manager changed this person's PIN or role). Go back to the PIN screen.
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
