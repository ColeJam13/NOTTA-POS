import { all, screens, read, withoutComments, declarationsIn } from './templates/testSupport';

/**
 * Keeps the phone layout honest (step P3 of the plan).
 *
 * WHAT: Reads every stylesheet as text and checks the rules about phones that can be
 *       checked without drawing anything:
 *         1. "A phone" is defined ONE way: @media (max-width: 700px). No stylesheet
 *            may use another width, or the screens would switch layout at different
 *            widths and some window size would get half of each.
 *         2. The screens staff use on a phone each have phone rules.
 *         3. Nothing is shown only when the pointer is over something: a finger
 *            cannot hover.
 *         4. A rule for a phone never takes a thing to tap below 44 pixels.
 * WHY:  A test browser (jsdom) applies no layout, so whether a screen FITS a phone
 *       cannot be tested here; that was checked in a real browser at a phone's size
 *       and is in the pull request. These are the rules that keep it that way when the
 *       next stylesheet is written.
 *
 * What "a phone" means, and why the number is written out in every stylesheet (a
 * media query cannot read a token), is at the top of the phone rules in App.css.
 */

const PHONE = '@media (max-width: 700px)';

// Every "@media ..." condition a stylesheet uses, e.g. "@media (max-width: 700px)"
const mediaQueriesIn = (file) => [...withoutComments(read(file)).matchAll(/@media[^{]+/g)].map(match => match[0].trim());

test('a phone is defined one way: every width a stylesheet asks about is "up to 700 pixels"', () => {
  const others = [];
  for (const file of all) {
    for (const query of mediaQueriesIn(file)) {
      // The one allowed form, alone or with a height as well (the PIN pad on a short phone)
      if (query === PHONE || query === `${PHONE} and (max-height: 700px)`) continue;
      others.push(`${file}: ${query}`);
    }
  }
  expect(others).toEqual([]);
});

test('the screens staff use on a phone each have rules for one', () => {
  // The plan's list: sign-in, tables, the order screen, the modifier picker, payment, and
  // Active Orders. Plus what every one of them is drawn inside or beside.
  const needed = [
    'App.css',
    'components/NavBar/NavBar.css',
    'components/Search/Search.css',
    'components/Notifications/Notifications.css',
    'components/PinPad/PinPad.css',
    'components/PaymentModal/PaymentModal.css',
    'views/Home/Home.css',
    'views/Tables/Tables.css',
    'views/QuickOrders/QuickOrders.css',
    'views/CreateOrder/CreateOrder.css',
    'views/CreateOrder/ModifierPicker.css',
    'views/ActiveOrders/ActiveOrders.css',
    'views/EightySixBoard/EightySixBoard.css',
    'views/Challenges/Challenges.css'
  ];
  const sheets = all.map(file => file.replaceAll('\\', '/'));

  for (const file of needed) {
    expect(sheets, file).toContain(file);
    expect(mediaQueriesIn(file).some(query => query.startsWith(PHONE)), `${file} has no phone rules`).toBe(true);
  }
});

test('nothing is shown or hidden by hovering', () => {
  // A rule that only applies under the pointer may change how a thing looks. It must
  // not be what makes a thing appear: on a touch screen there is no pointer to rest.
  const reveals = [];
  for (const file of screens) {
    for (const { selector, property, value } of declarationsIn(read(file))) {
      if (!selector.includes(':hover')) continue;
      if (property === 'display' || property === 'visibility' || (property === 'opacity' && Number(value) > 0.9)) {
        reveals.push(`${file}  ${selector} { ${property}: ${value} }`);
      }
    }
  }
  expect(reveals).toEqual([]);
});

test('no phone rule makes a thing to tap smaller than 44 pixels', () => {
  // The phone rules set min-height (and sometimes min-width) on buttons and boxes. This
  // catches a later edit that lowers one. (44 is the size both phone makers ask for.)
  const tooSmall = [];
  for (const file of screens) {
    for (const { selector, property, value } of declarationsIn(read(file))) {
      if (!selector.startsWith(PHONE)) continue;
      if (property !== 'min-height' && property !== 'min-width') continue;
      const pixels = Number((value.match(/^(\d+)px$/) || [])[1]);
      // "0" undoes a minimum that was there for the wide layout: that is not a size to tap
      if (value === '0' || Number.isNaN(pixels)) continue;
      if (pixels < 44) tooSmall.push(`${file}  ${selector} { ${property}: ${value} }`);
    }
  }
  expect(tooSmall).toEqual([]);
});

test('the checks above do read the phone rules (the test tests itself)', () => {
  const navBar = declarationsIn(read(all.find(file => file.replaceAll('\\', '/') === 'components/NavBar/NavBar.css')));
  const phoneRules = navBar.filter(rule => rule.selector.startsWith(PHONE));

  expect(phoneRules.length).toBeGreaterThan(10);
  // The row of screen buttons goes to the bottom of the phone
  expect(phoneRules).toContainEqual({ selector: `${PHONE} .navbar-bottom`, property: 'order', value: '99' });
  expect(phoneRules).toContainEqual({ selector: `${PHONE} .navbar-bottom button`, property: 'min-height', value: '44px' });
});
