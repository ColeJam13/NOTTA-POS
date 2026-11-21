import { useState } from 'react';
import { apiFetch } from '../../services/api';
import { TEMPLATES, useTemplates, showChosenTemplate, stopPreview } from '../../templates';
import './TemplatePreviewBar.css';

/**
 * The bar across the top of every screen while a manager is trying a template out.
 *
 * WHAT: Says which template is being previewed and that only this terminal shows it,
 *       and offers the two ways out: use it for the whole restaurant, or stop.
 * WHY:  A template has to be judged on the screens people work in (the order screen,
 *       the kitchen display, the Payment window), not on a small sample. So a preview
 *       stays on while the manager moves from screen to screen, and this bar goes with
 *       them: it is always clear the look is a trial, and the way out is always there.
 *
 * It draws nothing when no preview is on. App.jsx puts it above every screen.
 *
 * Choosing sends PUT /api/settings/template. Only a manager's request is accepted
 * (the backend decides); the other terminals pick the change up within seconds.
 */
function TemplatePreviewBar() {
  const { previewing } = useTemplates();
  const [message, setMessage] = useState('');           // Why the last attempt to choose was refused
  const [sending, setSending] = useState(false);

  if (previewing === null) return null;

  const template = TEMPLATES.find(candidate => candidate.id === previewing);

  const chooseForEveryone = async () => {
    setSending(true);
    setMessage('');
    try {
      const response = await apiFetch('/api/settings/template', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ template: previewing })
      });
      const answer = await response.json().catch(() => null);

      if (!response.ok) {
        setMessage(answer?.message || 'That could not be saved.');       // The backend's own explanation, as-is
        return;
      }
      showChosenTemplate(answer.template);              // It is the restaurant's template now...
      stopPreview();                                    // ...so this is no longer a trial
    } catch (error) {
      console.error('Choosing a template failed:', error);
      setMessage('Cannot reach the server.');
    } finally {
      setSending(false);
    }
  };

  const stop = () => {
    setMessage('');
    stopPreview();
  };

  return (
    // role="status": a screen reader announces the bar when it appears
    <div className="template-preview-bar" role="status" aria-label="Template preview">
      <span className="template-preview-text">
        Previewing <strong>{template.name}</strong> on this terminal only. Nobody else&apos;s screen has changed.
      </span>
      {message && <span className="template-preview-message" role="alert">{message}</span>}
      <button className="template-preview-use" onClick={chooseForEveryone} disabled={sending}>
        USE {template.name.toUpperCase()} EVERYWHERE
      </button>
      <button className="template-preview-stop" onClick={stop} disabled={sending}>
        STOP PREVIEW
      </button>
    </div>
  );
}

export default TemplatePreviewBar;
