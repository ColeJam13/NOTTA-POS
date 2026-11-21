/**
 * How readable is one color on another? The arithmetic behind contrast.test.js.
 *
 * CONTRAST RATIO is the standard measure (from the Web Content Accessibility
 * Guidelines, WCAG). It compares how bright two colors are, from 1 to 1 (the same
 * color: invisible) to 21 to 1 (black on white). The accepted minimums:
 *
 *     4.5 to 1   ordinary text
 *     3   to 1   large or fainter text, and borders and icons that carry a meaning
 *     7   to 1   the stricter level, used here for the High Contrast template
 *
 * Plain functions, no React and no files, so they are easy to test and could be used
 * by a screen one day (the template gallery, say).
 */

// '#fff', '#16213e', '#16213e80', 'rgb(1, 2, 3)', 'rgba(0, 0, 0, 0.8)' -> { r, g, b, a }
// with r, g, b from 0 to 255 and a (how solid) from 0 to 1. Anything else -> null.
export const parseColor = (text) => {
  const value = text.trim().toLowerCase();

  const hex = value.match(/^#([0-9a-f]{3,8})$/);
  if (hex) {
    let digits = hex[1];
    if (digits.length === 3 || digits.length === 4) digits = [...digits].map(d => d + d).join('');     // #abc is short for #aabbcc
    if (digits.length !== 6 && digits.length !== 8) return null;
    const part = (start) => parseInt(digits.slice(start, start + 2), 16);
    return { r: part(0), g: part(2), b: part(4), a: digits.length === 8 ? part(6) / 255 : 1 };
  }

  const rgb = value.match(/^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)$/);
  if (rgb) return { r: Number(rgb[1]), g: Number(rgb[2]), b: Number(rgb[3]), a: rgb[4] === undefined ? 1 : Number(rgb[4]) };

  if (value === 'white') return { r: 255, g: 255, b: 255, a: 1 };
  if (value === 'black') return { r: 0, g: 0, b: 0, a: 1 };
  return null;
};

// Every color written inside a value. A plain color gives one; a gradient
// ("linear-gradient(90deg, #b026ff, #39ff14)") gives each of its colors.
export const colorsIn = (value) =>
  (value.match(/#[0-9a-fA-F]{3,8}\b|rgba?\([^)]*\)/g) || []).map(parseColor).filter(Boolean);

// A see-through color as it looks over a solid one
export const over = (top, behind) => ({
  r: top.r * top.a + behind.r * (1 - top.a),
  g: top.g * top.a + behind.g * (1 - top.a),
  b: top.b * top.a + behind.b * (1 - top.a),
  a: 1
});

// How bright a color looks, from 0 (black) to 1 (white). The eye is most sensitive to
// green and least to blue, which is where the three weights come from (WCAG's formula).
export const luminance = ({ r, g, b }) => {
  const channel = (value) => {
    const share = value / 255;
    return share <= 0.03928 ? share / 12.92 : ((share + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
};

/**
 * The contrast ratio of two colors, as one number: 4.5 means "4.5 to 1".
 * A see-through foreground is first laid over the background.
 */
export const contrastRatio = (foreground, background) => {
  const front = foreground.a < 1 ? over(foreground, background) : foreground;
  const lighter = Math.max(luminance(front), luminance(background));
  const darker = Math.min(luminance(front), luminance(background));
  return (lighter + 0.05) / (darker + 0.05);
};

/**
 * The LOWEST contrast between a foreground value and a background value, either of
 * which may be a gradient (then every one of its colors has to work).
 *
 * @returns {number|null} - The ratio, or null if either value holds no color (e.g. "none")
 */
export const lowestContrast = (foregroundValue, backgroundValue) => {
  const fronts = colorsIn(foregroundValue);
  const backs = colorsIn(backgroundValue);
  if (fronts.length === 0 || backs.length === 0) return null;
  return Math.min(...fronts.flatMap(front => backs.map(back => contrastRatio(front, back))));
};
