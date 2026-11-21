# NOTTA-POS frontend

The screens of NOTTA-POS: a React 19 app, served and built by [Vite](https://vite.dev), tested with [Vitest](https://vitest.dev). (It was built with Create React App until November 2026. Create React App is retired.)

Everything below is run from this folder. To start the whole app at once, use `./start.sh` from the project folder instead.

You need Node.js 22 or 24 (20.19 is the oldest that works).

## Commands

| Command | What it does |
|---|---|
| `npm install` | Installs the packages. Run it once, and again after a pull that changes `package-lock.json`. |
| `npm start` | Serves the app at http://localhost:3000 and updates the browser when a file is saved. Stops with an error if port 3000 is taken. |
| `npm test` | Runs every test once. |
| `npm run test:watch` | Runs the tests, then runs them again whenever a file changes. |
| `npm run lint` | Checks the code against the rules in `eslint.config.js`. Any warning fails it. |
| `npm run build` | Builds the files for a server into `build/`. |
| `npm run preview` | Serves what `npm run build` made, to check a build before it is put anywhere. A built app looks for the backend at the address it was loaded from, so build with `VITE_API_URL=http://localhost:8080 npm run build` first, or the screens will load and every request will fail. |

CI runs `lint`, `build`, and `test` on every pull request.

## Settings

- **Where the backend is.** Under `npm start`, with no setting, `http://localhost:8080`. A built app with no setting uses the address it was loaded from, which is what the packaged build needs (`../package.sh`; the backend then serves these screens itself). To point somewhere else, create `.env.local` in this folder containing `VITE_API_URL=http://192.168.1.50:8080` and restart `npm start`. Only settings whose names start with `VITE_` reach the app, and the app reads them from `import.meta.env` (`src/services/api.js`).
- **Another port.** `PORT=3001 npm start`. The backend must allow that address (`notta.cors.allowed-origins`).

## How the app gets its look

A **template** is the look of the whole app. `src/templates/index.js` lists the templates and switches between them; the restaurant's choice is kept on the backend and made by a manager on the Settings screen. Fonts are in `src/fonts` and ship with the app.

`src/templates/eight-bit.css` gives a name (a "token") to every color, font, size, corner, border width, glow, and gap. Every other stylesheet uses those names (`var(--color-surface)`, `var(--space-10)`) and holds no values of its own, so a different look for the whole app is a second template file with other values. The top of that file explains how the names are built. `npm test` fails if a stylesheet goes back to a literal value (`src/templates/tokens.test.js`).

## Things that differ from Create React App

- **A file that contains JSX is named `.jsx`.** Vite reads a `.js` file as plain JavaScript. Screens, components, and the tests that draw them are `.jsx`; plain logic stays `.js`. An import does not name the ending (`import NavBar from './NavBar'`), so either works.
- **`index.html` is in this folder**, not in `public/`. It names the file the app starts from (`src/index.jsx`).
- **Tests use `vi` where Jest used `jest`**: `vi.fn()`, `vi.spyOn()`. `test`, `expect`, and `vi` need no import.
- **The build does not lint.** `npm run lint` does.

The settings for all of this are in `vite.config.js`, with comments.
