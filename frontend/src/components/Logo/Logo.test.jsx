import { render, screen } from '@testing-library/react';
import Logo from './Logo';
import { TEMPLATES, forgetTemplates, showChosenTemplate, previewTemplate, stopPreview } from '../../templates';
import { TEST_LOOK } from '../../templates/testSupport';

/**
 * Tests for the logo: the picture in a template that has artwork, the name as text in
 * one that does not.
 *
 * WHY: The artwork is drawn in the 8-Bit colors and belongs to 8-Bit (the owner's
 *      decision D13). On any other template the Home and PIN screens must not show it.
 *
 * The made-up second template (TEST_LOOK) has "text" as its logo.
 */

beforeEach(() => {
  TEMPLATES.push(TEST_LOOK);
  window.localStorage.clear();
  forgetTemplates();
});

afterEach(() => {
  TEMPLATES.pop();
  forgetTemplates();
});

const theLogo = () => screen.getByRole('img', { name: 'NOTTA-POS Logo' });

test('8-Bit shows the artwork, sized by the screen that asked for it', () => {
  render(<Logo pictureClassName="home-logo" size="large" />);

  expect(theLogo().tagName).toBe('IMG');
  expect(theLogo()).toHaveClass('home-logo');
});

test('a template with no artwork shows the name as text, at the size asked for', () => {
  showChosenTemplate('test-look');
  render(<Logo pictureClassName="home-logo" size="large" />);

  expect(theLogo().tagName).toBe('DIV');
  expect(theLogo()).toHaveTextContent('NOTTA-POS');
  expect(theLogo()).toHaveClass('logo-text', 'logo-text-large');
  expect(theLogo()).not.toHaveClass('home-logo');             // The picture's size does not apply to text
  expect(document.querySelector('img')).toBeNull();
});

test('the logo follows a preview, and goes back when it stops', async () => {
  render(<Logo pictureClassName="sign-in-logo" />);
  expect(theLogo().tagName).toBe('IMG');

  previewTemplate('test-look');
  expect((await screen.findByText('NOTTA-POS')).tagName).toBe('DIV');

  stopPreview();
  await screen.findByAltText('NOTTA-POS Logo');
  expect(theLogo().tagName).toBe('IMG');
});
