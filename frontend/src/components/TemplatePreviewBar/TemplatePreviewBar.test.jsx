import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import TemplatePreviewBar from './TemplatePreviewBar';
import { TEMPLATES, forgetTemplates, showChosenTemplate, previewTemplate } from '../../templates';

/**
 * Behavior tests for the bar shown while a manager is trying a template out.
 *
 * WHAT: Draws the bar against a fake backend and presses its two buttons.
 * WHY:  USE ... EVERYWHERE changes the look of every terminal in the restaurant. It
 *       must send exactly the template being previewed, and when the backend refuses
 *       it must say why and leave things as they were.
 *
 * The fake backend works the same way as in CreateOrder.test.jsx: global.fetch is
 * replaced with a function that returns canned JSON.
 */

let requests;         // [{ method, path, body }]
let refusal;          // { status, message } = the fake backend refuses the next change with it

const onThePage = () => document.documentElement.getAttribute('data-template');

beforeEach(() => {
  TEMPLATES.push({ id: 'test-look', name: 'Test Look', description: 'Made up for the tests.' });
  window.localStorage.clear();
  forgetTemplates();
  showChosenTemplate('8-bit');
  requests = [];
  refusal = null;

  global.fetch = vi.fn(async (url, options = {}) => {
    const method = options.method || 'GET';
    const path = url.replace('http://localhost:8080', '');
    const body = options.body ? JSON.parse(options.body) : null;
    requests.push({ method, path, body });
    const json = (data, status = 200) => ({ ok: status < 300, status, json: async () => data });

    if (method === 'PUT' && path === '/api/settings/template') {
      if (refusal) return json({ message: refusal.message }, refusal.status);
      return json({ taxRate: 0.03, template: body.template });
    }
    throw new Error(`Unexpected request in test: ${method} ${path}`);
  });
});

afterEach(() => {
  TEMPLATES.pop();
  forgetTemplates();
  vi.restoreAllMocks();
});

test('with no preview on, the bar draws nothing', () => {
  const { container } = render(<TemplatePreviewBar />);

  expect(container).toBeEmptyDOMElement();
});

test('during a preview the bar names the template and says only this terminal shows it', () => {
  previewTemplate('test-look');
  render(<TemplatePreviewBar />);

  const bar = screen.getByRole('status', { name: 'Template preview' });
  expect(bar).toHaveTextContent('Previewing Test Look on this terminal only.');
  expect(bar).toHaveTextContent("Nobody else's screen has changed.");
  expect(screen.getByRole('button', { name: 'USE TEST LOOK EVERYWHERE' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'STOP PREVIEW' })).toBeInTheDocument();
});

test('the bar appears by itself when a preview starts', async () => {
  render(<TemplatePreviewBar />);
  expect(screen.queryByRole('status')).not.toBeInTheDocument();

  previewTemplate('test-look');

  expect(await screen.findByRole('status', { name: 'Template preview' })).toBeInTheDocument();
});

test('STOP PREVIEW goes back to the restaurant\'s template and sends nothing', async () => {
  previewTemplate('test-look');
  render(<TemplatePreviewBar />);

  await userEvent.click(screen.getByRole('button', { name: 'STOP PREVIEW' }));

  expect(onThePage()).toBe('8-bit');
  expect(screen.queryByRole('status')).not.toBeInTheDocument();
  expect(requests).toEqual([]);
});

test('USE ... EVERYWHERE sends the previewed template, and it becomes the restaurant\'s', async () => {
  previewTemplate('test-look');
  render(<TemplatePreviewBar />);

  await userEvent.click(screen.getByRole('button', { name: 'USE TEST LOOK EVERYWHERE' }));

  await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument());      // No longer a trial
  expect(requests).toEqual([{ method: 'PUT', path: '/api/settings/template', body: { template: 'test-look' } }]);
  expect(onThePage()).toBe('test-look');
  expect(window.localStorage.getItem('notta.template')).toBe('test-look');               // And remembered for the PIN pad
});

test('when the backend refuses, the bar says why, and the preview and the restaurant\'s template stay as they were', async () => {
  previewTemplate('test-look');
  render(<TemplatePreviewBar />);
  refusal = { status: 403, message: 'Your role (server) is not allowed to do that.' };

  await userEvent.click(screen.getByRole('button', { name: 'USE TEST LOOK EVERYWHERE' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('Your role (server) is not allowed to do that.');
  expect(screen.getByRole('status', { name: 'Template preview' })).toBeInTheDocument();   // Still a trial
  expect(onThePage()).toBe('test-look');
  expect(window.localStorage.getItem('notta.template')).toBe('8-bit');

  // Stopping then goes back to what the restaurant really uses
  await userEvent.click(screen.getByRole('button', { name: 'STOP PREVIEW' }));
  expect(onThePage()).toBe('8-bit');
});

test('when the backend cannot be reached, the bar says so', async () => {
  previewTemplate('test-look');
  render(<TemplatePreviewBar />);
  global.fetch = vi.fn(async () => { throw new TypeError('Failed to fetch'); });
  vi.spyOn(console, 'error').mockImplementation(() => {});          // The bar logs the failure; keep the test output clean

  await userEvent.click(screen.getByRole('button', { name: 'USE TEST LOOK EVERYWHERE' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('Cannot reach the server.');
  expect(onThePage()).toBe('test-look');
});
