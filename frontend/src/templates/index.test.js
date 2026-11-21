import {
  DEFAULT_TEMPLATE, TEMPLATES, isKnownTemplate, showChosenTemplate, previewTemplate, stopPreview, forgetTemplates
} from './index';

/**
 * Tests for switching templates: what is put on the page, what the device remembers,
 * and how a preview and the restaurant's own choice live side by side.
 *
 * "The page" here is the test browser's <html> element. The name of the template in
 * force is its data-template attribute; the stylesheets do the rest, and they are not
 * loaded in these tests.
 *
 * The app has one real template so far, so a second, made-up one is added for the
 * tests that need something to switch to.
 */

const onThePage = () => document.documentElement.getAttribute('data-template');
const remembered = () => window.localStorage.getItem('notta.template');

beforeEach(() => {
  TEMPLATES.push({ id: 'test-look', name: 'Test Look', description: 'Made up for the tests.' });
  window.localStorage.clear();
  forgetTemplates();
});

afterEach(() => {
  TEMPLATES.pop();
  forgetTemplates();
  vi.restoreAllMocks();
});

test('the default template is one that exists', () => {
  expect(DEFAULT_TEMPLATE).toBe('8-bit');
  expect(isKnownTemplate(DEFAULT_TEMPLATE)).toBe(true);
});

test('every template has an id the backend would accept, a name, and a description', () => {
  // The backend stores only names made of lower-case letters, digits, and single hyphens,
  // at most 30 characters (SettingsService). A template with any other id could never be chosen.
  for (const template of TEMPLATES) {
    expect(template.id).toMatch(/^[a-z0-9]+(-[a-z0-9]+)*$/);
    expect(template.id.length).toBeLessThanOrEqual(30);
    expect(template.name).toBeTruthy();
    expect(template.description).toBeTruthy();
  }
  expect(new Set(TEMPLATES.map(template => template.id)).size).toBe(TEMPLATES.length);    // No id twice
});

test('the template the backend names is put on the page and remembered on this device', () => {
  expect(showChosenTemplate('test-look')).toBe('test-look');

  expect(onThePage()).toBe('test-look');
  // Remembered, so that index.html can put it on the page before anyone has signed in
  expect(remembered()).toBe('test-look');
});

test('a name this app has no template for is shown, and remembered, as the default', () => {
  // An older copy of the app, or a value set by hand on the backend. The screens must
  // stay readable, so the default is drawn.
  expect(showChosenTemplate('a-template-from-the-future')).toBe('8-bit');

  expect(onThePage()).toBe('8-bit');
  expect(remembered()).toBe('8-bit');
});

test('nothing but a known name is ever written to the page', () => {
  for (const notAName of [undefined, null, '', '"><script>', 'Test Look']) {
    showChosenTemplate(notAName);
    expect(onThePage()).toBe('8-bit');
  }
});

test('a preview is shown on this device without changing what the restaurant uses or what is remembered', () => {
  showChosenTemplate('8-bit');

  previewTemplate('test-look');

  expect(onThePage()).toBe('test-look');
  expect(remembered()).toBe('8-bit');           // A trial is not what the next person at the PIN pad should see
});

test('while a preview is on, an answer from the backend is noted but does not sweep the preview away', () => {
  showChosenTemplate('8-bit');
  previewTemplate('test-look');

  // The app asks the backend every few seconds; the restaurant's template is still 8-Bit
  showChosenTemplate('8-bit');
  expect(onThePage()).toBe('test-look');

  stopPreview();
  expect(onThePage()).toBe('8-bit');
});

test('stopping a preview goes back to the restaurant\'s template, including one chosen meanwhile', () => {
  showChosenTemplate('8-bit');
  previewTemplate('8-bit');

  // Another manager chooses a template on another terminal while this one is previewing
  showChosenTemplate('test-look');
  stopPreview();

  expect(onThePage()).toBe('test-look');
});

test('previewing something that is not a template does nothing', () => {
  showChosenTemplate('8-bit');

  previewTemplate('no-such-template');

  expect(onThePage()).toBe('8-bit');
});

test('a browser with storage switched off still shows the template', () => {
  vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('storage is off'); });

  expect(() => showChosenTemplate('test-look')).not.toThrow();
  expect(onThePage()).toBe('test-look');
});
