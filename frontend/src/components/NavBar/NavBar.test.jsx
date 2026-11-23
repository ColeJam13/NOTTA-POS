import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import NavBar from './NavBar';
import { StaffContext } from '../../context/StaffContext';

/**
 * Tests for the parts of the bar that step P3 (phone-sized screens) added.
 *
 * On a phone the bar's row of screen buttons is a strip at the bottom that scrolls
 * sideways, and SIGN OUT is drawn as an icon alone. Both are the stylesheet's doing
 * (NavBar.css, "on a phone"), which a test browser does not draw. What is tested here
 * is what the bar itself does for it.
 *
 * The search box and the bell inside the bar have tests of their own.
 */

beforeEach(() => {
  // The bar holds the search box and the bell, which ask the backend nothing until used
  global.fetch = vi.fn(async () => ({ ok: true, status: 200, headers: { get: () => null }, json: async () => [] }));
});

afterEach(() => {
  delete Element.prototype.scrollIntoView;
  vi.restoreAllMocks();
});

const renderBar = (props = {}, staff = { staffId: 7, name: 'Sarah Miller', role: 'server' }, signOut = vi.fn()) => {
  render(
    <StaffContext.Provider value={{ staff, signOut }}>
      <NavBar currentView="activeTables" setCurrentView={vi.fn()} setSelectedTable={vi.fn()} {...props} />
    </StaffContext.Provider>
  );
  return signOut;
};

test('SIGN OUT says what it is even when only its icon is drawn', async () => {
  // On a phone the words are hidden and the icon is all there is to see. The button's
  // name must not depend on the words being drawn.
  const signOut = renderBar();

  const button = screen.getByRole('button', { name: 'SIGN OUT' });
  expect(button).toHaveAttribute('aria-label', 'SIGN OUT');
  expect(button.querySelector('.sign-out-label')).toHaveTextContent('SIGN OUT');

  await userEvent.click(button);
  expect(signOut).toHaveBeenCalledTimes(1);
});

test('the button of the screen that is open is brought into view in the row of buttons', () => {
  // The row scrolls sideways on a phone, and every screen draws its own bar: without
  // this the row would start at its left end each time, with the open screen's button
  // somewhere off the side.
  const broughtIntoView = [];
  Element.prototype.scrollIntoView = function (how) { broughtIntoView.push({ text: this.textContent, how }); };

  renderBar({ currentView: 'eightySix' });

  expect(broughtIntoView).toEqual([{ text: '86 BOARD', how: { block: 'nearest', inline: 'center' } }]);
});

test('a screen with no button in the row (Home, Settings) brings nothing into view, and nothing breaks', () => {
  const broughtIntoView = [];
  Element.prototype.scrollIntoView = function () { broughtIntoView.push(this.textContent); };

  renderBar({ currentView: 'settings' });

  expect(broughtIntoView).toEqual([]);
  expect(screen.getByRole('button', { name: 'TABLES' })).toBeInTheDocument();
});

test('a browser with no scrollIntoView still draws the bar', () => {
  // (The test browser is one: this is the bar as every other test in the app draws it)
  expect(Element.prototype.scrollIntoView).toBeUndefined();

  renderBar({ currentView: 'eightySix' });

  expect(screen.getByRole('button', { name: '86 BOARD' })).toHaveClass('active');
});
