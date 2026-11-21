import { createContext, useContext } from 'react';

/**
 * Who is signed in, made available to every screen.
 *
 * WHAT: A React "context" - a value that any component can read without it
 *       being handed down through every component in between.
 * WHY:  The nav bar, the Home screen, the Floor Map, and the 86 board all
 *       need to know who is signed in. Passing it through every screen as a
 *       prop would touch a dozen files for one value.
 *
 * App.jsx provides the real value. The default below is what a component gets
 * when it is drawn on its own (in a test): nobody signed in, nothing restricted.
 *
 *   staff:   { staffId, name, role, mustChangePin } or null
 *            (mustChangePin is always false by the time a screen reads it: App.jsx
 *            shows only the choose-a-new-PIN screen until it is)
 *   signOut: call it to sign the current person out
 */
export const StaffContext = createContext({ staff: null, signOut: () => {} });

// Read who is signed in:  const { staff, signOut } = useStaff();
export const useStaff = () => useContext(StaffContext);
