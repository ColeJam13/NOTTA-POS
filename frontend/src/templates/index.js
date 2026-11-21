// Every template's stylesheet is loaded here, so adding a template is done in this one
// file (and its own stylesheet). The default template must be first: the others
// override it, and a later stylesheet wins.
import './fonts.css';
import './eight-bit.css';
import { useSyncExternalStore } from 'react';

/**
 * THE TEMPLATES: which looks the app can be drawn in, and switching between them.
 *
 * WHAT A TEMPLATE IS: one stylesheet in this folder that gives a value to every token
 *      (see the top of eight-bit.css). The screens' own stylesheets only ever use the
 *      tokens, so swapping the template restyles the whole app.
 *
 * HOW ONE IS SWITCHED ON: the page carries the template's name,
 *
 *          <html data-template="8-bit">
 *
 *      and each template's stylesheet says "these values apply where data-template is
 *      my name". Changing that one attribute changes every screen at once, with no
 *      reload. This file is the only code that writes it (index.html writes it once,
 *      before the app has started).
 *
 * WHO DECIDES WHICH: the restaurant has ONE template, chosen by a manager and kept on
 *      the backend (GET /api/settings), so every terminal and phone looks the same.
 *      App.jsx asks for it at sign-in and every few seconds after, and hands the
 *      answer to showChosenTemplate().
 *
 * THE DEVICE REMEMBERS THE LAST ANSWER (in localStorage, which survives closing the
 *      browser). The backend only tells a signed-in person which template is in use,
 *      so without this the PIN pad would always be drawn in the default and then
 *      change the moment someone signed in.
 *
 * PREVIEWING: a manager can try a template on their own device before choosing it for
 *      everyone (the Settings screen starts a preview; the bar that then sits at the
 *      top of every screen ends it). While a preview is on, answers from the backend
 *      are noted but not shown, so the preview is not swept away every few seconds.
 *
 * SCREENS THAT SHOW WHICH TEMPLATE IS IN USE read it with the hook at the bottom:
 *
 *          const { chosen, previewing } = useTemplates();
 *
 *      and are redrawn whenever either changes.
 *
 * TO ADD A TEMPLATE:
 *   1. Copy eight-bit.css to a new file and change the values. Its first line must be
 *      [data-template="its-name"] { ... } and it must define every token.
 *   2. Import the file above, after eight-bit.css.
 *   3. Add it to TEMPLATES below.
 *   tokens.test.js fails if any of the three is missing or a token was left out.
 *   The backend needs no change: it stores the name and nothing else.
 */

// The template a restaurant has until a manager chooses another, and the one drawn
// when a name is not recognized. The backend's default is the same (SettingsService).
export const DEFAULT_TEMPLATE = '8-bit';

// Every template, in the order the Settings screen lists them.
//   id          the name stored on the backend and written on the page: lower-case
//               letters, digits, and hyphens only (the backend refuses anything else)
//   name        what a manager sees
//   description one line about the look, for the Settings screen
export const TEMPLATES = [
  {
    id: '8-bit',
    name: '8-Bit',
    description: 'Neon on dark, set in a pixel font. The look NOTTA-POS has always had.'
  }
];

// Where this device keeps the last template the backend named
const STORAGE_KEY = 'notta.template';

let chosen = DEFAULT_TEMPLATE;      // The restaurant's template, as last heard from the backend
let previewing = null;              // The template being tried out on this device, or null

// Screens that asked to be told when the template changes (through useTemplates), and
// the state they are handed. A NEW object is made on every change: React compares the
// old and new one to decide whether to redraw, so it must not be the same object edited.
const listeners = new Set();
let state = Object.freeze({ chosen, previewing });
const changed = () => {
  state = Object.freeze({ chosen, previewing });
  listeners.forEach(listener => listener());
};

export const isKnownTemplate = (id) => TEMPLATES.some(template => template.id === id);

// Put a template's name on the page. Everything on screen is redrawn by the browser.
const draw = (id) => {
  document.documentElement.setAttribute('data-template', id);
};

const remember = (id) => {
  try {
    window.localStorage.setItem(STORAGE_KEY, id);
  } catch (error) {
    // Storage is switched off in this browser. The template still works; the PIN pad
    // will be drawn in the default until someone signs in.
  }
};

/**
 * The backend said which template the restaurant uses: show it, and remember it.
 *
 * A name this app does not have a template for (an older copy of the app, or a value
 * set by hand) is shown as the default. Nothing breaks; the screens stay readable.
 *
 * @param {string} id - The template's name, e.g. '8-bit'
 * @returns {string} - The template now in use on this device's record (the name, or the default)
 */
export const showChosenTemplate = (id) => {
  const before = chosen;
  chosen = isKnownTemplate(id) ? id : DEFAULT_TEMPLATE;
  remember(chosen);
  if (previewing === null) draw(chosen);
  if (chosen !== before) changed();
  return chosen;
};

/**
 * Try a template on this device only. Nothing is sent to the backend, and no other
 * terminal changes.
 *
 * @param {string} id - A template in TEMPLATES (anything else is ignored)
 */
export const previewTemplate = (id) => {
  if (!isKnownTemplate(id)) return;
  previewing = id;
  draw(id);
  changed();
};

// Stop trying a template out: back to the restaurant's own
export const stopPreview = () => {
  if (previewing === null) return;
  previewing = null;
  draw(chosen);
  changed();
};

// For tests: back to how a newly loaded page starts
export const forgetTemplates = () => {
  chosen = DEFAULT_TEMPLATE;
  previewing = null;
  document.documentElement.removeAttribute('data-template');
  changed();
};

/**
 * Read the templates' state in a component, and be redrawn when it changes:
 *
 *     const { chosen, previewing } = useTemplates();
 *
 *   chosen      the restaurant's template, as last heard from the backend
 *   previewing  the template being tried on this device, or null
 *
 * useSyncExternalStore is React's hook for state that lives OUTSIDE React (here, the
 * variables in this file). It is given a way to be told of changes and a way to read
 * the current state.
 */
export const useTemplates = () => useSyncExternalStore(
  (listener) => { listeners.add(listener); return () => listeners.delete(listener); },
  () => state
);
