import { useState, useEffect } from 'react';
import { formatUpcharge } from '../../utils/formatters';
import { priceWithModifiers } from '../../utils/totals';
import './ModifierPicker.css';

// Longest kitchen note the backend accepts (the special_instructions column holds 500 characters)
const MAX_NOTE_LENGTH = 500;

// The short rule shown beside each group name, e.g. "REQUIRED - CHOOSE 1" or "OPTIONAL - UP TO 3"
const ruleLabel = (group) => {
  // A group can never need more choices than it has
  const limit = Math.min(group.maxSelections, group.modifiers.length);
  const need = group.isRequired ? 'REQUIRED' : 'OPTIONAL';
  return limit === 1 ? `${need} - CHOOSE 1` : `${need} - UP TO ${limit}`;
};

/**
 * The pop-up where a server picks an item's modifiers and types a kitchen note.
 *
 * WHAT: Shows each modifier group for one menu item as a row of big buttons,
 *       plus a free-text note. Used both to ADD an item and to EDIT one that
 *       is still in draft or in the edit window.
 * RULES IT ENFORCES (the backend checks the same rules again when saving):
 *   - A REQUIRED group must have a choice before the confirm button unlocks.
 *   - A group never takes more choices than its maxSelections. In a
 *     "choose 1" group, tapping another choice swaps to it. In an "up to N"
 *     group, the rest go grey once N are picked.
 *   - OPTIONAL groups can be skipped.
 *
 * An item with no modifier groups can still open this to get a note.
 *
 * @param {Object} menuItem - The item being customized: { name, price } (price = menu price before upcharges)
 * @param {Array} groups - The item's modifier groups, each with a "modifiers" array of choices
 * @param {Array} [initialModifiers] - Modifiers already on the item (when editing)
 * @param {string} [initialNote] - Kitchen note already on the item (when editing)
 * @param {string} confirmLabel - Text for the confirm button ("ADD TO ORDER" or "SAVE CHANGES")
 * @param {Function} onConfirm - Called with { modifiers, specialInstructions } when confirmed
 * @param {Function} onCancel - Called when the picker is closed without confirming
 */
function ModifierPicker({
  menuItem,
  groups,
  initialModifiers = [],
  initialNote = '',
  confirmLabel,
  onConfirm,
  onCancel
}) {
  // IDs of the choices currently picked, across all groups
  const [selectedIds, setSelectedIds] = useState(() => initialModifiers.map(modifier => modifier.modifierId));
  const [note, setNote] = useState(initialNote);

  // Escape closes the picker without saving (handy when a keyboard is attached)
  useEffect(() => {
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') onCancel();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onCancel]);

  const isSelected = (option) => selectedIds.includes(option.modifierId);
  const selectedInGroup = (group) => group.modifiers.filter(isSelected);

  // A group is "full" once it holds as many choices as it allows
  const isFull = (group) => selectedInGroup(group).length >= group.maxSelections;

  const toggleOption = (group, option) => {
    setSelectedIds(current => {
      // Tapping a picked choice un-picks it
      if (current.includes(option.modifierId)) {
        return current.filter(id => id !== option.modifierId);
      }

      const idsInGroup = group.modifiers.map(modifier => modifier.modifierId);

      // "Choose 1" group: swap straight to the new choice (no need to un-pick the old one first)
      if (group.maxSelections === 1) {
        return [...current.filter(id => !idsInGroup.includes(id)), option.modifierId];
      }

      // "Up to N" group: ignore the tap once N are already picked
      const pickedInGroup = current.filter(id => idsInGroup.includes(id)).length;
      if (pickedInGroup >= group.maxSelections) return current;

      return [...current, option.modifierId];
    });
  };

  // The picked choices, always in the order the picker shows them (so tickets read the same every time)
  const chosenModifiers = groups.flatMap(group => selectedInGroup(group));

  // Required groups that still have no choice - the confirm button stays locked until this is empty
  const unansweredGroups = groups.filter(group => group.isRequired && selectedInGroup(group).length === 0);
  const canConfirm = unansweredGroups.length === 0;

  // Live price: menu price plus the upcharges picked so far
  const price = priceWithModifiers(menuItem.price, chosenModifiers);

  const handleConfirm = () => {
    if (!canConfirm) return;
    onConfirm({
      modifiers: chosenModifiers,
      specialInstructions: note.trim()
    });
  };

  return (
    <div className="modifier-picker-overlay" onClick={onCancel}>
      <div
        className="modifier-picker"
        role="dialog"
        aria-modal="true"
        aria-label={`Customize ${menuItem.name}`}
        onClick={(e) => e.stopPropagation()}                  // A tap inside the box must not count as "tapped outside to close"
      >
        <div className="modifier-picker-header">
          <h2>{menuItem.name}</h2>
          <button type="button" className="modifier-picker-close" aria-label="Close" onClick={onCancel}>✕</button>
        </div>

        <div className="modifier-picker-body">
          {groups.map(group => (
            <div key={group.modifierGroupId} className="modifier-group" role="group" aria-label={group.name}>
              <div className="modifier-group-heading">
                <h3>{group.name}</h3>
                <span className={`modifier-group-rule ${
                  group.isRequired && selectedInGroup(group).length === 0 ? 'unanswered' : ''
                }`}>
                  {ruleLabel(group)}
                </span>
              </div>

              <div className="modifier-options">
                {group.modifiers.map(option => (
                  <button
                    key={option.modifierId}
                    type="button"
                    className={`modifier-option ${isSelected(option) ? 'selected' : ''}`}
                    aria-pressed={isSelected(option)}             // Tells screen readers (and tests) whether this choice is picked
                    // "Up to N" group that is full: grey out what is left. ("Choose 1" groups swap instead.)
                    disabled={!isSelected(option) && group.maxSelections > 1 && isFull(group)}
                    onClick={() => toggleOption(group, option)}
                  >
                    <span className="modifier-option-name">{option.name}</span>
                    {option.priceAdjustment !== 0 && (
                      <span className="modifier-option-price">{formatUpcharge(option.priceAdjustment)}</span>
                    )}
                  </button>
                ))}
              </div>
            </div>
          ))}

          <div className="modifier-note">
            <label htmlFor="kitchen-note">KITCHEN NOTE</label>
            <textarea
              id="kitchen-note"
              value={note}
              maxLength={MAX_NOTE_LENGTH}
              placeholder="Allergy, no onions, sauce on the side..."
              onChange={(e) => setNote(e.target.value)}
            />
          </div>
        </div>

        <div className="modifier-picker-footer">
          <div className="modifier-picker-summary">
            <span className="modifier-picker-price">${price.toFixed(2)}</span>
            {!canConfirm && (
              <span className="modifier-picker-missing">
                Still needed: {unansweredGroups.map(group => group.name).join(', ')}
              </span>
            )}
          </div>
          <div className="modifier-picker-actions">
            <button type="button" className="modifier-picker-cancel" onClick={onCancel}>CANCEL</button>
            <button type="button" className="modifier-picker-confirm" disabled={!canConfirm} onClick={handleConfirm}>
              {confirmLabel}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default ModifierPicker;
