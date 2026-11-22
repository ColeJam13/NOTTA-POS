import ReactDOM from 'react-dom/client';
// The templates come first. A template gives a name ("token") to every color, font, and
// size, and every other stylesheet in the app reads its look from those names. Importing
// the folder loads every template's stylesheet and the fonts (see templates/index.js).
import './templates';
import './index.css';
import App from './App';

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
    <App />
);

