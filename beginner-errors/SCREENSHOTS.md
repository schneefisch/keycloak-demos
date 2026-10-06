# Screenshots

Steps for each screenshot of the article. Start from the baseline (`docker compose up -d --wait`, `scripts/fix.sh all`) and run
`scripts/fix.sh <n>` after each break. Admin console: http://localhost:8080/admin, `admin`/`admin`, then switch to realm **demo**.
SPA login: `alice`/`alice`.

Tips:
- Keycloak keeps you logged in (SSO session, 30 min idle). After a break, *Login* often redirects straight back without a form.
  Use *Logout* or a private window to see the form again.
- Tokens live only in the SPA's memory: reloading the page logs you out of the SPA (not of Keycloak).
- For terminal screenshots, macOS: `ACCESS_TOKEN=$(pbpaste)` right after clicking *Copy access token*.

---

### 02 · `02_server_log_events.png` — WARN event line

- **State:** `scripts/break.sh 2`
- **Steps:** open http://localhost:5173 → *Login*. Then in the terminal:
  `docker compose logs keycloak | grep org.keycloak.events`
- **Visible:**
  `WARN [org.keycloak.events] … type="LOGIN_ERROR", realmId="…", realmName="demo", clientId="spa", userId="null", ipAddress="172.19.0.1", error="invalid_redirect_uri", redirect_uri="http://localhost:5173/"`
- **Highlight:** `error="invalid_redirect_uri"` and `redirect_uri="http://localhost:5173/"`
- **Afterwards:** `scripts/fix.sh 2`

### 03 · `03_events_save_settings.png` — Save events

- **State:** baseline
- **Steps:** admin console → realm *demo* → *Realm settings* → tab *Events* → sub-tab *User events settings*
- **Visible:** *Save events* toggle **On**, *Expiration*, the list of saved event types
- **Highlight:** the toggle

### 04 · `04_events_list.png` — Events list (optional)

- **State:** needs one LOGIN_ERROR and one CODE_TO_TOKEN_ERROR. Produce them:
  1. `scripts/break.sh 2`, SPA → *Login* (LOGIN_ERROR), `scripts/fix.sh 2`
  2. `scripts/code-exchange.sh reuse` (CODE_TO_TOKEN_ERROR `invalid_code`)
- **Steps:** admin console → realm *demo* → *Events* (left menu) → *User events*. Expand the `CODE_TO_TOKEN_ERROR` row.
- **Visible:** rows `LOGIN_ERROR`, `CODE_TO_TOKEN`, `CODE_TO_TOKEN_ERROR`; expanded row with `error: invalid_code`
- **Highlight:** the `error` field
- Note: `scripts/break.sh 6` / `fix.sh 6` recreate Keycloak and clear the saved events.

### 05 · `05_https_required_page.png` — HTTPS required

- **State:** `scripts/break.sh 1` (realm *demo* only, never `master`)
- **Steps:** SPA → *Login*
- **Visible:** URL bar with `http://localhost:8080/realms/demo/protocol/openid-connect/auth?client_id=spa…` and the page
  **We are sorry... / HTTPS required**
- **Caption:** reproduced locally by setting *Require SSL* to *All requests*.
- **Afterwards:** `scripts/fix.sh 1`

### 06 · `06_realm_require_ssl.png` — Require SSL dropdown

- **State:** baseline (shows *External requests*); take it right after `break.sh 1` to show *All requests* selected instead.
- **Steps:** admin console → realm *demo* → *Realm settings* → tab *General* → open the *Require SSL* dropdown
- **Visible:** the three options *All requests*, *External requests*, *None*
- **Highlight:** the selected value

### 07 · `07_redirect_uri_error_page.png` — Invalid parameter: redirect_uri

- **State:** `scripts/break.sh 2` (Valid redirect URIs = `http://localhost:5173`, no slash, no wildcard)
- **Steps:** SPA → *Login*
- **Visible:** full URL bar and **We are sorry... / Invalid parameter: redirect_uri**
- **Highlight:** `redirect_uri=http%3A%2F%2Flocalhost%3A5173%2F` — keycloak-js sends the URI *with* the trailing slash
  (this differs from the outline, see VERIFICATION.md)
- **Afterwards:** keep the break for 08, then `scripts/fix.sh 2`

### 08 · `08_client_access_settings.png` — Access settings

- **State:** variant 1 with `scripts/break.sh 2` active, variant 2 after `scripts/fix.sh 2`
- **Steps:** admin console → realm *demo* → *Clients* → `spa` → tab *Settings* → section **Access settings**
- **Visible:** *Valid redirect URIs*, *Valid post logout redirect URIs*, *Web origins* (`+`)
- **Highlight:** variant 1: `http://localhost:5173` (missing `/*`); variant 2: `http://localhost:5173/*`

### 09 · `09_cors_console_error.png` — CORS error in the console

- **State:** `scripts/break.sh 3a`
- **Steps:** open DevTools → *Console*, then SPA → *Login* (you come back to the SPA, the token request fails)
- **Visible:**
  `Access to fetch at 'http://localhost:8080/realms/demo/protocol/openid-connect/token' from origin 'http://localhost:5173' has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present on the requested resource.`
  and below it `POST http://localhost:8080/realms/demo/protocol/openid-connect/token net::ERR_FAILED 403 (Forbidden)`
- **Highlight:** the URL (Keycloak `/token`) and the origin
- **Variant for "the error comes from your API":** `scripts/fix.sh 3`, `scripts/break.sh 3b`, log in, *Call API*:
  `Access to fetch at 'http://localhost:8081/api/hello' … Response to preflight request doesn't pass access control check: No 'Access-Control-Allow-Origin' header …`

### 10 · `10_cors_network_headers.png` — token request headers, before/after

- **Before:** `scripts/break.sh 3a`. DevTools → *Network*, SPA → *Login*. Select the `token` request (filter *Fetch/XHR*) →
  *Headers*. Visible: *General → Status Code* `403 Forbidden`, no `Access-Control-Allow-Origin` in the response headers, request
  header `Origin: http://localhost:5173`. The request headers also carry Keycloak's session cookies; crop or blur them.
- **After:** `scripts/fix.sh 3`, *Logout*, *Login* again. Same request: `Access-Control-Allow-Origin: http://localhost:5173`.
- **Highlight:** `Origin` and `Access-Control-Allow-Origin`

### 11 · `11_invalid_grant_double_exchange.png` — same code twice

- **State:** baseline
- **Steps:** `scripts/code-exchange.sh reuse`, open the printed URL, log in, paste the URL from the address bar
- **Visible:** first `curl -s -X POST …/token -d grant_type=authorization_code … -d code=… -d code_verifier=…` → `HTTP 200`
  with shortened tokens (`eyJhbGciOiJS…`); second identical call → `HTTP 400` `"error": "invalid_grant"`,
  `"error_description": "Code not valid"`. Below it: the refresh token from the first exchange fails with
  `Session doesn't have required client`.
- **Highlight:** the second response

### 12 · `12_realm_tokens_tab.png` — token lifespans

- **State:** baseline
- **Steps:** admin console → realm *demo* → *Realm settings* → tab *Tokens* → section *Access tokens*
- **Visible:** *Access Token Lifespan* `5 Minutes`, *Client Login Timeout* `1 Minutes`
- **Highlight:** both fields

### 13 · `13_api_401_www_authenticate.png` — 401 + WWW-Authenticate

- **State:** baseline
- **Steps:** SPA → *Login* → *Copy ID token*, then on the host:
  ```bash
  TOKEN=$(pbpaste)
  curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/hello
  ```
  (or, without host curl: `ID_TOKEN=$(pbpaste) scripts/call-api.sh id-token`)
- **Visible:** `HTTP/1.1 401` and
  `WWW-Authenticate: Bearer error="invalid_token", error_description="An error occurred while attempting to decode the Jwt: The aud claim is not valid", error_uri="https://tools.ietf.org/html/rfc6750#section-3.1", resource_metadata="http://localhost:8081/.well-known/oauth-protected-resource"`
- **Highlight:** `error="invalid_token"` and `error_description="…"`
- Alternatives: an access token older than ~6 min → `Jwt expired at …`; no header at all → `Bearer resource_metadata="…"` (no `error=`).

### 14 · `14_decoded_access_token.png` — decoded token

- **State:** baseline
- **Steps:** SPA → *Login* → *Copy access token*, then `ACCESS_TOKEN=$(pbpaste) scripts/decode-token.sh`
  (it prints the `jq` command it runs; with host jq the same command works directly)
- **Visible:** `iss`, `aud`, `exp` (+ `exp_utc`), `typ`, `azp`, `allowed-origins`
- **Highlight:** all six; `allowed-origins` ties back to Error ③

### 15 · `15_api_issuer_mismatch_log.png` — issuer mismatch

- **State:** `scripts/break.sh 6` (Keycloak is recreated: log in again)
- **Steps:** SPA → *Login* → *Call API*; or *Copy access token* and
  `curl -i -H "Authorization: Bearer $(pbpaste)" http://localhost:8081/api/hello`
- **Visible:** `401`, `error_description="An error occurred while attempting to decode the Jwt: The iss claim is not valid"`;
  in the SPA also the claims table with `iss "http://localhost:8080/realms/demo"`
- **Highlight:** the error description and the `iss` value (localhost) — the API expects `http://keycloak:8080/realms/demo`
- The API log stays empty in this configuration. The log variant with *"did not match the requested issuer"* needs `issuer-uri`
  without `jwk-set-uri`; the exact text is in VERIFICATION.md (question 7).
- **Afterwards:** keep the break for the "before" shot of 16, then `scripts/fix.sh 6`

### 16 · `16_discovery_inside_container.png` — issuer vs jwks_uri

- **State:** after `scripts/fix.sh 6` (optional "before" shot while break 6 is active)
- **Steps:**
  ```bash
  docker compose exec api sh -c 'curl -s http://keycloak:8080/realms/demo/.well-known/openid-configuration | jq "{issuer, jwks_uri, token_endpoint}"'
  ```
- **Visible (after the fix):** `"issuer": "http://localhost:8080/realms/demo"`,
  `"jwks_uri": "http://keycloak:8080/realms/demo/protocol/openid-connect/certs"`,
  `"token_endpoint": "http://keycloak:8080/realms/demo/protocol/openid-connect/token"`.
  Before the fix all three start with `http://keycloak:8080`.
- **Highlight:** `issuer` (localhost) vs `jwks_uri` (keycloak)
