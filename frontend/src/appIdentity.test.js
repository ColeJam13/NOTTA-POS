import { createHash } from 'node:crypto';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { cwd } from 'node:process';
import { defined } from './templates/testSupport';

/**
 * The app says what it is: its name, its icons, and nothing left over from the template
 * it was started from.
 *
 * WHAT: Reads the files a browser reads BEFORE the app's code runs (public/manifest.json,
 *       the icons beside it, and index.html) and checks that they describe NOTTA-POS.
 * WHY:  The project was started with "Create React App", a starter kit, and for a long
 *       time these files still said so: the manifest named the app "Create React App
 *       Sample" and the icons were the React logo. Nothing in the app's own code draws
 *       them, so no other test could notice: they show in the browser's tab, in a
 *       bookmark, and on a phone's home screen.
 *
 * WHAT A MANIFEST IS: a small file that tells a browser the app's name, icons, and
 *       colors, for the places the browser itself draws them. It is plain JSON and
 *       cannot use the design tokens, so its two colors are written out; this test
 *       ties them to the 8-Bit template (the default look), so they cannot drift.
 *
 * (The installable app is step P4 of the plan. This only makes the files truthful.)
 */

const FRONTEND = cwd();                                   // The tests are started from frontend/
const inPublic = (name) => join(FRONTEND, 'public', name);
const manifest = JSON.parse(readFileSync(inPublic('manifest.json'), 'utf8'));
const page = readFileSync(join(FRONTEND, 'index.html'), 'utf8');

// The width and height written inside a PNG file (bytes 16 to 23, after its signature)
const pngSize = (bytes) => ({ width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20) });

// The sizes of the pictures inside an .ico file. An .ico is a small container: a count,
// then one 16-byte entry per picture whose first two bytes are its width and height.
const icoSizes = (bytes) => {
  const count = bytes.readUInt16LE(4);
  return Array.from({ length: count }, (unused, index) => bytes.readUInt8(6 + index * 16));
};

// The icons Create React App ships (the React logo), by fingerprint: the three files that
// sat in public/ until step N1. An icon with one of these fingerprints is that picture.
const CREATE_REACT_APP_ICONS = [
  '3d10f7da6c603178340081668c4ac5b3ae9743ca9a262ab0fcd312fbb9f48bdd',
  'c386396ec70db3608075b5fbfaac4ab1ccaa86ba05a68ab393ec551eb66c3e00',
  '9ea4f4da7050c0cc408926f6a39c253624e9babb1d43c7977cd821445a60b461',
];
const fingerprint = (name) => createHash('sha256').update(readFileSync(inPublic(name))).digest('hex');

test('the manifest names the app Notta-POS and describes it', () => {
  expect(manifest.name).toBe('Notta-POS');
  expect(manifest.short_name).toBe('Notta-POS');
  expect(manifest.description).toMatch(/restaurant/i);
  expect(JSON.stringify(manifest)).not.toMatch(/react/i);        // Nothing of the starter kit's
});

test('the page has the same name, and a description', () => {
  expect(page).toMatch(/<title>Notta-POS<\/title>/);
  expect(page).toMatch(/<meta name="description" content="[^"]*Notta-POS[^"]*"/);
});

test('the colors a browser draws around the app are the 8-Bit template\'s', () => {
  // theme_color: the browser's own bar above the page. The app's top bar is drawn in
  // --color-bg-deep, so the two meet without a seam.
  expect(manifest.theme_color).toBe(defined['--color-bg-deep']);
  // background_color: what a phone shows for the moment before the app has drawn anything
  expect(manifest.background_color).toBe(defined['--color-bg']);
  // The page says the same as the manifest (a browser tab reads the page, an installed app the manifest)
  expect(page).toContain(`<meta name="theme-color" content="${manifest.theme_color}" />`);
});

test('every icon the manifest and the page name is a file, of the size it is said to be', () => {
  for (const icon of manifest.icons) {
    expect(existsSync(inPublic(icon.src)), icon.src).toBe(true);
    const bytes = readFileSync(inPublic(icon.src));
    const said = icon.sizes.split(' ').map(size => Number(size.split('x')[0]));
    if (icon.type === 'image/png') {
      expect(pngSize(bytes), icon.src).toEqual({ width: said[0], height: said[0] });
    } else {
      expect(icoSizes(bytes).sort((a, b) => a - b), icon.src).toEqual(said.sort((a, b) => a - b));
    }
  }
  // The two the page itself asks for
  for (const [, file] of page.matchAll(/<link rel="(?:icon|apple-touch-icon)" href="\/([^"]+)"/g)) {
    expect(existsSync(inPublic(file)), file).toBe(true);
  }
});

test('the icons are not the React logo that Create React App ships', () => {
  for (const icon of manifest.icons) {
    expect(CREATE_REACT_APP_ICONS, icon.src).not.toContain(fingerprint(icon.src));
  }
});

test('the starter kit\'s unused files and package are gone', () => {
  // They measured and drew nothing: a performance reporter that was never given
  // anywhere to report to, the React logo as a drawing, and the package the first used.
  expect(existsSync(join(FRONTEND, 'src', 'reportWebVitals.js'))).toBe(false);
  expect(existsSync(join(FRONTEND, 'src', 'logo.svg'))).toBe(false);
  expect(readFileSync(join(FRONTEND, 'src', 'index.jsx'), 'utf8')).not.toMatch(/reportWebVitals/);

  const packages = JSON.parse(readFileSync(join(FRONTEND, 'package.json'), 'utf8'));
  expect(Object.keys({ ...packages.dependencies, ...packages.devDependencies })).not.toContain('web-vitals');
});
