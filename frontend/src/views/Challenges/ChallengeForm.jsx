import { useId, useState } from 'react';
import { MEASURES, countableFrom, emptyForm, formFrom, requestFrom } from './challengeRules';

/**
 * The form a manager fills in to make a challenge, or to change one.
 *
 * WHAT IT ASKS: a name, an optional line about it, one of four measures, a target, a
 * start and an end, and an optional prize in the manager's own words. Two measures ask
 * one thing more: items sold asks WHICH items (a menu category, or one item), and a
 * check average asks how many checks a person needs before their average counts.
 *
 * The categories and items on offer are read from the menu it is handed. None is
 * written in this file.
 *
 * It holds what has been typed and nothing else. SAVE hands the screen a request in
 * the backend's shape (see requestFrom); the screen sends it and hands back the
 * backend's words if it was refused, which are shown as they are. The backend checks
 * everything again, so this form does not try to.
 *
 * @param {Object|null} challenge - The challenge being changed, or null for a new one
 * @param {Array} menuItems - The menu, for the "which items count" list
 * @param {function} onSave - Called with the request; resolves to { ok, message }
 * @param {function} onCancel - Called when the form is closed without saving
 */
function ChallengeForm({ challenge = null, menuItems = [], onSave, onCancel }) {
  const [form, setForm] = useState(() => (challenge ? formFrom(challenge) : emptyForm()));
  const [refusal, setRefusal] = useState('');
  const [saving, setSaving] = useState(false);
  const titleId = useId();

  // Changing a box clears the last refusal, so an old message never sits under new input
  const set = (field) => (event) => {
    setForm({ ...form, [field]: event.target.value });
    setRefusal('');
  };

  const measure = MEASURES.find(candidate => candidate.value === form.measure);
  const { categories, items } = countableFrom(menuItems);
  // A challenge on an item that has since left the menu still has to show what it counts
  const itemNoLongerListed = challenge && challenge.menuItemId && !items.some(item => item.menuItemId === challenge.menuItemId);

  const save = async (event) => {
    event.preventDefault();           // A form's own "submit" would reload the page
    setSaving(true);
    const answer = await onSave(requestFrom(form));
    setSaving(false);
    if (!answer.ok) setRefusal(answer.message);
  };

  return (
    <div className="challenge-form-overlay">
      <form className="challenge-form" role="dialog" aria-modal="true" aria-labelledby={titleId} onSubmit={save}>
        <h3 id={titleId} className="section-title">{challenge ? 'Change challenge' : 'New challenge'}</h3>

        <label>
          Name
          <input type="text" value={form.name} onChange={set('name')} maxLength={60} required autoFocus />
        </label>

        <label>
          A line about it (optional)
          <input type="text" value={form.description} onChange={set('description')} maxLength={300} />
        </label>

        <div className="challenge-form-row">
          <label>
            What it measures
            <select value={form.measure} onChange={set('measure')}>
              {MEASURES.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
          </label>

          <label>
            {measure.targetLabel}
            <input
              type="number"
              value={form.target}
              onChange={set('target')}
              min={measure.counts === 'items' ? 1 : 0.01}
              step={measure.counts === 'items' ? 1 : 0.01}
              required
            />
          </label>
        </div>

        {form.measure === 'items_sold' && (
          <label>
            Which items count
            <select value={form.counts} onChange={set('counts')}>
              <option value="">Pick a category or an item</option>
              <optgroup label="Everything in a category">
                {categories.map(category => <option key={category} value={`category:${category}`}>{category}</option>)}
              </optgroup>
              <optgroup label="One item">
                {items.map(item => <option key={item.menuItemId} value={`item:${item.menuItemId}`}>{item.name}</option>)}
                {itemNoLongerListed && (
                  <option value={`item:${challenge.menuItemId}`}>{challenge.menuItemName || 'An item no longer on the menu'}</option>
                )}
              </optgroup>
            </select>
          </label>
        )}

        {form.measure === 'check_average' && (
          <label>
            Checks needed before an average counts
            <input type="number" value={form.minChecks} onChange={set('minChecks')} min={1} step={1} required />
          </label>
        )}

        <div className="challenge-form-row">
          <label>
            Starts
            <input type="datetime-local" value={form.startsAt} onChange={set('startsAt')} required />
          </label>
          <label>
            Ends
            <input type="datetime-local" value={form.endsAt} onChange={set('endsAt')} required />
          </label>
        </div>

        <label>
          Prize, in your own words (optional)
          <input type="text" value={form.prize} onChange={set('prize')} maxLength={100} />
        </label>

        {refusal && <p className="challenge-form-refusal" role="alert">{refusal}</p>}

        <div className="challenge-form-buttons">
          <button type="button" className="challenge-btn secondary" onClick={onCancel}>CANCEL</button>
          <button type="submit" className="challenge-btn" disabled={saving}>{challenge ? 'SAVE CHANGES' : 'CREATE CHALLENGE'}</button>
        </div>
      </form>
    </div>
  );
}

export default ChallengeForm;
