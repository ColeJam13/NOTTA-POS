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
| `npm run preview` | Serves what `npm run build` made, to check a build before it is put anywhere. |

CI runs `lint`, `build`, and `test` on every pull request.

## Settings

- **Where the backend is.** With no setting, `http://localhost:8080`. To point somewhere else, create `.env.local` in this folder containing `VITE_API_URL=http://192.168.1.50:8080` and restart `npm start`. Only settings whose names start with `VITE_` reach the app, and the app reads them from `import.meta.env` (`src/services/api.js`).
- **Another port.** `PORT=3001 npm start`. The backend must allow that address (`notta.cors.allowed-origins`).

## Things that differ from Create React App

- **A file that contains JSX is named `.jsx`.** Vite reads a `.js` file as plain JavaScript. Screens, components, and the tests that draw them are `.jsx`; plain logic stays `.js`. An import does not name the ending (`import NavBar from './NavBar'`), so either works.
- **`index.html` is in this folder**, not in `public/`. It names the file the app starts from (`src/index.jsx`).
- **Tests use `vi` where Jest used `jest`**: `vi.fn()`, `vi.spyOn()`. `test`, `expect`, and `vi` need no import.
- **The build does not lint.** `npm run lint` does.

The settings for all of this are in `vite.config.js`, with comments.
