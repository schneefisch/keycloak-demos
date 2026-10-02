# Verification

Every break/fix cycle of this demo was run end to end. This file records what was observed, verbatim.

- **Date:** 2026-10-01 / 2026-10-02
- **Keycloak:** 26.7.5 (`quay.io/keycloak/keycloak:26.7.5`, `start-dev`)
- **Stack:** keycloak-js 26.2.4, React 19.3.0, Vite 8.3.2, Spring Boot 4.1.1 (Spring Security 7.1), Java 25.0.4.1
- **Host:** macOS, colima with Docker 29.2.1, Docker Compose 5.5.1, Chromium-based browser

`realmId`, `sessionId`, `userId` and `code_id` values are replaced with `…` below. `ipAddress="172.19.0.1"` is the Docker bridge
gateway (requests from the host browser); `0:0:0:0:0:0:0:1` is the toolbox container, which shares Keycloak's network.

## Baseline

- keycloak-js sends `redirect_uri=http%3A%2F%2Flocalhost%3A5173%2F` (decoded: `http://localhost:5173/`, **with** trailing slash;
  it uses `location.href`).
- Decoded access token: `iss` `http://localhost:8080/realms/demo`, `aud` `"demo-api"`, `typ` `"Bearer"`, `azp` `"spa"`,
  `allowed-origins` `["http://localhost:5173"]`. ID token: `aud` `"spa"`, `typ` `"ID"`. Both have the JWT header `"typ": "JWT"`.
- API: `200 {"message":"Hello, alice!","iss":"http://localhost:8080/realms/demo","aud":["demo-api"],"azp":"spa"}`
- Discovery from inside the API container:
  `issuer` = `http://localhost:8080/realms/demo`, `jwks_uri` = `http://keycloak:8080/realms/demo/protocol/openid-connect/certs`,
  `token_endpoint` = `http://keycloak:8080/realms/demo/protocol/openid-connect/token`.

## Error 1 — HTTPS required (`scripts/break.sh 1`)

- Script output: `realm demo: sslRequired: external -> all`
- Browser (after clicking Login, URL bar shows the `…/protocol/openid-connect/auth?client_id=spa…` URL):
  **We are sorry... / HTTPS required**
- Log:
  `WARN [org.keycloak.events] (executor-thread-8) type="LOGIN_ERROR", realmId="…", realmName="demo", clientId="null", userId="null", ipAddress="172.19.0.1", error="ssl_required"`
- Fix: `realm demo: sslRequired: all -> external`; login works again.
- The hint *"Non-secure context detected…"* does not appear in this setup (everything is on localhost).

## Error 2 — Invalid parameter: redirect_uri (`scripts/break.sh 2`)

- Script output: `client spa: {"redirectUris":["http://localhost:5173/*"]} -> {"redirectUris":["http://localhost:5173"]}`
- Browser: **We are sorry... / Invalid parameter: redirect_uri**, URL bar contains
  `redirect_uri=http%3A%2F%2Flocalhost%3A5173%2F`
- Log:
  `WARN [org.keycloak.events] (executor-thread-11) type="LOGIN_ERROR", realmId="…", realmName="demo", clientId="spa", userId="null", ipAddress="172.19.0.1", error="invalid_redirect_uri", redirect_uri="http://localhost:5173/"`
- Fix restores `http://localhost:5173/*`; login works.

## Error 3 — CORS

### 3a: Web origins empty (`scripts/break.sh 3a`)

- Script output: `client spa: {"webOrigins":["+"]} -> {"webOrigins":[]}`
- Login redirect works, then the token request fails. Console:
  `Access to fetch at 'http://localhost:8080/realms/demo/protocol/openid-connect/token' from origin 'http://localhost:5173' has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present on the requested resource.`
  followed by a second console line with the real status:
  `POST http://localhost:8080/realms/demo/protocol/openid-connect/token net::ERR_FAILED 403 (Forbidden)`
- Chrome DevTools → Network lists the `token` request with status `403`; *Headers → General* shows `Status Code: 403 Forbidden`.
- SPA shows `Failed to fetch` (the `keycloak.init()` promise rejects).
- Real response (same request with `curl -H 'Origin: http://localhost:5173'`): **`403 {"error":"Invalid origin"}`**, no CORS headers.
- **No event is logged** for this case (neither in the server log nor in the saved events). The code is not exchanged.
- Baseline response of the same request: `Access-Control-Allow-Origin: http://localhost:5173`,
  `Access-Control-Allow-Credentials: true`, `Access-Control-Expose-Headers: Access-Control-Allow-Methods`.

### 3b: API without CORS (`scripts/break.sh 3b`)

- Login works, *Call API* fails. Console:
  `Access to fetch at 'http://localhost:8081/api/hello' from origin 'http://localhost:5173' has been blocked by CORS policy: Response to preflight request doesn't pass access control check: No 'Access-Control-Allow-Origin' header is present on the requested resource.`
- Network: `OPTIONS http://localhost:8081/api/hello → 401` (the preflight hits Spring Security's authentication), then the `GET` fails.

### 3c: outdated `/auth` base URL (`scripts/break.sh 3c`)

- The SPA shows `Keycloak http://localhost:8080/auth`. Login navigates to
  `http://localhost:8080/auth/realms/demo/protocol/openid-connect/auth?…` → **404**, page: **We are sorry... / Page not found**.
  No CORS error, because keycloak-js does the login as a full-page redirect.
- A `fetch()` from the SPA origin to `http://localhost:8080/auth/realms/demo/protocol/openid-connect/token` *does* show up as CORS:
  `Access to fetch at 'http://localhost:8080/auth/realms/demo/protocol/openid-connect/token' from origin 'http://localhost:5173' has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present on the requested resource.`
  The real response is **`404 Not Found`** (`Content-Type: application/json`) without CORS headers.

## Error 4 — invalid_grant (`scripts/code-exchange.sh`)

| Case       | Response                                                                 | Log `error=`                                     |
|------------|--------------------------------------------------------------------------|--------------------------------------------------|
| `reuse`    | 1st: `200` with tokens. 2nd: `400 {"error":"invalid_grant","error_description":"Code not valid"}` | `invalid_code`                |
| `redirect` | `400 {"error":"invalid_grant","error_description":"Incorrect redirect_uri"}` | `invalid_redirect_uri`                        |
| `pkce`     | `400 {"error":"invalid_grant","error_description":"PKCE verification failed: Code mismatch"}` | `pkce_verification_failed`, `reason="Code mismatch"` |
| `expired` (70 s wait) | `400 {"error":"invalid_grant","error_description":"Code not valid"}` (**not** "Code is expired") | `invalid_code` |

Log lines:

```
WARN [org.keycloak.events] (executor-thread-8) type="CODE_TO_TOKEN_ERROR", realmId="…", realmName="demo", clientId="cli-demo", userId="null", sessionId="…", ipAddress="0:0:0:0:0:0:0:1", error="invalid_code", grant_type="authorization_code", code_id="…", client_auth_method="client-secret"
WARN [org.keycloak.events] (executor-thread-5) type="CODE_TO_TOKEN_ERROR", realmId="…", realmName="demo", clientId="cli-demo", userId="…", sessionId="…", ipAddress="0:0:0:0:0:0:0:1", error="invalid_redirect_uri", reason="Parameter 'redirect_uri' did not match originally saved redirect URI used in initial OIDC request. Saved redirectUri: http://localhost:9999/callback, redirectUri parameter: http://localhost:9999/other-callback", grant_type="authorization_code", code_id="…", client_auth_method="client-secret"
WARN [org.keycloak.events] (executor-thread-8) type="CODE_TO_TOKEN_ERROR", realmId="…", realmName="demo", clientId="cli-demo", userId="…", sessionId="…", ipAddress="0:0:0:0:0:0:0:1", error="pkce_verification_failed", reason="Code mismatch", grant_type="authorization_code", code_id="…", …
```

For the expired code Keycloak additionally logs
`WARN [org.keycloak.protocol.oidc.utils.OAuth2CodeParser] … Code '…' already used for userSession '…' and client '…'.` —
misleading, the code was never used.

Why "Code is expired" doesn't show: `OAuth2CodeParser` stores the code in the single-use store with a lifespan of exactly
*Client Login Timeout* (`accessCodeLifespan`). After that the entry is gone, so the lookup fails first ("Code not valid"); the later
expiry check is only a fallback for a race window.

Note: the log shows `client_auth_method="client-secret"` even for the public client `cli-demo`.

## Error 5 — 401 from the API (`scripts/call-api.sh`, `scripts/break.sh 5`)

All responses `HTTP 401`. Since Spring Security 7, every `WWW-Authenticate` also contains
`resource_metadata="…/.well-known/oauth-protected-resource"` (RFC 9728); the API serves that document automatically.

| Case               | `WWW-Authenticate`                                                                                                  |
|--------------------|---------------------------------------------------------------------------------------------------------------------|
| no token           | `Bearer resource_metadata="http://localhost:8081/.well-known/oauth-protected-resource"`                             |
| malformed          | `Bearer error="invalid_token", error_description="An error occurred while attempting to decode the Jwt: Malformed token", error_uri="https://tools.ietf.org/html/rfc6750#section-3.1", resource_metadata="…"` |
| expired            | `… error_description="An error occurred while attempting to decode the Jwt: Jwt expired at 2026-10-01T21:27:08Z", …` |
| ID token           | `… error_description="An error occurred while attempting to decode the Jwt: The aud claim is not valid", …`          |
| no `demo-api` aud  | `… error_description="An error occurred while attempting to decode the Jwt: The aud claim is not valid", …`          |

- `scripts/break.sh 5`: `client spa: default client scope demo-api-audience removed` (same for `cli-demo`). The next access token
  has **no `aud` claim at all**.
- When a token is both expired and has the wrong audience, only the first error (`Jwt expired at …`) is reported.
- The API log stays silent at the default log level for all of these.

## Error 6 — Issuer mismatch (`scripts/break.sh 6`)

- Discovery, same realm, two issuers:
  from the host `issuer` = `http://localhost:8080/realms/demo`; from inside the API container `issuer` =
  `http://keycloak:8080/realms/demo`, `jwks_uri` = `http://keycloak:8080/realms/demo/protocol/openid-connect/certs`.
- Token from the browser: `iss` = `http://localhost:8080/realms/demo`.
- Call API: `401`, `WWW-Authenticate: Bearer error="invalid_token", error_description="An error occurred while attempting to decode the Jwt: The iss claim is not valid", error_uri="https://tools.ietf.org/html/rfc6750#section-3.1", resource_metadata="http://localhost:8081/.well-known/oauth-protected-resource"`
- API log: nothing (startup is normal).
- After `scripts/fix.sh 6` and a new login: `200`.

## Answers to the open questions

1. **kcadm.sh workaround on 26.7.5:** Yes, when `master` is on the default *External requests*. Tested on a throwaway container:
   `kcadm.sh config credentials --server http://localhost:8080 --realm master --user admin` → `Logging into http://localhost:8080 as user admin of realm master`
   (without `--password` it prompts for it), then `kcadm.sh update realms/master -s sslRequired=NONE` → exit 0, no output;
   `get realms/master` shows `"sslRequired" : "none"`. It does **not** help if `master` was set to *All requests*: login from
   inside the container then fails with `HTTPS required [invalid_request]`. The throwaway container was removed afterwards.
2. **Section name:** *Access settings* (Clients → spa → Settings). It contains Root URL, Home URL, Valid redirect URIs,
   Valid post logout redirect URIs, Web origins, Admin URL. PKCE is under *Capability config* → *Require PKCE* / *PKCE Method*.
3. **Outdated `/auth` URL:** Not with keycloak-js login: it's a full-page navigation, the browser shows Keycloak's
   *Page not found* page (404). It appears as a CORS error only for `fetch()`/XHR calls (e.g. token, userinfo); the real
   status is **404**, without CORS headers.
4. **Refresh token after code reuse:** No longer works. `400 {"error":"invalid_grant","error_description":"Session doesn't have required client"}`,
   log `type="REFRESH_TOKEN_ERROR", …, error="invalid_token", reason="Session doesn't have required client"`.
5. **React StrictMode + keycloak-js 26.2.4:** No double code exchange (one `CODE_TO_TOKEN` per login in the saved events) in all
   three patterns tested: `init()` once at module level (this demo), a shared instance with `init()` in `useEffect`, and
   `new Keycloak()` inside `useEffect`. The shared-instance pattern throws
   `A 'Keycloak' instance can only be initialized once.` on StrictMode's second effect run. With one instance per effect, the
   second instance finds no stored callback state and stays unauthenticated. **Don't mention a double exchange in the article.**
6. **No token:** Yes. `WWW-Authenticate: Bearer resource_metadata="http://localhost:8081/.well-known/oauth-protected-resource"`,
   no `error=`. Same header (no `error=`) when the API cannot initialize its JwtDecoder (see 7).
7. **When the issuer mismatch shows up:** Never at startup; Spring Boot 4.1 resolves the issuer lazily. Always on the first
   request with a token:
   - `issuer-uri` + `jwk-set-uri` (this demo's break 6), or `issuer-uri` only while Keycloak has no fixed hostname:
     401 with `The iss claim is not valid`, nothing in the log.
   - `issuer-uri=http://keycloak:8080/realms/demo` only, Keycloak with `KC_HOSTNAME`: client gets 401 **without** `error=`; API log:
     `ERROR … JwtDecoderInitializationException: Failed to lazily resolve the supplied JwtDecoder instance` caused by
     `java.lang.IllegalStateException: The Issuer "http://localhost:8080/realms/demo" provided in the configuration did not match the requested issuer "http://keycloak:8080/realms/demo"`.
   - `issuer-uri=http://localhost:8080/realms/demo` only, inside Docker: same 401/ERROR, caused by
     `Unable to resolve the Configuration with the provided Issuer of "http://localhost:8080/realms/demo"` /
     `I/O error on GET request for "http://localhost:8080/realms/demo/.well-known/openid-configuration": Connection refused`.
8. **HTTPS required as an event:** Yes. `type="LOGIN_ERROR", …, clientId="null", userId="null", ipAddress="172.19.0.1", error="ssl_required"`
   (WARN in the log, also listed under Events).
9. **Defaults in 26.7.5** (realm `demo`, unchanged; *Realm settings → Tokens → Access tokens*): *Access Token Lifespan* **5 minutes**
   (`accessTokenLifespan` 300), *Client Login Timeout* **1 minute** (`accessCodeLifespan` 60). For context: SSO Session Idle 30 min,
   *Access Token Lifespan For Implicit Flow* 15 min. Spring adds 60 s clock skew, so an access token is rejected ~6 min after issue.
10. **ID token with the audience check disabled:** **Accepted.** With `audiences` empty the API returned
    `200 {"message":"Hello, alice!","iss":"http://localhost:8080/realms/demo","aud":["spa"],"azp":"spa"}` for the ID token. Spring
    doesn't check the payload `typ` (`ID` vs `Bearer`) and the JWT header `typ` is `JWT` for both. Security note: the `aud` check
    is what keeps ID tokens (and tokens for other APIs) out.

## Differences from the outline

- **Screenshot 07:** keycloak-js sends the URI *with* trailing slash, so the break uses `http://localhost:5173` (without). Highlight
  `redirect_uri=http%3A%2F%2Flocalhost%3A5173%2F`.
- **Error 4 table:** an expired code returns `Code not valid` / `invalid_code`, not `Code is expired` (see above).
- **Error 3, variant 3:** true for `fetch()` calls only; an outdated URL in keycloak-js breaks at the login redirect with a 404 page.
- **Error 3, variant 1:** behind the CORS error Keycloak answers `403 {"error":"Invalid origin"}` and logs nothing. Chrome shows the
  403 in the console (second line) and in the Network tab, so the browser is the place to find it, not the Keycloak log.
- **Error 5:** Spring Security 7 appends `resource_metadata="…"` to every `WWW-Authenticate` header; plan for it in screenshot 13.
- **Error 6:** the "did not match the requested issuer" message appears only in the API log (`ERROR`), and only with `issuer-uri`
  alone; the client then gets a 401 without `error=`.
