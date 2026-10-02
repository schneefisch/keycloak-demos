# Six Keycloak Errors Every Beginner Hits

Companion demo for the article *Six Keycloak Errors Every Beginner Hits — and What They Actually Mean* (link follows once published).

A React SPA, a Spring Boot API and Keycloak in Docker Compose. Everything works out of the box, and one script per error breaks the
setup in exactly the way beginners break it, so you can see the real error message, find the cause and verify the fix.

> [!WARNING]
> **Demo only.** Keycloak runs in `start-dev` mode over plain HTTP, the passwords are public (`admin`/`admin`, `alice`/`alice`),
> the SPA has buttons that copy tokens to the clipboard, and `scripts/break.sh 1` changes *Require SSL*. Never reuse any of this
> in production.

## Quick Start

Prerequisites: **Docker with Compose v2.24.4 or newer**. Nothing else; the scripts run `curl`, `jq` and `openssl` in a helper container.

```bash
cd beginner-errors
docker compose up -d --wait
```

The first start builds the API and SPA images and takes a few minutes. Then:

| What                  | URL                                                                | Login           |
|-----------------------|--------------------------------------------------------------------|-----------------|
| SPA                   | http://localhost:5173                                              | `alice`/`alice` |
| API                   | http://localhost:8081/api/hello                                    | Bearer token    |
| Keycloak admin console | http://localhost:8080/admin (realm `demo`)                        | `admin`/`admin` |

Ports used: `8080` (Keycloak), `8081` (API), `5173` (SPA). `http://localhost:9999/callback` is only a redirect target for
`scripts/code-exchange.sh`; nothing listens there.

Click **Login**, then **Call API**. You should see `HTTP status 200` and `Hello, alice!`.

## What's Inside

```
Browser ──localhost:5173──▶ spa       React + Vite + keycloak-js (PKCE S256)
   │
   ├──────localhost:8080──▶ keycloak  realm demo, clients spa + cli-demo, user alice
   │                           ▲
   └──────localhost:8081──▶ api ──keycloak:8080──┘  Spring Boot resource server, GET /api/hello
```

- **Realm `demo`** ([realm-export.json](realm-export.json)): user events are saved, the `jboss-logging` listener writes error events
  to the server log.
- **Client `spa`**: public, Standard flow, PKCE S256, Valid redirect URIs `http://localhost:5173/*`, Web origins `+`.
- **Client `cli-demo`**: public, PKCE S256, redirect URI `http://localhost:9999/callback`, used by `scripts/code-exchange.sh`.
- **Client scope `demo-api-audience`**: puts `demo-api` into the `aud` claim of access tokens. Default scope of both clients.
- **API** ([application.yml](api/src/main/resources/application.yml)): checks `iss` = `http://localhost:8080/realms/demo`, `aud` =
  `demo-api` and `exp`; fetches keys from `http://keycloak:8080/...`. CORS allows `http://localhost:5173` and exposes
  `WWW-Authenticate`.
- **Keycloak hostname**: `KC_HOSTNAME=http://localhost:8080` + `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true` give one issuer for browser
  and containers.

There is no password grant (ROPC) anywhere. Scripts take tokens from the SPA's copy buttons or from the authorization code flow.

## Break It, Fix It

Run one break at a time, look at the error, then restore the baseline.

| #  | Error                                   | Break                  | What you see                                                                                   | Fix                  |
|----|-----------------------------------------|------------------------|------------------------------------------------------------------------------------------------|----------------------|
| 1  | HTTPS required                          | `scripts/break.sh 1`   | Login → *We are sorry… HTTPS required*                                                         | `scripts/fix.sh 1`   |
| 2  | Invalid parameter: redirect_uri         | `scripts/break.sh 2`   | Login → *We are sorry… Invalid parameter: redirect_uri*                                        | `scripts/fix.sh 2`   |
| 3a | CORS: Keycloak doesn't know the origin  | `scripts/break.sh 3a`  | Login → console: CORS error on `…/token`                                                       | `scripts/fix.sh 3`   |
| 3b | CORS: the API sends no CORS headers     | `scripts/break.sh 3b`  | Call API → console: CORS error on the preflight to `localhost:8081`                            | `scripts/fix.sh 3`   |
| 3c | Outdated `/auth` base URL               | `scripts/break.sh 3c`  | Login → *We are sorry… Page not found* (a `fetch()` to that URL shows up as CORS)               | `scripts/fix.sh 3`   |
| 4  | invalid_grant                           | –                      | `scripts/code-exchange.sh`: *Code not valid*, *Incorrect redirect_uri*, *PKCE verification failed* | –                |
| 5  | 401 from the API                        | `scripts/break.sh 5`   | `scripts/call-api.sh …`: 401 + `WWW-Authenticate` with the reason                              | `scripts/fix.sh 5`   |
| 6  | Issuer mismatch                         | `scripts/break.sh 6`   | Call API → 401 `The iss claim is not valid`                                                    | `scripts/fix.sh 6`   |

`scripts/fix.sh all` restores everything. All switches are idempotent and print what they changed.

- Switches 1, 2, 3a and 5 change the realm with `kcadm.sh` inside the Keycloak container.
- Switches 3b, 3c and 6 recreate containers with an override file from [compose/](compose). They replace each other, so use one at
  a time. Recreating Keycloak (6) re-imports the realm: other Keycloak breaks and saved events are reset, and you log in again.

### Helper scripts

```bash
scripts/code-exchange.sh [all|reuse|redirect|pkce|expired]   # Error 4: exchange codes for cli-demo by hand
scripts/call-api.sh [all|no-token|malformed|valid|id-token|no-audience|expired]   # Error 5
scripts/decode-token.sh [--all]                               # decode a token locally with jq
```

`code-exchange.sh` prints an authorization URL. Open it, log in as `alice`, and copy the URL from the address bar after the redirect
(the page at `localhost:9999` doesn't load — that's expected). Each case needs a new code, because Keycloak burns a code on the
first exchange attempt.

`call-api.sh` and `decode-token.sh` read tokens from `ACCESS_TOKEN` / `ID_TOKEN` or ask for them. Use the SPA's copy buttons:

```bash
ACCESS_TOKEN=$(pbpaste) scripts/decode-token.sh        # macOS; on Linux e.g. $(xclip -o -selection clipboard)
```

### Where Keycloak tells you the real reason

```bash
docker compose logs -f keycloak | grep org.keycloak.events
```

Error events are logged at `WARN`, e.g.
`type="LOGIN_ERROR", realmName="demo", clientId="spa", …, error="invalid_redirect_uri", redirect_uri="http://localhost:5173/"`.
The same events are listed in the admin console under *Events* (realm `demo`).

## Versions

| Component       | Version                                                            |
|-----------------|--------------------------------------------------------------------|
| Keycloak        | `quay.io/keycloak/keycloak:26.7.5`                                 |
| keycloak-js     | 26.2.4                                                             |
| React / Vite    | 19.3.0 / 8.3.2 (`@vitejs/plugin-react` 6.1.1), Node `24.21.0-alpine` |
| Spring Boot     | 4.1.1 (Spring Security 7.1), Java 25 (`eclipse-temurin:25.0.4.1_1-jre-noble`), Gradle 9.8.0 |
| Toolbox         | `alpine:3.24.2` with bash, curl, jq, openssl                       |

Spring Boot 4 renamed the resource server starter to `spring-boot-starter-security-oauth2-resource-server` (the old
`spring-boot-starter-oauth2-resource-server` still exists but is deprecated).

Results of running every break/fix cycle: [VERIFICATION.md](VERIFICATION.md). Screenshot instructions for the article:
[SCREENSHOTS.md](SCREENSHOTS.md).

## Cleanup

```bash
docker compose --profile tools down --rmi local
```

Removes the containers, the network and the locally built images (`api`, `spa`, `toolbox`). No volumes are created.

## License

MIT — see [LICENSE](../LICENSE)
