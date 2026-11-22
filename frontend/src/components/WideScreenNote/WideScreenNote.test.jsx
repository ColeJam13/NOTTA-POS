import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { cwd } from 'node:process';
import { render, screen } from '@testing-library/react';
import WideScreenNote from './WideScreenNote';

/**
 * Tests for the note a screen shows on a phone when it is laid out for a wider screen.
 *
 * The note is always in the page; its stylesheet hides it everywhere but on a phone.
 * A test browser applies no stylesheet rules about the width of the window, so the
 * second test reads the stylesheet as text.
 */

test('it says what it is given', () => {
  render(<WideScreenNote>Rearranging the floor needs a wider screen.</WideScreenNote>);

  expect(screen.getByText('Rearranging the floor needs a wider screen.')).toHaveClass('wide-screen-note');
});

test('its stylesheet hides it, and shows it on a phone only', () => {
  const css = readFileSync(join(cwd(), 'src', 'components', 'WideScreenNote', 'WideScreenNote.css'), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
  const [everywhere, onAPhone] = css.split('@media (max-width: 700px)');

  expect(everywhere).toMatch(/\.wide-screen-note\s*\{\s*display:\s*none;/);
  expect(onAPhone).toMatch(/\.wide-screen-note\s*\{[^}]*display:\s*block;/);
});

test('the Floor Map, Financials, Staff, and the gallery each carry one', () => {
  // These are the screens that keep their tablet layout (the plan's step P3). Each says
  // so in its own words; a screen that lost its note would leave a person on a phone
  // wondering whether it was broken.
  const source = (file) => readFileSync(join(cwd(), 'src', 'views', file), 'utf8');

  for (const file of ['FloorMap/FloorMap.jsx', 'Financials/Financials.jsx', 'Staff/Staff.jsx', 'Gallery/Gallery.jsx']) {
    expect(source(file), file).toMatch(/<WideScreenNote>[^<]+<\/WideScreenNote>/);
  }
});
