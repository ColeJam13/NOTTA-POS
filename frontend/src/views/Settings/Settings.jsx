import NavBar from '../../components/NavBar';
import { TEMPLATES, useTemplates, previewTemplate } from '../../templates';
import './Settings.css';

/**
 * A small made-up corner of the app, drawn in one template.
 *
 * HOW IT IS DRAWN IN A TEMPLATE OTHER THAN THE PAGE'S: the box carries
 * data-template="<name>". Each template's stylesheet gives its values to any element
 * with its name on it, and Settings.css draws this box with tokens only, so everything
 * inside takes that template's colors, fonts, and shapes whatever the rest of the
 * page is showing.
 *
 * It shows the things a template must keep unmistakable: a heading, an amount, a
 * locked item, a late and a warned ticket, an 86'd item, and the three kinds of button.
 */
function TemplateSample({ templateId }) {
  return (
    // aria-hidden: this is a picture of the app, not part of it. A screen reader skips it.
    <div className="template-sample" data-template={templateId} aria-hidden="true">
      <div className="template-sample-heading">TABLE W2</div>
      <div className="template-sample-row">
        <span>Omelette of the Day</span>
        <span className="template-sample-amount">$16.00</span>
      </div>
      <div className="template-sample-row locked">
        <span>Garden Toast</span>
        <span>LOCKED</span>
      </div>
      <div className="template-sample-statuses">
        <span className="template-sample-late">20 MIN</span>
        <span className="template-sample-warning">12 MIN</span>
        <span className="template-sample-86">86&apos;D</span>
      </div>
      <div className="template-sample-buttons">
        <span className="template-sample-btn primary">SEND</span>
        <span className="template-sample-btn">SAVE</span>
        <span className="template-sample-btn danger">CLOSE</span>
      </div>
    </div>
  );
}

/**
 * Settings screen (managers only). Today it holds one thing: the restaurant's template.
 *
 * WHAT A TEMPLATE IS: the look of every screen (colors, fonts, shapes). The restaurant
 * has one, kept on the backend, so every terminal and phone looks the same.
 *
 * WHAT A MANAGER DOES HERE:
 *   - sees every template, each with a small sample drawn in it, and which is in use
 *   - presses PREVIEW to try one on this terminal only. A bar then sits at the top
 *     of every screen (components/TemplatePreviewBar), so they can walk through the
 *     order screen and the kitchen display in the new look before deciding. That bar
 *     holds the two ways out: use it everywhere, or stop.
 *
 * This screen sends nothing itself. Which template is in use comes from
 * templates/index.js (App.jsx keeps it up to date from the backend), and choosing one
 * is the preview bar's job.
 */
function Settings({ setCurrentView, setSelectedTable }) {
  const { chosen, previewing } = useTemplates();

  return (
    <div className="page-with-nav">
      <NavBar currentView="settings" setCurrentView={setCurrentView} setSelectedTable={setSelectedTable} />
      <div className="settings-page">
        <h2>SETTINGS</h2>

        {/* A <section> with a name is a "region": a screen reader can jump to it, and the tests find it by that name */}
        <section className="settings-section" aria-label="Template">
          <h3>TEMPLATE</h3>
          <p className="settings-help">
            The look of every screen. The whole restaurant uses one template: when you choose
            one, every terminal and phone changes to it within a few seconds.
          </p>
          <p className="settings-help">
            PREVIEW shows a template on this terminal only, so you can open the order screen
            and the kitchen display in it before deciding. Nothing changes for anyone else
            until you choose it.
          </p>

          <div className="template-list">
            {TEMPLATES.map(template => (
              <div
                key={template.id}
                className={`template-card ${chosen === template.id ? 'in-use' : ''}`}
                data-testid={`template-${template.id}`}
              >
                <TemplateSample templateId={template.id} />
                <div className="template-card-text">
                  <h4>{template.name}</h4>
                  <p>{template.description}</p>
                </div>
                {/* One of three things on the right: this is the one in use, this is the
                    one being tried out, or a button to try it */}
                {chosen === template.id && <span className="template-in-use">IN USE</span>}
                {chosen !== template.id && previewing === template.id && (
                  <span className="template-previewing">PREVIEWING</span>
                )}
                {chosen !== template.id && previewing !== template.id && (
                  <button
                    className="template-preview-btn"
                    aria-label={`Preview ${template.name}`}
                    onClick={() => previewTemplate(template.id)}
                  >
                    PREVIEW
                  </button>
                )}
              </div>
            ))}
          </div>

          {TEMPLATES.length === 1 && (
            <p className="settings-help">This is the only template so far. More are on the way.</p>
          )}
        </section>
      </div>
    </div>
  );
}

export default Settings;
