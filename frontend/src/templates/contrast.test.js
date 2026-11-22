import { parseColor, colorsIn, contrastRatio, lowestContrast, faded } from './contrast';
import { templateFiles, templateNameOf, resolvedTokensOf } from './testSupport';

/**
 * Can everything be read, in every template?
 *
 * WHAT: Reads each template's tokens and works out the contrast ratio of a fixed list
 *       of pairs: this text token on that background token.
 * WHY:  A template is a file of colors. It is easy to pick a muted grey that looks
 *       fine on the designer's screen and cannot be read on a kitchen tablet under a
 *       heat lamp. This test does not judge taste; it refuses a template in which
 *       something cannot be read. (What a contrast ratio is: see contrast.js.)
 *
 * THE LIST BELOW SAYS WHERE EACH TOKEN IS DRAWN, which this test cannot find out for
 * itself. When a screen starts drawing a text token on a new background, add the pair.
 *
 * THE RULES
 *   4.5 to 1  text
 *   3   to 1  the fainter text tokens (subtle, faint, hint), and borders and icons
 *             that carry a meaning
 *   Switched-off controls are not checked (--color-on-disabled): they are meant to
 *   look unavailable.
 *   A template may be held to more than this: see STRICTER.
 *
 * THREE STATES ARE ALSO FADED (a locked item, a finished ticket, an 86'd menu card): the
 * whole box is drawn partly see-through, so its text and its background both sink
 * toward the page behind. For those the test works out the colors as they END UP, not
 * as the tokens name them. A pair's fifth entry says which opacity token fades it and
 * what is behind.
 *
 * 8-BIT DOES NOT PASS ALL OF IT. It was drawn before this test existed, and the owner's
 * instruction is that 8-Bit is not changed by it. Every pair 8-Bit fails is written
 * down in KNOWN_EXCEPTIONS, by name, with what is drawn there. EVERY OTHER TEMPLATE
 * MUST PASS WITH NO EXCEPTIONS, and the last test in this file refuses an exception
 * for any template but 8-Bit.
 */

const TEXT = 4.5;
const FAINT = 3;
const MARK = 3;       // A border or icon that carries a meaning

// A box faded by an opacity token, over the page
const LOCKED = { fadedBy: '--status-locked-opacity', onto: '--color-bg' };
const TICKET_DONE = { fadedBy: '--status-ticket-done-opacity', onto: '--color-bg' };
const CARD_86 = { fadedBy: '--status-86-card-opacity', onto: '--color-bg' };

// [ foreground token, background token, minimum, what is drawn there, (how it is faded) ]
const PAIRS = [
  // ----- Ordinary text, on everything it is written on -----
  ['--color-text', '--color-bg', TEXT, 'ordinary text on the page'],
  ['--color-text', '--color-surface', TEXT, 'ordinary text on a card or panel'],
  ['--color-text', '--color-bg-deep', TEXT, 'ordinary text in a row, a text box, the top bar'],
  ['--color-text', '--color-control', TEXT, 'the words on a neutral button (SAVE DRAFT, CANCEL)'],
  ['--color-text', '--color-surface-hover', TEXT, 'the words on a card or plain button under the pointer'],
  ['--color-text', '--color-control-hover', TEXT, 'the words on a neutral button under the pointer'],
  ['--color-text-muted', '--color-bg', TEXT, 'explanations on the page (who is signed in, why a PIN must change)'],
  ['--color-text-muted', '--color-surface', TEXT, 'form labels and explanations on a panel'],
  ['--color-text-muted', '--color-bg-deep', TEXT, 'the description on a template card'],
  ['--color-text-muted', '--color-control', TEXT, 'muted text on a neutral button'],

  // ----- The fainter text -----
  ['--color-text-subtle', '--color-surface', FAINT, 'secondary labels on a panel (a stat\'s caption)'],
  ['--color-text-subtle', '--color-bg', FAINT, 'secondary labels on the page ("nothing is 86\'d")'],
  ['--color-text-faint', '--color-surface', FAINT, 'the time on a table card; seats on a table'],
  ['--color-text-faint', '--color-bg', FAINT, 'placeholders and "nothing here" messages'],
  ['--color-text-faint', '--color-bg-deep', FAINT, 'the time of a transaction'],
  ['--color-text-hint', '--color-bg', FAINT, 'the rule under a modifier group ("choose 1")'],

  // ----- Text on a filled background -----
  ['--color-on-accent', '--color-accent-fill', TEXT, 'a selected tab; a button under the pointer'],
  ['--color-on-accent-alt', '--color-accent-fill', TEXT, 'ACTIVE / CLOSED on Tables; a period button under the pointer'],
  ['--color-on-primary', '--color-primary-fill', TEXT, 'SEND, PROCESS PAYMENT, START, a selected choice'],
  ['--color-on-primary', '--color-primary-fill-hover', TEXT, 'the same, under the pointer'],
  ['--color-on-success', '--color-success-fill', TEXT, 'the tick when a payment goes through'],
  ['--color-on-danger', '--color-danger-fill', TEXT, 'CLOSE ORDER; the "items are locked" banner'],
  ['--color-on-danger', '--color-danger-fill-hover', TEXT, 'CLOSE ORDER under the pointer'],
  ['--color-on-danger-alt', '--color-danger-fill', TEXT, 'CANCEL under the pointer in the Payment window'],
  ['--color-on-neutral', '--color-neutral-fill', TEXT, 'SHOW DONE when it is switched on'],
  ['--tip-input-text', '--tip-input-surface', TEXT, 'what is typed into the custom tip box'],

  // ----- Headings, accents, and amounts, where they are written -----
  ['--color-heading', '--color-surface', TEXT, 'the title bar of a screen'],
  ['--color-heading', '--color-bg', TEXT, 'the title of a pop-up; ENTER YOUR PIN'],
  ['--color-subheading', '--color-surface', TEXT, 'section headings on a panel'],
  ['--color-subheading', '--color-bg', TEXT, 'section headings on the page'],
  ['--color-accent', '--color-surface', TEXT, 'the server\'s name on a table card'],
  ['--color-accent', '--color-bg', TEXT, 'labels in a pop-up'],
  ['--color-accent', '--color-bg-deep', TEXT, 'how a transaction was paid'],
  ['--color-accent-soft', '--color-surface', TEXT, 'the modifiers under an item on the order'],
  ['--color-accent-soft', '--color-bg-deep', TEXT, 'the modifiers under an item on a kitchen ticket'],
  ['--color-primary', '--color-surface', TEXT, 'amounts and names on a card'],
  ['--color-primary', '--color-bg', TEXT, 'the total of an order'],
  ['--color-primary', '--color-bg-deep', TEXT, 'an amount in a row'],
  ['--color-text', '--color-accent-surface', TEXT, 'the name and the numbers on your own row of the leaderboard'],
  ['--color-primary', '--color-accent-surface', TEXT, 'your own sales on the leaderboard'],
  ['--color-success', '--color-bg', TEXT, '"Payment processed successfully"'],
  ['--color-danger', '--color-bg', TEXT, 'CANCEL in a pop-up'],
  ['--color-danger', '--color-surface', TEXT, 'the x that removes an item'],
  ['--color-attention', '--color-surface', TEXT, 'how a check was paid, on its card'],
  ['--color-note', '--color-surface', TEXT, 'a kitchen note on the order'],
  ['--color-note', '--color-bg-deep', TEXT, 'a kitchen note on a ticket'],
  ['--color-note-alt', '--color-bg', TEXT, 'a kitchen note on a closed check'],

  // ----- Status: text that carries a meaning -----
  ['--status-draft-text', '--status-draft-surface', TEXT, 'a saved draft on the order'],
  ['--status-locked-text', '--status-locked-surface', TEXT, 'a locked item on the order', LOCKED],
  ['--status-item-label', '--color-bg-deep', TEXT, 'PENDING / FIRED / COMPLETED on a ticket'],
  ['--status-wait-ok', '--color-surface', TEXT, 'a ticket\'s wait: on time'],
  ['--status-wait-warning', '--color-surface', TEXT, 'a ticket\'s wait: the 10-minute warning'],
  ['--status-wait-late', '--color-surface', TEXT, 'a ticket\'s wait: late'],
  ['--status-ticket-done-text', '--color-surface', TEXT, 'DONE on a finished ticket', TICKET_DONE],
  ['--status-86-text', '--status-86-surface', TEXT, 'an 86\'d item on the 86 board'],
  ['--status-86-text', '--color-surface', TEXT, 'the "86\'d now" heading'],
  ['--status-86-card-text', '--color-surface', TEXT, 'the name of an 86\'d item on the order screen', CARD_86],
  ['--status-86-label', '--color-surface', TEXT, '86\'D on a menu card', CARD_86],
  ['--status-error-text', '--color-bg', TEXT, 'the message under the PIN dots'],
  ['--status-error-text', '--color-surface', TEXT, 'a refusal on the Staff screen'],
  ['--status-error-text', '--status-error-surface', TEXT, 'an error box (Floor Map, 86 board)'],
  ['--status-paused-text', '--color-bg-deep', TEXT, 'a paused terminal\'s address'],

  // ----- Status: borders and icons that carry a meaning -----
  ['--status-draft-border', '--color-bg', MARK, 'the border of a saved draft'],
  ['--status-locked-border', '--color-bg', MARK, 'the border of a locked item'],
  ['--status-locked-icon', '--status-locked-surface', MARK, 'the lock on a locked item', LOCKED],
  ['--status-limbo-border', '--color-bg', MARK, 'a table card with something in its edit window'],
  ['--status-in-kitchen-border', '--color-bg', MARK, 'a table card the kitchen is working on'],
  ['--status-all-done-border', '--color-bg', MARK, 'a table card with everything made'],
  ['--status-ticket-done-border', '--color-bg', MARK, 'a finished ticket'],
  ['--status-86-border', '--color-bg', MARK, 'an 86\'d item on the 86 board'],
  ['--status-86-card-border', '--color-bg', MARK, 'an 86\'d item on the order screen'],
  ['--status-error-border', '--color-bg', MARK, 'an error box'],
  ['--status-table-available-border', '--color-bg-deep', MARK, 'an available table on the floor'],
  ['--status-table-occupied-border', '--color-bg-deep', MARK, 'an occupied table on the floor'],
  ['--status-paused-border', '--color-surface', MARK, 'a paused terminal\'s row'],
  ['--color-border-primary', '--color-surface', MARK, 'the line around the search result the arrow keys or the pointer are on'],
  // The medals beside the first three ranks on the leaderboard. They are icons, so they
  // must be visible; they are NOT what tells first from third (the rank's number is
  // printed beside each), which is why they are not a group in distinct.test.js: gold
  // and bronze are near-twins in the light templates.
  ['--color-rank-gold', '--color-bg-deep', MARK, 'the medal beside rank 1'],
  ['--color-rank-silver', '--color-bg-deep', MARK, 'the medal beside rank 2'],
  ['--color-rank-bronze', '--color-bg-deep', MARK, 'the medal beside rank 3'],
  ['--color-rank-gold', '--color-accent-surface', MARK, 'the medal beside rank 1, on your own row'],
  ['--color-rank-silver', '--color-accent-surface', MARK, 'the medal beside rank 2, on your own row'],
  ['--color-rank-bronze', '--color-accent-surface', MARK, 'the medal beside rank 3, on your own row'],
];

// Templates held to more than the usual minimums. High Contrast exists to be the most
// readable the app can be, so its text must clear 7 to 1 (WCAG's stricter level).
const STRICTER = {
  'high-contrast': { [TEXT]: 7 }
};

// The pairs 8-Bit does not pass, each with what is drawn there. They are 8-Bit as it
// was drawn before this test existed, and it is not to be changed by the test (the
// owner's instruction). Each is worth a look when 8-Bit is next polished.
// Written as 'foreground on background'. No other template may appear here.
const KNOWN_EXCEPTIONS = {
  '8-bit': [
    // --- The purple (#b026ff) is too dark to be text on the dark blues: 3.4 to 4.1, needs 4.5 ---
    '--color-accent on --color-surface',              // 3.4  the server's name on a table card
    '--color-accent on --color-bg',                   // 3.7  labels in a pop-up
    '--color-accent on --color-bg-deep',              // 4.1  how a transaction was paid
    '--color-subheading on --color-surface',          // 3.4  section headings on a panel
    '--color-subheading on --color-bg',               // 3.7  section headings on the page
    // ...and dark text on a purple button is the same pair the other way round
    '--color-on-accent on --color-accent-fill',       // 3.7  a selected tab; a button under the pointer

    // --- White on the red (#ff3366): 3.5, needs 4.5 ---
    '--color-on-danger on --color-danger-fill',       // 3.5  CLOSE ORDER; the "items are locked" banner
    '--color-on-danger on --color-danger-fill-hover', // 3.9  CLOSE ORDER under the pointer

    // --- Near misses on a card (#16213e): 4.4, needs 4.5. The same colors pass on the page behind. ---
    '--color-danger on --color-surface',              // 4.4  the x that removes an item
    '--status-wait-late on --color-surface',          // 4.4  a ticket's wait: late
    '--status-86-text on --color-surface',            // 4.4  the "86'd now" heading
    '--status-86-label on --color-surface',           // 4.4  86'D on a menu card
    '--status-error-text on --color-surface',         // 4.4  a refusal on the Staff screen
    '--status-ticket-done-text on --color-surface',   // 4.4  DONE on a finished ticket (grey #888)
    '--status-86-card-text on --color-surface',       // 4.4  the name of an 86'd item on the order screen (grey #888)

    // --- A locked order line is drawn half faded, which halves what is left of its contrast ---
    '--status-locked-text on --status-locked-surface',  // 2.3  a locked item on the order (needs 4.5)
    '--status-locked-icon on --status-locked-surface',  // 2.1  the lock on a locked item (needs 3)
    // (The 86'd menu card and the finished ticket above are faded as well; their figures
    // above are before fading, and they are lower still on screen.)

    // --- The faintest grey (#666): 2.7 to 2.9, needs 3 ---
    '--color-text-faint on --color-surface',          // 2.7  the time on a table card; seats on a table
    '--color-text-faint on --color-bg',               // 2.9  placeholders and "nothing here" messages
    '--status-locked-border on --color-bg',           // 2.9  the border of a locked item
    '--status-ticket-done-border on --color-bg',      // 2.9  a finished ticket
    '--status-86-card-border on --color-bg',          // 2.9  an 86'd item on the order screen
  ]
};

const key = ([foreground, background]) => `${foreground} on ${background}`;
const oneDecimal = (ratio) => Math.floor(ratio * 10) / 10;         // Rounded DOWN: 4.49 is not "4.5"

// The contrast of text on its box when the whole box is faded over what is behind it:
// both colors move toward the color behind, and so toward each other
const contrastWhenFaded = (foregroundValue, backgroundValue, opacity, behindValue) => {
  const [front, back, behind] = [foregroundValue, backgroundValue, behindValue].map(parseColor);
  if (!front || !back || !behind || Number.isNaN(opacity)) return null;
  return contrastRatio(faded(front, opacity, behind), faded(back, opacity, behind));
};

// Every pair a template does not pass: ['--a on --b: 3.2 to 1, needs 4.5 (what is drawn there)']
const failuresOf = (file) => {
  const tokens = resolvedTokensOf(file);
  const stricter = STRICTER[templateNameOf(file)] || {};
  const failures = [];
  for (const pair of PAIRS) {
    const [foreground, background, usualMinimum, where, fade] = pair;
    const minimum = stricter[usualMinimum] || usualMinimum;
    const ratio = fade
      ? contrastWhenFaded(tokens[foreground], tokens[background], Number(tokens[fade.fadedBy]), tokens[fade.onto])
      : lowestContrast(tokens[foreground], tokens[background]);
    if (ratio === null) {
      failures.push({ key: key(pair), line: `${key(pair)}: one of the two is not a color (${tokens[foreground]} / ${tokens[background]})` });
    } else if (ratio < minimum) {
      failures.push({ key: key(pair), line: `${key(pair)}: ${oneDecimal(ratio)} to 1, needs ${minimum} (${where})` });
    }
  }
  return failures;
};

// ============================================================================
// The arithmetic
// ============================================================================

test('colors are read from the ways a stylesheet writes them', () => {
  expect(parseColor('#fff')).toEqual({ r: 255, g: 255, b: 255, a: 1 });
  expect(parseColor('#16213E')).toEqual({ r: 22, g: 33, b: 62, a: 1 });
  expect(parseColor('rgb(57, 255, 20)')).toEqual({ r: 57, g: 255, b: 20, a: 1 });
  expect(parseColor('rgba(0, 0, 0, 0.8)')).toEqual({ r: 0, g: 0, b: 0, a: 0.8 });
  expect(parseColor('#00000080').a).toBeCloseTo(0.5, 2);
  expect(parseColor('none')).toBeNull();
  expect(colorsIn('linear-gradient(90deg, #b026ff, #39ff14)')).toHaveLength(2);
});

test('the contrast ratio matches the published figures', () => {
  const black = parseColor('#000'), white = parseColor('#fff');

  expect(contrastRatio(black, white)).toBeCloseTo(21, 5);         // The most there can be
  expect(contrastRatio(white, black)).toBeCloseTo(21, 5);         // Whichever way round
  expect(contrastRatio(white, white)).toBeCloseTo(1, 5);          // The least: the same color
  // #767676 on white is the well-known grey that only just passes 4.5 to 1
  expect(contrastRatio(parseColor('#767676'), white)).toBeGreaterThanOrEqual(4.5);
  expect(contrastRatio(parseColor('#777777'), white)).toBeLessThan(4.5);
});

test('fading a box brings its text and its background closer together', () => {
  const white = parseColor('#fff'), black = parseColor('#000');

  // Black text on a white box, the box half faded over a black page: the box ends up
  // mid-grey, the text stays black
  expect(contrastRatio(faded(black, 0.5, black), faded(white, 0.5, black))).toBeLessThan(6);
  // Not faded at all: nothing changes
  expect(contrastRatio(faded(black, 1, black), faded(white, 1, black))).toBeCloseTo(21, 5);
});

test('on a gradient, the worst of its colors is the one that counts', () => {
  // Dark text on a gradient from white to black is unreadable at the black end
  expect(lowestContrast('#000', 'linear-gradient(90deg, #fff, #000)')).toBeCloseTo(1, 5);
  expect(lowestContrast('#000', '#fff')).toBeCloseTo(21, 5);
  expect(lowestContrast('#000', 'none')).toBeNull();
});

// ============================================================================
// The templates
// ============================================================================

test('every pair names tokens that exist', () => {
  // A misspelled token here would make a pair "pass" by never being checked
  const tokens = resolvedTokensOf(templateFiles[0]);
  const unknown = PAIRS.flatMap(([foreground, background]) => [foreground, background]).filter(name => !(name in tokens));

  expect([...new Set(unknown)]).toEqual([]);
});

test.each(templateFiles.map(file => [templateNameOf(file), file]))(
  'the %s template can be read everywhere, apart from its known exceptions', (name, file) => {
    const allowed = KNOWN_EXCEPTIONS[name] || [];
    const failures = failuresOf(file);

    // Anything that fails and is not written down
    expect(failures.filter(failure => !allowed.includes(failure.key)).map(failure => failure.line)).toEqual([]);
    // Anything written down that no longer fails: take it off the list
    expect(allowed.filter(exception => !failures.some(failure => failure.key === exception))).toEqual([]);
  }
);

test('only 8-Bit has exceptions', () => {
  // 8-Bit is the look the app had before this test. Every template made since must pass outright.
  expect(Object.keys(KNOWN_EXCEPTIONS)).toEqual(['8-bit']);
});
