# Step-Up Authentication with Keycloak

ACR-based step-up authentication: require MFA only for sensitive operations while letting low-risk requests through with password-only authentication.

## The Problem

Traditional "always MFA" or "never MFA" approaches don't match the reality of varying risk levels within a single application. Users shouldn't need to enter their TOTP code just to view their profile, but they absolutely should when accessing sensitive data like changing their email or deleting their account.

## How It Works

```
User → Spring Boot Backend → checks ACR claim in JWT

  GET /api/user/profile     →  acr=password  →  ✓ 200 OK
  GET /api/admin/sensitive   →  acr=password  →  ✗ 401 + WWW-Authenticate: acr_values="mfa"

  (Frontend redirects to Keycloak with acr_values=mfa → user enters TOTP)

  GET /api/admin/sensitive   →  acr=mfa       →  ✓ 200 OK
```

The backend uses a custom `@RequireAcr(2)` annotation to enforce ACR levels per endpoint. When the ACR level is insufficient, it returns `401` with a `WWW-Authenticate` header that tells the client which ACR level is needed.

Keycloak maps ACR values to authentication levels via the client's `acr.loa.map`:

| ACR Value  | LoA Level | Authentication Method  |
|------------|-----------|------------------------|
| `password` | 1         | Username + password    |
| `mfa`      | 2         | Username + password + OTP |

## Quick Start

### 1. Start Keycloak

```bash
docker-compose up
```

Keycloak starts on `http://localhost:8080` with the `step-up-demo` realm pre-configured.

- Admin console: `http://localhost:8080/admin` (`admin` / `admin`)
- Test user: `testuser` / `password`

### 2. Configure OTP for the Test User

The step-up flow requires the user to have OTP configured:

1. Open `http://localhost:8080/realms/step-up-demo/account`
2. Log in as `testuser` / `password`
3. Go to **Signing in** → **Two-factor authentication**
4. Click **Set up Authenticator application**
5. Scan the QR code with your authenticator app (Google Authenticator, Authy, etc.)
6. Enter the code to verify

### 3. Start the Spring Boot App

```bash
./gradlew bootRun
```

The app starts on `http://localhost:8081`.

### 4. Test the Step-Up Flow

```bash
bash scripts/test-step-up.sh
```

Or manually:

```bash
# Get a level-1 token (password only)
TOKEN=$(curl -s -X POST "http://localhost:8080/realms/step-up-demo/protocol/openid-connect/token" \
  -d "client_id=step-up-demo-app" \
  -d "username=testuser" \
  -d "password=password" \
  -d "grant_type=password" | jq -r '.access_token')

# This works (any authenticated user)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/user/profile

# This returns 401 (requires ACR level 2)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/admin/sensitive
```

## Endpoints

| Endpoint               | Auth Required | ACR Level | Description                  |
|------------------------|---------------|-----------|------------------------------|
| `GET /api/public/info` | No            | —         | Public, no authentication    |
| `GET /api/user/profile`| Yes           | Any (≥1)  | User profile, basic auth     |
| `GET /api/admin/sensitive` | Yes       | 2 (MFA)   | Sensitive data, step-up      |

## Key Files

| File | Purpose |
|------|---------|
| `acr/RequireAcr.java` | Annotation to mark endpoints requiring a minimum ACR level |
| `acr/AcrAspect.java` | AOP aspect that validates the ACR claim against the annotation |
| `config/SecurityConfig.java` | Spring Security configuration (JWT resource server) |
| `exception/GlobalExceptionHandler.java` | Returns 401 with `WWW-Authenticate` header for step-up |
| `realm-export.json` | Keycloak realm with step-up browser flow and ACR mappings |

## Keycloak Configuration: Pitfalls and Insights

This section documents every configuration mistake made while building this demo and the non-obvious behaviors of Keycloak 26.x that aren't well-covered in the official docs.

### Pitfall: `acr.loa.map` format is the reverse of what you'd expect

The client attribute `acr.loa.map` maps **ACR value names to numeric LoA levels**, not the other way around. The values must be integers.

```
Wrong:  {"1":"password","2":"mfa"}        -- keys are levels, values are names
Wrong:  {"password":"1","mfa":"2"}        -- values are strings, not integers
Right:  {"password":1,"mfa":2}            -- keys are ACR names, values are integer levels
```

Getting this wrong produces a deserialization error during import that is easy to miss:

```
WARN  Invalid client configuration (ACR-LOA map) for client 'step-up-demo-app'.
      Cannot deserialize value of type 'java.lang.Integer' from String "password"
```

Keycloak imports the realm anyway — it just silently ignores the ACR-LoA mapping. Everything appears to work until you try to use `acr_values` and realize the ACR claim in the token isn't what you expected.

### Pitfall: Built-in client scopes are NOT auto-created during `--import-realm`

In Keycloak 26.x, importing a realm with `--import-realm` does **not** auto-create the standard OIDC client scopes (`basic`, `profile`, `email`, `roles`, `web-origins`). If you don't define them explicitly in your `clientScopes` array, they don't exist in the realm.

**Consequence:** tokens are missing fundamental claims like `sub`, `preferred_username`, `email`, and `name`. The token still validates — it's just empty except for `iss`, `exp`, `iat`, and `azp`.

**Solution:** define every scope you need in `clientScopes` with its protocol mappers. This demo defines `acr`, `basic`, `profile`, `email`, and `roles` explicitly. The most critical is `basic`, which provides the `sub` claim via the `oidc-sub-mapper`.

### Pitfall: Referencing undefined scopes silently fails

If a client's `defaultClientScopes` lists a scope name that doesn't exist in the realm, Keycloak logs a warning and drops it:

```
WARN  Referenced client scope 'profile' doesn't exist. Ignoring
```

No error, no import failure. The scope simply isn't linked to the client. This makes it easy to end up with a client that has no scopes and produces near-empty tokens.

### Pitfall: `defaultDefaultClientScopes` overrides ALL realm defaults

Setting `defaultDefaultClientScopes` at the realm level **replaces** the entire default scope list for all clients. If you set `["acr"]`, every client in the realm loses `basic`, `profile`, `email`, etc. Either list every scope you need, or **omit this field entirely** and assign scopes per-client via `defaultClientScopes` on each client object.

### Pitfall: Step-up does not work in the Direct Grant (ROPC) flow

The `conditional-level-of-authentication` authenticator evaluates whether the user's session needs to step up to a higher LoA. In the browser flow, this works correctly: it checks the requested `acr_values` and only triggers OTP when the client requests LoA 2+.

In the direct grant flow, the condition **always evaluates to true**, triggering OTP validation regardless of the `acr_values` parameter. This causes:

```
ERROR  AuthenticationFlowException: authenticator: direct-grant-validate-otp
```

**This is by design.** The direct grant flow has no concept of an existing session with a previously-established LoA. Every direct grant request starts from LoA 0, and the condition `0 < 2` is always true.

**Implication for this demo:** the direct grant flow uses the built-in flow (password only, no conditional OTP). Level-1 tokens come from the direct grant. Level-2 (MFA) tokens require the browser-based authorization code flow with `acr_values=mfa`. This matches the real-world pattern: step-up is a browser redirect, not an API call.

### Pitfall: `requiredActions: ["CONFIGURE_TOTP"]` blocks the direct grant

If the test user has `CONFIGURE_TOTP` as a required action, the direct grant flow cannot complete. Keycloak requires the user to interactively scan a QR code and enter a TOTP code — this requires a browser UI.

**Workaround:** don't set `CONFIGURE_TOTP` as a required action in the realm export. Instead, document the OTP setup as a manual step in the Quick Start (see above).

### Insight: ACR claim values are strings, not numbers

With `acr.loa.map = {"password":1,"mfa":2}`, Keycloak puts the **map key** (the named ACR value) in the `acr` token claim, not the numeric LoA level:

```json
{"acr": "password"}    // not {"acr": 1}
{"acr": "mfa"}         // not {"acr": 2}
```

The backend must maintain its own mapping from these strings to numeric levels. See `AcrAspect.ACR_LEVEL_MAP`. If you forget this, `Integer.parseInt("password")` fails silently and every request appears to have ACR level 0.

### Insight: The `oidc-acr-mapper` must be enabled for access tokens

By default, Keycloak may only include the `acr` claim in ID tokens. For a resource server (which validates access tokens, not ID tokens), the `acr` client scope must have the `oidc-acr-mapper` configured with:

```json
"access.token.claim": "true"
```

Without this, the backend never sees the ACR claim and rejects every request.

### Insight: Authentication flow nesting structure

The step-up browser flow requires three levels of nesting. Getting this wrong produces either "always require OTP" or "never require OTP":

```
step-up browser (top-level, basic-flow)
  ├── auth-cookie (ALTERNATIVE)                    -- SSO session check
  └── step-up browser forms (ALTERNATIVE, sub-flow)
        ├── auth-username-password-form (REQUIRED)  -- always required
        └── step-up conditional otp (CONDITIONAL, sub-flow)
              ├── conditional-level-of-authentication (REQUIRED)  -- the gate
              │     config: loa-condition-level=2, loa-max-age=300
              └── auth-otp-form (REQUIRED)          -- only runs if gate passes
```

Key points:
- The **CONDITIONAL** requirement on the OTP sub-flow makes it conditional
- The `conditional-level-of-authentication` execution inside acts as the **gate** — it determines WHEN the sub-flow runs
- The gate is **REQUIRED** within the conditional flow (it must be evaluated), but the flow itself is **CONDITIONAL** (it may be skipped)

### Insight: `loa-max-age` controls step-up freshness

The `loa-max-age: 300` setting on the LoA condition means the user's MFA authentication "expires" after 300 seconds (5 minutes). After that, even if the session still has an active SSO cookie, Keycloak re-prompts for OTP on the next `acr_values=mfa` request.

This is useful for high-risk operations: even if the user authenticated with MFA 10 minutes ago, you can force re-authentication for critical actions like deleting an account.

## Tech Stack

- **Java 25** / Spring Boot 4.0.5
- **Keycloak 26.5.6** (Docker)
- **Gradle 9.4.1**
- **JUnit 5** + Spring Security Test

## Running Tests

```bash
./gradlew test
```
