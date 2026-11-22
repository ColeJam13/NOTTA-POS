import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Settings from './Settings';
import { TEMPLATES, forgetTemplates, showChosenTemplate, stopPreview } from '../../templates';
import { TEST_LOOK } from '../../templates/testSupport';

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
 * A made-up template is added to the real ones here, so these tests have one to
 * preview that does not change when a real template is added, renamed, or dropped.
 */

const onThePage = () => document.documentElement.getAttribute('data-template');
const card = (id) => screen.getByTestId(`template-${id}`);

beforeEach(() => {
  TEMPLATES.push(TEST_LOOK);
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

test('the screen lists every template with its name, what it looks like, light or dark, and four of its colors', () => {
  renderScreen();
  const section = screen.getByRole('region', { name: 'Template' });

  expect(within(card('8-bit')).getByRole('heading', { name: '8-Bit' })).toBeInTheDocument();
  expect(within(card('8-bit')).getByText(/pixel font/)).toBeInTheDocument();
  expect(within(card('test-look')).getByRole('heading', { name: 'Test Look' })).toBeInTheDocument();
  // One card per template: the real ones and the made-up one
  expect(within(section).getAllByTestId(/^template-/)).toHaveLength(TEMPLATES.length);

  expect(within(card('8-bit')).getByText('DARK')).toBeInTheDocument();
  expect(within(card('test-look')).getByText('LIGHT')).toBeInTheDocument();

  // Each card's swatches are marked with ITS template, which is what makes the
  // stylesheet draw them in that template's colors whatever the page is showing
  expect(card('8-bit').querySelector('.template-swatches')).toHaveAttribute('data-template', '8-bit');
  expect(card('test-look').querySelector('.template-swatches')).toHaveAttribute('data-template', 'test-look');
  // Four dots each, drawn from tokens and never from a color written into the list
  const dots = [...card('8-bit').querySelectorAll('.template-swatch')];
  expect(dots).toHaveLength(4);
  expect(dots.map(dot => dot.style.background)).toEqual(
    ['var(--color-bg)', 'var(--color-accent-fill)', 'var(--color-primary-fill)', 'var(--color-danger-fill)']);
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

test('every template after 8-Bit is offered, marked light or dark, with the name as its logo', () => {
  renderScreen();

  // What a manager is told before previewing. The first batch, then the second.
  const offered = {
    plain: ['Plain', 'LIGHT'], 'coffee-shop': ['Coffee Shop', 'LIGHT'], nightclub: ['Nightclub', 'DARK'], fancy: ['Fancy', 'DARK'],
    'dive-bar': ['Dive Bar', 'DARK'], brunch: ['Brunch Spot', 'LIGHT'], diner: ['Diner', 'LIGHT'], 'sports-bar': ['Sports Bar', 'DARK']
  };
  for (const [id, [name, scheme]] of Object.entries(offered)) {
    expect(within(card(id)).getByRole('heading', { name })).toBeInTheDocument();
    expect(within(card(id)).getByText(scheme)).toBeInTheDocument();
    expect(within(card(id)).getByRole('button', { name: `Preview ${name}` })).toBeInTheDocument();
    // The NOTTA-POS artwork is drawn in 8-Bit's colors and belongs to 8-Bit alone
    expect(TEMPLATES.find(template => template.id === id).logo).toBe('text');
  }
});

test('OPEN THE GALLERY goes to the gallery', async () => {
  const setCurrentView = vi.fn();
  render(<Settings setCurrentView={setCurrentView} setSelectedTable={vi.fn()} />);

  await userEvent.click(screen.getByRole('button', { name: 'OPEN THE GALLERY' }));

  expect(setCurrentView).toHaveBeenCalledWith('gallery');
});
