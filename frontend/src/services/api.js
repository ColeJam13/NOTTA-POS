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
export const apiFetch = (path, options) => fetch(`${API_BASE_URL}${path}`, options);

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
