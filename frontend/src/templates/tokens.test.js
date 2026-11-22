import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { cwd } from 'node:process';
import { TEMPLATES } from './index';
import {
  all, screens, read, withoutComments, declarationsIn, tokensDefinedIn, tokensUsedIn,
  templateFiles, templateNamesIn, defined, DEFAULT_TEMPLATE_FILE
} from './testSupport';

/**
 * Keeps the design tokens honest.
 *
 * WHAT: Reads every stylesheet in src/ as text and checks:
 *         1. No stylesheet outside src/templates holds a color, a font, a type size, a
 *            corner size, a border width, a shadow, or a gap of its own. They must come
 *            from a token: var(--color-surface), var(--space-10), and so on.
 *         2. Every token a stylesheet uses is defined in the template.
 *         3. Every token the template defines is used somewhere.
 *         4. The list of templates (index.js) and the template stylesheets agree, and
 *            every template defines every token.
 *         5. Nothing is fetched from the internet: fonts ship with the app.
 * WHY:  The whole point of tokens (step B4a of the plan) is that a template can change
 *       the look of the app from one file. One "color: #b026ff" typed into a screen's
 *       stylesheet is a spot the next template cannot reach, and nobody would notice
 *       until the Coffee Shop template had one purple button. This test notices.
 *
 * It reads files; it does not draw anything. That the tokens changed nothing on screen
 * was checked once, when they were introduced, by putting every value back and comparing
 * with the stylesheets as they were.
 *
 * The reading of the files is in testSupport.js, shared with contrast.test.js.
 *
 * WHEN THIS FAILS on a new stylesheet: use an existing token. If the design really
 * needs a value no token has, add a token to the template (named for its job, not its
 * color) and use that.
 */

// What a value may still contain once its tokens are taken out
const stripTokens = (value) => value.replace(/var\(--[a-z0-9-]+\)/g, '');
const A_COLOR = /#[0-9a-fA-F]{3,8}\b|\brgba?\(|\bhsla?\(|\b(white|black|red|green|blue|yellow|purple|orange|gr[ae]y|gold|silver)\b/;
const A_LENGTH = /\d+(px|em|rem|pt)\b/;

// The properties whose values must come from tokens, and what counts as a value of their own
const RULES = [
  { name: 'a color', applies: p => p === 'color' || p.startsWith('background') || p.startsWith('border') || p.startsWith('outline') || p.includes('shadow'), breaks: v => A_COLOR.test(v) },
  { name: 'a font', applies: p => p === 'font-family', breaks: v => v !== '' && v !== 'inherit' },
  { name: 'a type size', applies: p => p === 'font-size', breaks: v => v !== '' },
  { name: 'a font weight', applies: p => p === 'font-weight', breaks: v => v !== '' },
  { name: 'letter or line spacing', applies: p => p === 'letter-spacing' || p === 'line-height', breaks: v => v !== '' },
  { name: 'a corner size', applies: p => p === 'border-radius', breaks: v => A_LENGTH.test(v) || v.includes('%') },
  { name: 'a border width', applies: p => /^border(-(top|right|bottom|left))?(-width)?$/.test(p), breaks: v => A_LENGTH.test(v) },
  { name: 'a shadow', applies: p => p.includes('shadow'), breaks: v => v !== '' && v !== 'none' },
  { name: 'a gap', applies: p => p.startsWith('padding') || p.startsWith('margin') || p === 'gap' || p === 'row-gap' || p === 'column-gap', breaks: v => A_LENGTH.test(v) },
];

test('the template defines tokens, and they were found', () => {
  // If this fails, the test is looking in the wrong place and the others prove nothing
  expect(templateFiles).toContain(DEFAULT_TEMPLATE_FILE);
  expect(Object.keys(defined).length).toBeGreaterThan(100);
  expect(defined['--color-surface']).toBe('#16213e');
  expect(screens.length).toBeGreaterThan(15);
});

test('no stylesheet outside the templates has a color, font, size, corner, border, shadow, or gap of its own', () => {
  const offenders = [];
  for (const file of screens) {
    for (const { selector, property, value } of declarationsIn(read(file))) {
      const leftOver = stripTokens(value).trim();
      for (const rule of RULES) {
        if (rule.applies(property) && rule.breaks(leftOver)) {
          offenders.push(`${file}  ${selector} { ${property}: ${value} }  <- ${rule.name} that is not a token`);
          break;
        }
      }
    }
  }
  expect(offenders).toEqual([]);
});

test('every token a stylesheet uses is defined in the template', () => {
  const missing = [];
  for (const file of all) {
    for (const token of tokensUsedIn(withoutComments(read(file)))) {
      if (!(token in defined)) missing.push(`${file} uses ${token}, which the template does not define`);
    }
  }
  // An undefined token is not an error to a browser: the property is silently dropped,
  // and the thing is drawn with no color or size at all
  expect([...new Set(missing)]).toEqual([]);
});

test('every token the template defines is used', () => {
  const used = new Set(all.flatMap(file => tokensUsedIn(withoutComments(read(file)))));
  const unused = Object.keys(defined).filter(token => !used.has(token));
  // A token nothing uses is a value somebody will change and wonder why nothing happened
  expect(unused).toEqual([]);
});

// ============================================================================
// The templates themselves
// ============================================================================

test('every template in the list has a stylesheet, and every template stylesheet is in the list', () => {
  // A template in the list with no stylesheet would be offered to a manager and draw
  // nothing new. A stylesheet not in the list could never be chosen.
  const namesWithAStylesheet = templateFiles.flatMap(templateNamesIn);

  expect([...new Set(namesWithAStylesheet)].sort()).toEqual(TEMPLATES.map(template => template.id).sort());
});

test('each template stylesheet gives its values to one template only', () => {
  for (const file of templateFiles) {
    expect([...new Set(templateNamesIn(file))], file).toHaveLength(1);
  }
});

test('every template defines every token, and no token of its own', () => {
  // A token a template leaves out silently keeps the default template's value: one
  // neon-green button in the middle of the Coffee Shop. A token only one template has
  // is one no screen can rely on.
  const expected = Object.keys(defined).sort();

  for (const file of templateFiles) {
    expect(Object.keys(tokensDefinedIn(file)).sort(), file).toEqual(expected);
  }
});

test('each template\'s entry in the list agrees with its stylesheet', () => {
  for (const template of TEMPLATES) {
    const file = templateFiles.find(candidate => templateNamesIn(candidate).includes(template.id));
    const tokens = tokensDefinedIn(file);

    // Light or dark, as the card on the Settings screen says it
    expect(['light', 'dark'], template.id).toContain(template.scheme);
    // ...and as the browser is told for its own controls. 8-Bit keeps the browser's
    // default ("normal"), as it always has; every template since says which it is.
    if (file !== DEFAULT_TEMPLATE_FILE) expect(tokens['--color-scheme'], template.id).toBe(template.scheme);

    // Four swatches, each a token the template defines (never a color written into the list)
    expect(template.swatches, template.id).toHaveLength(4);
    for (const swatch of template.swatches) expect(Object.keys(tokens), `${template.id}: ${swatch}`).toContain(swatch);

    expect(['picture', 'text'], template.id).toContain(template.logo);
  }
});

test('nothing is fetched from the internet: no stylesheet or the page itself names an outside address', () => {
  // A restaurant's internet goes down; the app must not lose its fonts when it does.
  // url('../fonts/x.ttf') is a file shipped with the app. url(https://...) is not.
  const outside = /url\(\s*['"]?(https?:)?\/\//i;
  const offenders = all.filter(file => outside.test(withoutComments(read(file))) || /@import/.test(withoutComments(read(file))));
  expect(offenders).toEqual([]);

  const page = readFileSync(join(cwd(), 'index.html'), 'utf8').replace(/<!--[\s\S]*?-->/g, '');
  expect(page).not.toMatch(/(href|src)\s*=\s*["'](https?:)?\/\//i);
});

test('the rules above do catch a literal value (the test tests itself)', () => {
  const breaks = (property, value) => RULES.some(rule => rule.applies(property) && rule.breaks(stripTokens(value).trim()));

  expect(breaks('color', '#b026ff')).toBe(true);
  expect(breaks('background', 'rgba(0, 0, 0, 0.8)')).toBe(true);
  expect(breaks('border', '2px solid var(--color-border)')).toBe(true);
  expect(breaks('border', 'var(--border-width) solid #666')).toBe(true);
  expect(breaks('font-size', '12px')).toBe(true);
  expect(breaks('font-family', "'Press Start 2P', monospace")).toBe(true);
  expect(breaks('padding', '10px var(--space-20)')).toBe(true);
  expect(breaks('border-radius', '8px')).toBe(true);
  expect(breaks('box-shadow', '0 0 20px var(--color-border)')).toBe(true);

  // And they leave alone what is allowed
  expect(breaks('color', 'var(--color-text)')).toBe(false);
  expect(breaks('border', 'var(--border-width) solid var(--color-border)')).toBe(false);
  expect(breaks('border', 'none')).toBe(false);
  expect(breaks('background', 'transparent')).toBe(false);
  expect(breaks('padding', '0 var(--space-10)')).toBe(false);
  expect(breaks('margin', '0 auto')).toBe(false);
  expect(breaks('box-shadow', 'none')).toBe(false);
  expect(breaks('width', '40px')).toBe(false);            // Layout sizes are not tokens
});
