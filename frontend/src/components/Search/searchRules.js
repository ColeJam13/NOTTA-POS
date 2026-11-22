/**
 * The small rules of the search box that both the box and its tests need.
 *
 * The backend applies the same "how much must be typed" rule (SearchService), so a
 * request for too little would come back empty anyway; checking here saves the request.
 */

// How long to wait after the last key before asking the backend, in milliseconds.
// This is called DEBOUNCING: a timer restarts on every key, and the request goes only
// when this long passes with no key. Without it, typing "table 12" sends eight
// requests, seven of them for something the person was never looking for.
export const SEARCH_DELAY_MS = 250;

// The most the box accepts, the same as the backend searches for (SearchService.MAX_QUERY_LENGTH)
export const MAX_QUERY_LENGTH = 50;

// How far back to look for paid checks. "value" is what the request carries
// (GET /api/search?closed=7days); each means "from the start of that day until now".
export const SEARCH_PERIODS = [
  { value: 'today', label: 'TODAY' },
  { value: 'yesterday', label: 'SINCE YESTERDAY' },
  { value: '7days', label: '7 DAYS' },
  { value: '30days', label: '30 DAYS' }
];

/**
 * Is this enough to search for? Digits search from the first one ("7" is table 7);
 * anything else from the second character, because one letter matches half the menu.
 *
 * @param {string} text - What is in the box
 */
export const longEnough = (text) => {
  const query = text.trim();
  if (query === '') return false;
  return /^\d+$/.test(query) || query.length >= 2;
};
