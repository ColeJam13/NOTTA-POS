import { backendAddressFrom } from './api';

/**
 * Tests for where the frontend looks for the backend.
 *
 * WHY: There are two ways the app runs. While developing, the frontend and the
 *      backend are two programs on two ports. In the packaged build, the backend
 *      serves the frontend, and a phone must send its requests to whatever
 *      address it opened the app at. Getting this wrong does not fail a build:
 *      it produces an app that works on the computer it was built on and shows
 *      "Failed to fetch" on every phone.
 */

test('while developing, with no setting, the backend is on this computer at port 8080', () => {
  expect(backendAddressFrom({ DEV: true })).toBe('http://localhost:8080');
});

test('a built app with no setting uses the address it was loaded from', () => {
  // '' in front of '/api/tables' leaves '/api/tables': the browser sends it to
  // wherever the page came from (http://192.168.1.50:8080 on a phone, say)
  expect(backendAddressFrom({ DEV: false })).toBe('');
});

test('a VITE_API_URL setting wins, developing or built', () => {
  expect(backendAddressFrom({ VITE_API_URL: 'http://192.168.1.50:8080', DEV: true })).toBe('http://192.168.1.50:8080');
  expect(backendAddressFrom({ VITE_API_URL: 'http://192.168.1.50:8080', DEV: false })).toBe('http://192.168.1.50:8080');
});

test('an empty setting counts as no setting', () => {
  // The tests pin VITE_API_URL to '' (vite.config.js), and package.sh builds with it
  // empty, so that a .env.local made for a tablet cannot leak into either
  expect(backendAddressFrom({ VITE_API_URL: '', DEV: true })).toBe('http://localhost:8080');
  expect(backendAddressFrom({ VITE_API_URL: '', DEV: false })).toBe('');
});
