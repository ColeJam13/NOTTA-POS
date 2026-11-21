import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Settings from './Settings';
import { TEMPLATES, forgetTemplates, showChosenTemplate, stopPreview } from '../../templates';

/**
 * Behavior tests for the Settings screen (managers only): the list of templates.
 *
 * WHAT: Draws the screen and checks what it lists, which template it marks as in
 *       use, and that PREVIEW tries a template on this device only.
 * WHY:  This screen changes the look of every terminal in the restaurant. It must be
 *       plain which template is in use, and trying one must not change anything for
 *       anyone else.
 *
 * The screen sends nothing to the backend itself (choosing is the preview bar's job,
 * tested with that component), so these tests replace fetch with one that fails the
 * test if it is ever called.
 *
 * The app has one real template so far. A second, made-up one is added here so there
 * is something to preview.
 */

const onThePage = () => document.documentElement.getAttribute('data-template');
const card = (id) => screen.getByTestId(`template-${id}`);

beforeEach(() => {
  TEMPLATES.push({ id: 'test-look', name: 'Test Look', description: 'Made up for the tests.' });
  window.localStorage.clear();
  forgetTemplates();
  showChosenTemplate('8-bit');                        // What the backend says the restaurant uses

  global.fetch = vi.fn(async (url) => { throw new Error(`Unexpected request in test: ${url}`); });
});

afterEach(() => {
  TEMPLATES.pop();
  forgetTemplates();
  vi.restoreAllMocks();
});

const renderScreen = () => render(<Settings setCurrentView={vi.fn()} setSelectedTable={vi.fn()} />);

test('the screen lists every template with its name, what it looks like, and a sample', () => {
  renderScreen();
  const section = screen.getByRole('region', { name: 'Template' });

  expect(within(card('8-bit')).getByRole('heading', { name: '8-Bit' })).toBeInTheDocument();
  expect(within(card('8-bit')).getByText(/pixel font/)).toBeInTheDocument();
  expect(within(card('test-look')).getByRole('heading', { name: 'Test Look' })).toBeInTheDocument();
  expect(within(section).getAllByTestId(/^template-/)).toHaveLength(2);

  // Each card's sample is marked with ITS template, which is what makes the stylesheet
  // draw it in that template whatever the page around it is showing
  expect(card('8-bit').querySelector('.template-sample')).toHaveAttribute('data-template', '8-bit');
  expect(card('test-look').querySelector('.template-sample')).toHaveAttribute('data-template', 'test-look');
});

test('the template the restaurant uses is marked IN USE and cannot be previewed', () => {
  renderScreen();

  expect(within(card('8-bit')).getByText('IN USE')).toBeInTheDocument();
  expect(card('8-bit')).toHaveClass('in-use');
  expect(within(card('8-bit')).queryByRole('button')).not.toBeInTheDocument();

  expect(within(card('test-look')).queryByText('IN USE')).not.toBeInTheDocument();
  expect(within(card('test-look')).getByRole('button', { name: 'Preview Test Look' })).toBeInTheDocument();
});

test('PREVIEW shows that template on this device and sends nothing to the backend', async () => {
  renderScreen();
  expect(onThePage()).toBe('8-bit');

  await userEvent.click(screen.getByRole('button', { name: 'Preview Test Look' }));

  expect(onThePage()).toBe('test-look');
  expect(within(card('test-look')).getByText('PREVIEWING')).toBeInTheDocument();
  expect(within(card('test-look')).queryByRole('button')).not.toBeInTheDocument();
  // 8-Bit is still what the restaurant uses, and is still marked so
  expect(within(card('8-bit')).getByText('IN USE')).toBeInTheDocument();
  expect(window.localStorage.getItem('notta.template')).toBe('8-bit');
  expect(global.fetch).not.toHaveBeenCalled();
});

test('when a preview is stopped, the card offers PREVIEW again', async () => {
  renderScreen();
  await userEvent.click(screen.getByRole('button', { name: 'Preview Test Look' }));

  stopPreview();                                      // What STOP PREVIEW on the bar does

  expect(await screen.findByRole('button', { name: 'Preview Test Look' })).toBeInTheDocument();
  expect(onThePage()).toBe('8-bit');
});

test('when the restaurant\'s template changes while the screen is open, IN USE moves to it', async () => {
  renderScreen();

  showChosenTemplate('test-look');                    // The app heard it from the backend: someone chose it

  expect(await within(card('test-look')).findByText('IN USE')).toBeInTheDocument();
  expect(within(card('8-bit')).queryByText('IN USE')).not.toBeInTheDocument();
  expect(within(card('8-bit')).getByRole('button', { name: 'Preview 8-Bit' })).toBeInTheDocument();
});

test('with a single template, the screen says more are coming and offers nothing to press', () => {
  TEMPLATES.pop();                                    // Only the real one
  renderScreen();

  expect(screen.getByText(/only template so far/)).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /^Preview/ })).not.toBeInTheDocument();

  TEMPLATES.push({ id: 'test-look', name: 'Test Look', description: 'Made up for the tests.' });    // For afterEach to remove
});
