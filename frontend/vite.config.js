import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

/**
 * Settings for Vite, the tool that serves the frontend while it is being worked
 * on ("npm start") and builds the files that are put on a server ("npm run
 * build"). It replaced Create React App, which is no longer maintained. Vitest,
 * the test runner ("npm test"), reads its settings from this same file, so the
 * tests are built exactly the way the app is.
 *
 * TWO THINGS THAT ARE DIFFERENT FROM CREATE REACT APP:
 *
 *   A file that contains JSX (the HTML-like <NavBar /> syntax) must be named
 *   .jsx. Vite reads a plain .js file as plain JavaScript and stops at the
 *   first "<". Screens, components, and the tests that draw them are .jsx;
 *   files of plain logic (utils, services, orderActions) stay .js.
 *
 *   Settings for the app are read from import.meta.env, and only those whose
 *   names start with VITE_ are shown to it (see services/api.js).
 *
 * defineConfig does nothing by itself: it lets an editor offer the option names.
 * It is imported from 'vitest/config' and not from 'vite' so that the "test"
 * section below is recognized.
 */
export default defineConfig({
  // Teaches Vite about React: JSX, and "fast refresh" (a saved file updates in
  // the browser without losing what the screen was showing)
  plugins: [react()],

  // "npm start"
  server: {
    // Answer on every network address of this computer, not only "localhost", so a
    // tablet on the same network can open the app at http://192.168.1.50:3000.
    // Create React App did this by default; Vite's default is this computer only.
    host: true,
    // Port 3000, as before. The backend allows this address by default
    // (notta.cors.allowed-origins). PORT=3001 npm start uses another, which is how
    // start.sh passes FRONTEND_PORT on.
    port: Number(process.env.PORT) || 3000,
    // If the port is taken, stop and say so. Vite's default is to quietly move to the
    // next free port, where the backend would refuse every request (wrong origin).
    strictPort: true,
    // Do not open a browser tab: start.sh opens one when everything is up
    open: false
  },

  // "npm run preview": serves the built files, to check a build before it is put anywhere
  preview: {
    host: true,
    port: Number(process.env.PORT) || 3000,
    strictPort: true
  },

  // "npm run build" writes to build/, the folder Create React App used (Vite's own
  // default is dist/), so .gitignore and anything that picks the build up stay as they were
  build: {
    outDir: 'build'
  },

  // "npm test" (Vitest)
  test: {
    // test, expect, beforeEach, and vi are available in every test file without importing
    // them, as they were under Jest. React Testing Library also relies on this to clean
    // the page up after each test.
    globals: true,
    // Tests run in jsdom, a stand-in for a browser, so screens can be drawn and clicked
    environment: 'jsdom',
    // Runs before each test file: adds matchers such as toBeInTheDocument()
    setupFiles: './src/setupTests.js',
    // Before each test, forget what every mock was called with. Create React App set
    // the same thing for Jest (resetMocks), and the tests were written counting on it.
    mockReset: true,
    // Run a file's afterEach steps in the order they were written, as Jest does.
    // (Vitest's default is last-written-first.)
    sequence: { hooks: 'list' },
    // The tests' fake backend answers at http://localhost:8080. A frontend/.env.local
    // that points the app at a tablet-facing address must not reach the tests, or every
    // request in every test would go to an address the fake does not know. (Create
    // React App skipped .env.local in tests by itself. Vite reads it always.)
    env: { VITE_API_URL: '' }
  }
});
