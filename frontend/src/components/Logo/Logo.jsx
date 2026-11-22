import picture from '../../assets/NOTTA LOGO.png';
import { useTemplates, templateOnScreen } from '../../templates';
import './Logo.css';

/**
 * The NOTTA-POS logo at the top of the Home, sign-in, and choose-a-PIN screens.
 *
 * WHAT: The template on screen decides which of two things this is (its "logo" entry
 *       in templates/index.js):
 *         'picture'  the artwork, as an image
 *         'text'     the name NOTTA-POS, set in the template's logo font (--font-logo) and
 *                    accent color
 * WHY:  The artwork is one picture, drawn in the 8-Bit colors: neon green and purple
 *       on black. A template cannot recolor a picture, and that picture on a cream
 *       Coffee Shop page would look like a mistake. So it belongs to 8-Bit, and every
 *       other template writes the name in its own type (the owner's decision D13).
 *       A template that gets artwork of its own later only needs its entry changed;
 *       the three screens ask for "the logo" and do not care which kind they get.
 *
 * @param {string} pictureClassName - The class the screen gives the picture (its size
 *        on that screen). The text does not use it.
 * @param {'large'|'medium'|'small'} size - How big the text is: large on Home, medium
 *        on sign-in, small on the choose-a-PIN screen
 */
function Logo({ pictureClassName, size = 'medium' }) {
  const template = templateOnScreen(useTemplates());

  if (template.logo === 'picture') {
    return <img src={picture} alt="NOTTA-POS Logo" className={pictureClassName} />;
  }

  // role="img" with a name: to a screen reader (and to the tests) this is the logo,
  // the same as the picture is, and not a line of text to be read out
  return (
    <div className={`logo-text logo-text-${size}`} role="img" aria-label="NOTTA-POS Logo">
      {/* Written in mixed case, drawn in capitals by every template but one: the
          template's --logo-case decides (see Logo.css) */}
      Notta-Pos
    </div>
  );
}

export default Logo;
