import { render, screen } from '@testing-library/react';
import App from './App';

// Smoke test: the app should boot to the Home screen and show its landing buttons.
// (Replaces the default Create React App test, which looked for "learn react"
// text that was removed when the Home view was built.)
test('renders the home screen with its landing buttons', () => {
  render(<App />);

  // The logo proves the Home view mounted
  expect(screen.getByAltText(/NOTTA-POS Logo/i)).toBeInTheDocument();

  // A couple of landing buttons prove navigation options rendered
  expect(screen.getByRole('button', { name: /FLOOR MAP/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /CREATE ORDER/i })).toBeInTheDocument();
});
