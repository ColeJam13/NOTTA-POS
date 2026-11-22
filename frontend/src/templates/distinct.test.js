import { parseColor, colorDifference } from './contrast';
import { templateFiles, templateNameOf, resolvedTokensOf } from './testSupport';

/**
 * Can the states be told apart, in every template?
 *
 * WHAT: For a few groups of tokens that are drawn SIDE BY SIDE and mean DIFFERENT
 *       things, checks that no two colors in a group are close enough to confuse.
 * WHY:  contrast.test.js asks "can this be read?". It cannot ask "can this be told
 *       from that?": a dark orange and a brick red both read perfectly on cream, and a
 *       cook glancing at a ticket could not say which of them meant "10 minutes" and
 *       which meant "late". (That pair was in the first draft of the Coffee Shop
 *       template and is why this test exists.)
 *
 * THE GROUPS are the places where color is the main thing that tells states apart:
 *   - a table's card on Tables and Quick Orders shows what its items are doing by the
 *     color of its border and nothing else;
 *   - a ticket's waiting time turns from calm to a warning to late by color alone;
 *   - a table on the Floor Map is available or occupied;
 *   - a saved draft on the order has a border of its own.
 * States that also carry a word or an icon (the lock, 86'D, DONE) are not listed:
 * there the color is not the only signal. The leaderboard's gold, silver, and bronze
 * medals are not listed for the same reason: each has its rank's number beside it.
 *
 * THE MINIMUM is a difference of 30 (see colorDifference in contrast.js for what the
 * number means). It is a floor that catches near-twins, not a judgment of taste.
 *
 * 8-BIT has one exception, written down below: it was drawn before this test, and it
 * is not to be changed by a test (the owner's instruction). No other template may.
 */

const MINIMUM = 30;

// [ what is drawn there, the tokens that must all look different from each other ]
const GROUPS = [
  ['the border of a table\'s card: nothing sent, in the edit window, in the kitchen, all made',
    ['--color-border', '--status-limbo-border', '--status-in-kitchen-border', '--status-all-done-border']],
  ['a ticket\'s waiting time: on time, the 10-minute warning, late',
    ['--status-wait-ok', '--status-wait-warning', '--status-wait-late']],
  ['a table on the Floor Map: available, occupied',
    ['--status-table-available-border', '--status-table-occupied-border']],
  ['an order line: ordinary, a saved draft',
    ['--color-border', '--status-draft-border']],
];

// Written as 'one token = other token'. In 8-Bit a table's card with an item still in
// its edit window has the same purple border as a card with nothing sent.
const KNOWN_EXCEPTIONS = {
  '8-bit': ['--color-border = --status-limbo-border']
};

// Every pair in a group that is closer than the minimum
const twinsOf = (file) => {
  const tokens = resolvedTokensOf(file);
  const twins = [];
  for (const [where, group] of GROUPS) {
    group.forEach((one, index) => group.slice(index + 1).forEach(other => {
      const [a, b] = [parseColor(tokens[one]), parseColor(tokens[other])];
      const key = `${one} = ${other}`;
      if (!a || !b) twins.push({ key, line: `${key}: one of the two is not a plain color (${tokens[one]} / ${tokens[other]})` });
      else if (colorDifference(a, b) < MINIMUM) {
        twins.push({ key, line: `${key}: a difference of ${Math.round(colorDifference(a, b))}, needs ${MINIMUM} (${where})` });
      }
    }));
  }
  return twins;
};

test('the difference between two colors matches what an eye would say', () => {
  const color = (text) => parseColor(text);

  expect(colorDifference(color('#b23333'), color('#b23333'))).toBe(0);              // The same color
  expect(colorDifference(color('#000'), color('#fff'))).toBeCloseTo(100, 0);        // As far apart as light and dark go
  expect(colorDifference(color('#ff0000'), color('#00ff00'))).toBeGreaterThan(100); // Red and green: further still
  // Two shades of one brown, then that brown against a blue
  expect(colorDifference(color('#8b5e3c'), color('#7b4a2e'))).toBeLessThan(MINIMUM);
  expect(colorDifference(color('#8b5e3c'), color('#2f5bea'))).toBeGreaterThan(MINIMUM);
  // The pair this test was written for: a burnt orange and a brick red
  expect(colorDifference(color('#955000'), color('#a33a27'))).toBeLessThan(MINIMUM);
});

test('every group names tokens that exist', () => {
  // A misspelled token here would make a group "pass" by never being compared
  const tokens = resolvedTokensOf(templateFiles[0]);
  const unknown = GROUPS.flatMap(([, group]) => group).filter(name => !(name in tokens));

  expect([...new Set(unknown)]).toEqual([]);
});

test.each(templateFiles.map(file => [templateNameOf(file), file]))(
  'in the %s template, states drawn side by side cannot be mistaken for each other', (name, file) => {
    const allowed = KNOWN_EXCEPTIONS[name] || [];
    const twins = twinsOf(file);

    // Anything too alike that is not written down
    expect(twins.filter(twin => !allowed.includes(twin.key)).map(twin => twin.line)).toEqual([]);
    // Anything written down that is no longer alike: take it off the list
    expect(allowed.filter(exception => !twins.some(twin => twin.key === exception))).toEqual([]);
  }
);

test('only 8-Bit has exceptions', () => {
  expect(Object.keys(KNOWN_EXCEPTIONS)).toEqual(['8-bit']);
});
