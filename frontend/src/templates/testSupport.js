import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { cwd } from 'node:process';

/**
 * Reading the stylesheets as text, for the tests that check them (tokens.test.js and
 * contrast.test.js). Nothing in the running app uses this file.
 *
 * WHY A TEST READS FILES: the rules about templates ("no stylesheet has a color of its
 * own", "every template defines every token", "this text is readable on that
 * background") are about what is WRITTEN in the stylesheets. The test browser does not
 * apply stylesheets at all, so the only way to check them is to read them.
 */

// The src/ folder. The tests are started from frontend/ (npm test), which is what cwd() answers.
export const SRC = join(cwd(), 'src');
export const TEMPLATES_FOLDER = `templates${sep}`;

// Every .css file under src/, as paths relative to src/
export const stylesheetsUnder = (folder) => readdirSync(folder).flatMap(name => {
  const path = join(folder, name);
  if (statSync(path).isDirectory()) return stylesheetsUnder(path);
  return name.endsWith('.css') ? [relative(SRC, path)] : [];
});

export const withoutComments = (css) => css.replace(/\/\*[\s\S]*?\*\//g, '');

// One stylesheet as a list of { selector, property, value }, skipping @font-face
// blocks (a font's own name and file have to be written out) and @keyframes.
export const declarationsIn = (css) => {
  const found = [];
  const open = [];                                    // the selectors of the blocks we are inside
  let start = 0;
  const text = withoutComments(css);
  // Whatever sits between the last ";" (or "{") and here is one declaration
  const readDeclaration = (end) => {
    const [property, ...rest] = text.slice(start, end).split(':');
    const inside = open.join(' ');
    if (rest.length > 0 && open.length > 0 && !inside.includes('@font-face') && !inside.includes('@keyframes')) {
      found.push({ selector: inside, property: property.trim(), value: rest.join(':').trim() });
    }
  };
  for (let i = 0; i < text.length; i++) {
    const character = text[i];
    if (character === '{') { open.push(text.slice(start, i).trim()); start = i + 1; }
    else if (character === ';') { readDeclaration(i); start = i + 1; }
    else if (character === '}') {
      readDeclaration(i);             // The last line of a block may have no ";" after it. It still counts.
      open.pop(); start = i + 1;
    }
  }
  return found;
};

export const all = stylesheetsUnder(SRC);
export const inTemplatesFolder = all.filter(file => file.startsWith(TEMPLATES_FOLDER));
export const screens = all.filter(file => !file.startsWith(TEMPLATES_FOLDER));
export const read = (file) => readFileSync(join(SRC, file), 'utf8');

// The tokens one stylesheet defines: { '--color-surface': '#16213e', ... }
export const tokensDefinedIn = (file) => Object.fromEntries(
  declarationsIn(read(file))
    .filter(declaration => declaration.property.startsWith('--'))
    .map(declaration => [declaration.property, declaration.value])
);

// The template stylesheets: the files in src/templates that define tokens (fonts.css does not)
export const templateFiles = inTemplatesFolder.filter(file => Object.keys(tokensDefinedIn(file)).length > 0);
export const DEFAULT_TEMPLATE_FILE = `templates${sep}eight-bit.css`;

// The tokens of the default template. Every other template must define the same names.
export const defined = tokensDefinedIn(DEFAULT_TEMPLATE_FILE);

// The template names a stylesheet gives its values to: [data-template="8-bit"] -> '8-bit'
export const templateNamesIn = (file) => [...withoutComments(read(file)).matchAll(/\[data-template="([^"]+)"\]/g)].map(match => match[1]);
export const tokensUsedIn = (text) => [...text.matchAll(/var\((--[a-z0-9-]+)\)/g)].map(match => match[1]);

/**
 * One template's tokens with every var(--other-token) replaced by that token's value,
 * so each is a plain value: { '--status-wait-late': '#ff3366', ... }
 *
 * @param {string} file - A template stylesheet, e.g. 'templates/eight-bit.css'
 */
export const resolvedTokensOf = (file) => {
  const tokens = tokensDefinedIn(file);
  const resolve = (value, depth = 0) => {
    if (depth > 10) throw new Error(`Tokens in ${file} refer to each other in a circle: ${value}`);
    return value.replace(/var\((--[a-z0-9-]+)\)/g, (whole, name) => {
      if (!(name in tokens)) throw new Error(`${file} uses ${name}, which it does not define`);
      return resolve(tokens[name], depth + 1);
    });
  };
  return Object.fromEntries(Object.entries(tokens).map(([name, value]) => [name, resolve(value)]));
};

// The one template name a template stylesheet is for: 'templates/eight-bit.css' -> '8-bit'
export const templateNameOf = (file) => templateNamesIn(file)[0];
