import { useEffect, useState } from 'react';
import { apiUrl, config, keycloak, keycloakReady } from './keycloak.js';

const CLAIMS = ['iss', 'aud', 'exp', 'typ', 'azp', 'allowed-origins'];

function formatClaim(name, value) {
  if (value === undefined) return '—';
  if (name === 'exp') return `${value}  (${new Date(value * 1000).toLocaleTimeString()})`;
  return JSON.stringify(value);
}

export default function App() {
  const [authenticated, setAuthenticated] = useState(false);
  const [initError, setInitError] = useState(null);
  const [apiResult, setApiResult] = useState(null);
  const [copied, setCopied] = useState('');

  useEffect(() => {
    keycloakReady.then(setAuthenticated).catch((e) => setInitError(e?.message ?? String(e)));
    keycloak.onAuthError = (e) => setInitError(`Token request failed: ${e?.error ?? 'see DevTools → Console / Network'}`);
  }, []);

  async function callApi() {
    const headers = keycloak.token ? { Authorization: `Bearer ${keycloak.token}` } : {};
    try {
      const res = await fetch(`${apiUrl}/api/hello`, { headers });
      setApiResult({
        status: res.status,
        wwwAuthenticate: res.headers.get('WWW-Authenticate'),
        body: await res.text(),
      });
    } catch (e) {
      // fetch() only throws on network/CORS errors; the real reason is in the DevTools console
      setApiResult({ error: `${e.message} (CORS or network error, see DevTools → Console)` });
    }
  }

  async function copy(label, value) {
    await navigator.clipboard.writeText(value ?? '');
    setCopied(label);
    setTimeout(() => setCopied(''), 1500);
  }

  const claims = keycloak.tokenParsed ?? {};

  return (
    <main>
      <h1>Keycloak Beginner Errors</h1>
      <p className="muted">
        Keycloak <code>{config.url}</code> · realm <code>{config.realm}</code> · client <code>{config.clientId}</code> · API <code>{apiUrl}</code>
      </p>

      <section className="row">
        {authenticated ? (
          <>
            <span>Logged in as <strong>{claims.preferred_username}</strong></span>
            <button onClick={() => keycloak.logout()}>Logout</button>
          </>
        ) : (
          <button onClick={() => keycloak.login()}>Login</button>
        )}
        <button onClick={callApi}>Call API</button>
        <button disabled={!keycloak.token} onClick={() => copy('access', keycloak.token)}>Copy access token</button>
        <button disabled={!keycloak.idToken} onClick={() => copy('id', keycloak.idToken)}>Copy ID token</button>
        {copied && <span className="muted">Copied {copied} token (demo only!)</span>}
      </section>

      {initError && <p className="error">{initError}</p>}

      <h2>Access token claims</h2>
      <table>
        <tbody>
          {CLAIMS.map((name) => (
            <tr key={name}>
              <th>{name}</th>
              <td><code>{formatClaim(name, claims[name])}</code></td>
            </tr>
          ))}
        </tbody>
      </table>

      <h2>API response</h2>
      {!apiResult && <p className="muted">Click “Call API”.</p>}
      {apiResult?.error && <p className="error">{apiResult.error}</p>}
      {apiResult?.status && (
        <table>
          <tbody>
            <tr><th>HTTP status</th><td><code>{apiResult.status}</code></td></tr>
            <tr><th>WWW-Authenticate</th><td><code>{apiResult.wwwAuthenticate ?? '—'}</code></td></tr>
            <tr><th>Body</th><td><code>{apiResult.body || '(empty)'}</code></td></tr>
          </tbody>
        </table>
      )}
    </main>
  );
}
