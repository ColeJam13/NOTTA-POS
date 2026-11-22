import './WideScreenNote.css';

/**
 * A line that is only drawn on a phone, on a screen that is laid out for a tablet or
 * wider, to say so.
 *
 * WHY: The screens staff use all shift (sign-in, tables, the order screen, payment, the
 *      kitchen display) have a layout of their own for a phone. A few that a manager
 *      uses at a desk do not: rearranging the floor, Financials, Staff. They still open
 *      on a phone, and this says plainly what to expect there in place of leaving the
 *      person to wonder whether the screen is broken.
 *
 * It is always in the page and hidden by its stylesheet on anything wider than a phone
 * (see App.css for what "a phone" means), so no code has to know the width of the window.
 *
 * @param {ReactNode} children - What to say on this screen
 */
function WideScreenNote({ children }) {
  return <p className="wide-screen-note">{children}</p>;
}

export default WideScreenNote;
