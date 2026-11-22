import { declarationsIn, read } from '../../templates/testSupport';
import { join } from 'node:path';

/**
 * One rule about the modifier picker's stylesheet, checked by reading it.
 *
 * WHAT: A chosen modifier keeps its "chosen" background while the pointer is on it.
 * WHY:  A chosen option is drawn as a filled button: the primary color behind, and the
 *       text color made for that fill on top. The stylesheet also lightens any option
 *       under the pointer. That second rule used to win over the first for the
 *       BACKGROUND only, so a chosen option under the pointer got the pale hover
 *       background with the fill's text color still on it: dark on dark in 8-Bit,
 *       white on white in a light template. The name of the modifier just chosen
 *       could not be read, and on a touch screen the "pointer" stays where the finger
 *       last tapped, which is exactly on the option just chosen.
 *
 * The test browser does not apply stylesheets or know about a pointer, so this cannot
 * be tested by drawing the picker. It reads the stylesheet instead (see
 * templates/testSupport.js, which the token tests use the same way).
 */

const rules = declarationsIn(read(join('views', 'CreateOrder', 'ModifierPicker.css')));

test('the stylesheet was found and has the rules this test is about', () => {
  // If the picker's classes are renamed, this fails first and says why the next one would prove nothing
  expect(rules.some(rule => rule.selector === '.modifier-option.selected' && rule.property === 'background')).toBe(true);
  expect(rules.some(rule => rule.selector.includes('.modifier-option:hover') && rule.property === 'background')).toBe(true);
});

test('pointing at a chosen modifier does not take away its chosen background', () => {
  // Every rule that recolors an option under the pointer must leave chosen options alone
  const hoverBackgrounds = rules.filter(rule =>
    rule.selector.includes('.modifier-option:hover') && rule.property.startsWith('background'));

  for (const rule of hoverBackgrounds) {
    expect(rule.selector, `${rule.selector} { ${rule.property}: ${rule.value} }`).toContain(':not(.selected)');
  }
});
