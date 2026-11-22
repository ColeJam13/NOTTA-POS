import NavBar from '../../components/NavBar';
import { TEMPLATES, useTemplates, previewTemplate } from '../../templates';
import './Settings.css';

/**
 * Four dots of a template's own colors, for its card.
 *
 * HOW THEY ARE DRAWN IN A TEMPLATE OTHER THAN THE PAGE'S: the box carries
 * data-template="<name>". Each template's stylesheet gives its values to any element
 * with its name on it, so var(--color-bg) inside this box is THAT template's
 * background, whatever the page around it is showing. The four tokens to show are
 * chosen per template (its "swatches" in templates/index.js); the colors themselves
 * are never written down twice.
 */
function TemplateSwatches({ template }) {
  return (
    // aria-hidden: decoration. The name and description beside it say what the template is.
    <div className="template-swatches" data-template={template.id} aria-hidden="true">
      {template.swatches.map(token => (
        <span key={token} className="template-swatch" style={{ background: `var(${token})` }} />
      ))}
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
 *   - sees every template as a card (its name, a line about it, whether it is light or
 *     dark, and four of its colors), and which is in use
 *   - opens the GALLERY: one page with every piece of the interface in every state,
 *     drawn in whichever template is on screen. It is the quick way to judge one.
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
            until you choose it. The gallery shows every piece of the app in every state on
            one page, in whichever template is on screen.
          </p>

          <button className="settings-gallery-btn" onClick={() => setCurrentView('gallery')}>
            OPEN THE GALLERY
          </button>

          {/* A grid: as many cards across as fit, so thirteen templates are a few rows, not a long list */}
          <div className="template-list">
            {TEMPLATES.map(template => (
              <div
                key={template.id}
                className={`template-card ${chosen === template.id ? 'in-use' : ''}`}
                data-testid={`template-${template.id}`}
              >
                <div className="template-card-top">
                  <TemplateSwatches template={template} />
                  <span className="template-scheme">{template.scheme === 'light' ? 'LIGHT' : 'DARK'}</span>
                </div>
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
        </section>
      </div>
    </div>
  );
}

export default Settings;
