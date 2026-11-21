import ReactDOM from 'react-dom/client';
// The templates come first. A template gives a name ("token") to every color, font, and
// size, and every other stylesheet in the app reads its look from those names. Importing
// the folder loads every template's stylesheet and the fonts (see templates/index.js).
import './templates';
import './index.css';
import App from './App';
import reportWebVitals from './reportWebVitals';

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
    <App />
);

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
