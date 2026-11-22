import { backendAddressFrom, sendForAnswer } from './api';

/**
 * Tests for where the frontend looks for the backend.
 *
 * WHY: There are two ways the app runs. While developing, the frontend and the
 *      backend are two programs on two ports. In the packaged build, the backend
 *      serves the frontend, and a phone must send its requests to whatever
 *      address it opened the app at. Getting this wrong does not fail a build:
 *      it produces an app that works on the computer it was built on and shows
 *      "Failed to fetch" on every phone.
 */

test('while developing, with no setting, the backend is on this computer at port 8080', () => {
  expect(backendAddressFrom({ DEV: true })).toBe('http://localhost:8080');
});

test('a built app with no setting uses the address it was loaded from', () => {
  // '' in front of '/api/tables' leaves '/api/tables': the browser sends it to
  // wherever the page came from (http://192.168.1.50:8080 on a phone, say)
  expect(backendAddressFrom({ DEV: false })).toBe('');
});

test('a VITE_API_URL setting wins, developing or built', () => {
  expect(backendAddressFrom({ VITE_API_URL: 'http://192.168.1.50:8080', DEV: true })).toBe('http://192.168.1.50:8080');
  expect(backendAddressFrom({ VITE_API_URL: 'http://192.168.1.50:8080', DEV: false })).toBe('http://192.168.1.50:8080');
});

test('an empty setting counts as no setting', () => {
  // The tests pin VITE_API_URL to '' (vite.config.js), and package.sh builds with it
  // empty, so that a .env.local made for a tablet cannot leak into either
  expect(backendAddressFrom({ VITE_API_URL: '', DEV: true })).toBe('http://localhost:8080');
  expect(backendAddressFrom({ VITE_API_URL: '', DEV: false })).toBe('');
});

// ---------------------------------------------------------------------------
// sendForAnswer: a request whose refusal has to be shown to the person who made it
// ---------------------------------------------------------------------------

describe('sendForAnswer', () => {
  const answering = (status, body) => {
    global.fetch = vi.fn(async () => ({
      ok: status < 400,
      status,
      headers: { get: () => null },
      json: async () => {
        if (body === undefined) throw new Error('no body');      // What response.json() does on an empty answer
        return body;
      }
    }));
  };

  afterEach(() => {
    vi.restoreAllMocks();
  });

  test('when it works, the answer is handed back', async () => {
    answering(201, { challengeId: 31, name: 'Big night' });

    expect(await sendForAnswer('POST', '/api/challenges', { name: 'Big night' }))
      .toEqual({ ok: true, data: { challengeId: 31, name: 'Big night' }, message: '' });
    expect(global.fetch.mock.calls[0][1]).toMatchObject({ method: 'POST', body: '{"name":"Big night"}' });
  });

  test('an answer with no body (a delete) is still a success', async () => {
    answering(204, undefined);

    expect(await sendForAnswer('DELETE', '/api/challenges/31')).toEqual({ ok: true, data: null, message: '' });
    // ...and no body was sent either
    expect(global.fetch.mock.calls[0][1].body).toBeUndefined();
  });

  test('a refusal comes back with the backend\'s own words, not a status number', async () => {
    answering(409, { status: 409, message: 'Dan has not completed this challenge.', fieldErrors: null, code: null });

    expect(await sendForAnswer('POST', '/api/challenges/31/awards/8'))
      .toEqual({ ok: false, data: null, message: 'Dan has not completed this challenge.' });
  });

  test('a refusal that names fields comes back as each field\'s message', async () => {
    answering(400, { message: 'Validation failed', fieldErrors: { name: 'A challenge needs a name', target: 'The target has to be more than zero' } });

    const answer = await sendForAnswer('POST', '/api/challenges', {});
    expect(answer.ok).toBe(false);
    expect(answer.message).toBe('A challenge needs a name. The target has to be more than zero');
  });

  test('a refusal with nothing readable in it still says something', async () => {
    answering(500, undefined);

    expect((await sendForAnswer('POST', '/api/challenges', {})).message).toBe('That could not be saved.');
  });

  test('when the backend cannot be reached it says so, and does not throw', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    global.fetch = vi.fn(async () => { throw new TypeError('Failed to fetch'); });

    expect(await sendForAnswer('POST', '/api/challenges', {})).toEqual({ ok: false, data: null, message: 'Cannot reach the server.' });
  });
});
