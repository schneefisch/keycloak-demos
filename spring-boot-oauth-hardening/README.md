# Hardening Spring Boot OAuth 2.0 / OIDC Beyond the Defaults

Companion demo for the article on hardening Spring Boot's OAuth 2.0 and OpenID Connect setup (link follows once published).

Spring Boot 4.1 (Spring Security 7.1) and Keycloak 26.8. Three Spring Boot apps run in two modes:

- **naive**: what many teams ship. `issuer-uri`, the defaults, and the Keycloak role converter from a tutorial.
- **hardened**: the same apps with the checks the defaults leave out.

Real Keycloak tokens that should be rejected are sent to both modes, so you see what gets through and which setting stops it.

> [!WARNING]
> **Demo only.** Keycloak runs in `start-dev` mode over plain HTTP, all passwords and client secrets are public
> (`admin`/`admin`, `alice`/`password`, `bob`/`password`, `<client>-secret`), and the scripts log users in through a test client.
> Never reuse any of this in production.

## Quick Start

Prerequisites: **Docker with Compose v2**. Nothing else; the scripts run `curl`, `jq` and `openssl` in a helper container.

```bash
cd spring-boot-oauth-hardening
docker compose up -d --wait        # all apps start in naive mode
scripts/attack.sh                  # tokens that should be rejected, sent to events-api
scripts/mode.sh hardened           # switch all apps to hardened mode
scripts/attack.sh                  # same tokens again
```

The first start builds three Spring Boot images and takes a few minutes.

| What                   | URL                                          | Login                                   |
|------------------------|----------------------------------------------|-----------------------------------------|
| events-portal (BFF)    | http://localhost:8082                        | `alice`/`password` or `bob`/`password`  |
| events-api             | http://localhost:8081/api/events             | Bearer token                            |
| notification-service   | http://localhost:8083/reminders/preview      | none (demo trigger)                     |
| Keycloak admin console | http://localhost:8080/admin (realm `demo`)   | `admin`/`admin`                         |

`http://localhost:9999/callback` is only a redirect target for the scripts' test client `demo-cli`; nothing listens there.

## What's Inside

```
Browser ──localhost:8082──▶ events-portal ──user's token──▶ events-api ◀──client credentials── notification-service
   │                         (BFF, oauth2Login)               (resource server)                  (backend, no frontend)
   │                                                                                                       ▲
   └──────localhost:8080──▶ keycloak (realm demo)                               reporting-job ─────────────┘
                                                                                (token for notification-service)
```

- **events-api** ([code](events-api/src/main/java/de/schneefisch/eventsapi)): `GET /api/events` for every authenticated
  caller, `POST /api/events` needs scope `events:write`, `DELETE /api/events/{id}` needs the `events-api` client role `admin`.
  `GET /api/whoami` shows what the API made of a token.
- **notification-service** ([code](notification-service/src/main/java/de/schneefisch/notificationservice)): calls events-api
  with the client credentials grant, and offers `POST /api/notifications` (scope `notifications:send`) to other services.
- **events-portal** ([code](events-portal/src/main/java/de/schneefisch/eventsportal)): BFF with `oauth2Login`, confidential
  client. Tokens stay on the server; the browser holds only the session cookie.
- **Realm `demo`** ([realm-export.json](realm-export.json)), configured the Keycloak 26.8 way:
  - Client scopes `events:read`, `events:write` and `notifications:send`, each with an Audience mapper for its API. They are
    *optional* scopes: a client gets them (and the audience) only when it asks.
  - **Full scope allowed** is off for all demo clients ([deprecated in 26.8](https://github.com/keycloak/keycloak/issues/52923)).
    Each client's role scope lists the roles it needs: `events-portal` gets `events-api` roles, `demo-cli` (stands in for a
    frontend that calls both APIs) gets the `admin` roles of both APIs.
  - Users: **alice** is `admin` in `events-api`. **bob** is `admin` in `notification-service` only.

## Naive vs. Hardened

### events-api (resource server)

| Check                         | naive                                   | hardened                                                         |
|-------------------------------|-----------------------------------------|------------------------------------------------------------------|
| `iss`, signature, `exp`/`nbf` | ✔ (Spring default)                      | ✔                                                                |
| `alg: none`, HS256 confusion  | ✔ rejected (Spring default)             | ✔                                                                |
| `aud`                         | ✘                                       | `audiences: events-api`                                          |
| Token type (payload `typ`)    | ✘ ID tokens accepted                    | `JwtClaimValidator("typ", "Bearer")`                             |
| `exp` required                | ✘ token without `exp` never expires     | `JwtTimestampValidator.setAllowEmptyExpiryClaim(false)`          |
| Roles                         | all realm and client roles → `ROLE_`    | only `resource_access.events-api.roles` → `ROLE_`                |
| Principal                     | `preferred_username` (mutable)          | `sub` (default)                                                  |

Config: [application-hardened.yml](events-api/src/main/resources/application-hardened.yml),
[HardenedJwtConfig](events-api/src/main/java/de/schneefisch/eventsapi/HardenedJwtConfig.java),
[NaiveJwtConfig](events-api/src/main/java/de/schneefisch/eventsapi/NaiveJwtConfig.java).

### notification-service (client credentials)

| Setting            | naive                                              | hardened                                       |
|--------------------|----------------------------------------------------|------------------------------------------------|
| `scope`            | none: token without `events-api` audience          | `events:read`: Keycloak adds `aud: events-api` |
| `audiences` (own API) | none                                            | `notification-service`                         |

Both modes use `AuthorizedClientServiceOAuth2AuthorizedClientManager` (token cached until 60 s before expiry) and read the
client secret from an environment variable.

### events-portal (BFF)

| Setting                   | naive                                          | hardened                                                  |
|---------------------------|------------------------------------------------|-----------------------------------------------------------|
| `state`, `nonce`, PKCE    | ✔ (Spring Security 7 default, PKCE for confidential clients too) | ✔                                       |
| CSRF protection           | ✘ disabled (copied from an API config)         | ✔ on (default)                                            |
| Logout                    | local session only                             | RP-initiated logout + back-channel logout                 |
| Principal                 | `user-name-attribute: preferred_username`      | `sub` (default)                                           |
| Session cookie            | browser defaults                               | `http-only`, `same-site: lax`                             |

## Scripts

Every script prints what it sends and checks the result against the expectation for the current mode (✔/✘).

| Script                                   | Shows                                                                                                   |
|------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `scripts/mode.sh [naive\|hardened] [app]` | Switches all apps or one app and restarts it. Without arguments: the current modes                     |
| `scripts/attack.sh [case]`               | events-api: token for another service, ID token as bearer token, bob's `admin` role from another client |
| `scripts/client-credentials.sh`          | The token notification-service gets, and how events-api answers it                                     |
| `scripts/portal-pkce.sh [show\|enforce\|relax]` | The portal's authorization request (`state`, `nonce`, PKCE), and Keycloak's answer with and without PKCE |
| `scripts/portal-login.sh [alice\|bob]`   | Login through the portal, then the portal deletes an event with the user's token                       |
| `scripts/portal-logout.sh`               | Does logout end the Keycloak session? Does the portal notice an admin logout in Keycloak?             |
| `scripts/show-token.sh [user\|client] [scope]` | Decoded tokens from the demo realm                                                               |
| `scripts/full-scope.sh [on\|off]`        | "Full scope allowed" for `demo-cli`: compare `scripts/show-token.sh bob`, and see Keycloak's deprecation warning in `docker compose logs keycloak` |

Interesting combinations:

```bash
scripts/mode.sh hardened events-api       # API hardened, notification-service still naive
scripts/client-credentials.sh             # 401: the token has no events-api audience
scripts/mode.sh hardened notification-service
scripts/client-credentials.sh             # 200: scope events:read brings aud events-api

scripts/portal-pkce.sh enforce            # Keycloak requires PKCE for events-portal
scripts/portal-pkce.sh                    # Spring Boot 4 sends it; a request without it gets
                                          # "Missing parameter: code_challenge_method"
```

`scripts/portal-login.sh bob` returns 403 in both modes: the portal's role scope holds only `events-api` roles, so Keycloak
leaves bob's `notification-service` role out of the token. `scripts/attack.sh foreign-role` shows the same role leaking through
`demo-cli`, which legitimately needs roles for both APIs. Role scopes in Keycloak and role filtering in Spring complement each other.

## Tests Without Keycloak

`events-api` has tests that sign tokens with a local key and run them through the real `JwtDecoder` Spring Boot builds
([TestTokens](events-api/src/test/java/de/schneefisch/eventsapi/TestTokens.java)):

- [NaiveProfileTest](events-api/src/test/java/de/schneefisch/eventsapi/NaiveProfileTest.java): what the defaults already reject
  (`alg: none`, HS256 signed with the public key, wrong issuer, expired) and what gets through.
- [HardenedProfileTest](events-api/src/test/java/de/schneefisch/eventsapi/HardenedProfileTest.java): every hardened check on its own,
  e.g. an ID token *with* the API's audience is still rejected by the `typ` check.
- [AuthoritiesClaimExpressionsTest](events-api/src/test/java/de/schneefisch/eventsapi/AuthoritiesClaimExpressionsTest.java):
  Spring Boot 4.1's `authorities-claim-expressions` reads nested Keycloak roles, but replaces the scope mapping, and without
  `authority-prefix` the roles become `SCOPE_admin`.

```bash
cd events-api
docker run --rm -v "$PWD":/src -w /src gradle:9.8.0-jdk25 gradle test
```

## Clean Up

```bash
docker compose --profile tools down --rmi local
rm -f .env
```
