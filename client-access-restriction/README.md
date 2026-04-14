# Client Access Restriction with Keycloak Authorization Services

Block users from logging into a third-party app unless they have a specific Keycloak role — using Authorization Services policies evaluated during login via the [keycloak-restrict-client-auth](https://github.com/sventorben/keycloak-restrict-client-auth) SPI.

## The Problem

When you integrate a third-party application with Keycloak via OAuth/OIDC, every user in the realm can log in. Many apps assign a default role (like "Viewer") to anyone who authenticates, regardless of whether they should have access at all.

This is a security problem: if 500 employees share a Keycloak realm but only 20 should access Grafana, the other 480 can still sign in and see dashboards.

The app itself can't help — it delegates authentication to Keycloak and trusts whatever token it receives. You need Keycloak to decide **before issuing a token** whether the user should have access at all.

## How It Works

```
Alice (platform-team) → Keycloak login → policy: ✓ has grafana-access → token issued → Grafana
Bob   (marketing)     → Keycloak login → policy: ✗ no grafana-access  → access denied → never reaches Grafana
```

Keycloak's **Authorization Services** let you define per-client access policies (roles, groups, custom logic). By themselves, these policies only evaluate during UMA ticket grants — not during standard OIDC login. The [keycloak-restrict-client-auth](https://github.com/sventorben/keycloak-restrict-client-auth) SPI bridges this gap: it evaluates the client's Authorization Services policies **during the authentication flow**, blocking unauthorized users before a token is issued.

The key advantage: **one authentication flow for all clients**. Per-client access control is configured entirely through Authorization Services on each client — no per-app authentication flows needed.

### Architecture

```
                    ┌─────────────────────────────────────────────┐
                    │  ONE custom authentication flow (realm-wide) │
                    │                                             │
                    │  1. Username/Password → authenticate        │
                    │  2. restrict-client-auth SPI → evaluate     │
                    │     Authorization Services policies on the  │
                    │     target client                           │
                    └─────────────────────────────────────────────┘
                              ↓                        ↓
              ┌───────────────────────┐  ┌───────────────────────┐
              │  Grafana client       │  │  Other app client     │
              │  ┌─────────────────┐  │  │  (no restriction)     │
              │  │ Keycloak Client │  │  │                       │
              │  │ Resource        │  │  │  No "Keycloak Client  │
              │  │ + role policy   │  │  │  Resource" → everyone │
              │  │ + permission    │  │  │  can log in           │
              │  └─────────────────┘  │  │                       │
              └───────────────────────┘  └───────────────────────┘
```

Clients **with** a resource named `Keycloak Client Resource` are restricted — only users whose policies evaluate to PERMIT can log in. Clients **without** this resource are unrestricted.

## Quick Start

### 1. Download the SPI Extension

```bash
bash scripts/download-spi.sh
```

This downloads the [keycloak-restrict-client-auth](https://github.com/sventorben/keycloak-restrict-client-auth) JAR (v26.1.0, MIT license).

### 2. Start the Stack

```bash
docker-compose up
```

This starts:
- **Keycloak** on `http://localhost:8080` with the `app-access-demo` realm pre-configured
- **Grafana** on `http://localhost:3000` with OAuth pointing to Keycloak

Admin console: `http://localhost:8080/admin` (`admin` / `admin`)

### 3. Test in the Browser

Open `http://localhost:3000` and click **Sign in with Keycloak**.

| User    | Password   | Group           | Has `grafana-access` | Expected Result                    |
|---------|------------|-----------------|----------------------|------------------------------------|
| `alice` | `password` | `platform-team` | Yes (via group)      | Logs into Grafana as Viewer        |
| `bob`   | `password` | —               | No                   | Blocked by Keycloak (access denied)|

### 4. Run the Automated Test

```bash
bash scripts/test-access.sh
```

## Key Files

| File                              | Purpose                                                                   |
|-----------------------------------|---------------------------------------------------------------------------|
| `realm-export.json`               | Keycloak realm with auth flows, Authorization Services, roles, groups     |
| `docker-compose.yml`              | Keycloak + PostgreSQL + Grafana with OAuth and SPI JAR mounted            |
| `keycloak-restrict-client-auth.jar` | SPI extension (downloaded via `scripts/download-spi.sh`, not committed) |
| `scripts/download-spi.sh`        | Downloads the SPI JAR from GitHub                                         |
| `scripts/test-access.sh`         | Automated test verifying access control                                   |

## Keycloak Configuration: Step by Step

The `realm-export.json` pre-configures everything below.

### Step 1 — Install the SPI Extension

Mount `keycloak-restrict-client-auth.jar` into `/opt/keycloak/providers/`. In dev mode, Keycloak auto-discovers it on startup.

### Step 2 — Create the Authentication Flow

Create ONE custom browser flow that applies to all clients:

```
restricted-browser
├── Cookie (ALTERNATIVE)                                    ← SSO session check
└── restricted-browser-forms (ALTERNATIVE, sub-flow)
    ├── Username Password Form (REQUIRED)                    ← authenticate the user
    └── Restrict user authentication on clients (REQUIRED)   ← evaluate Authorization Services
          config: accessProviderId = policy
```

Set this as the realm's browser flow. Optionally create a matching direct grant flow.

The `policy` mode evaluates Authorization Services policies on the **target client** for each login attempt. Clients without Authorization Services configuration are unrestricted.

### Step 3 — Create a Realm Role

**Realm roles → Create role:**
- Role name: `grafana-access`

### Step 4 — Create a Group and Assign the Role

**Groups → Create group:** `platform-team`

**Groups → platform-team → Role mapping → Assign role:** `grafana-access`

### Step 5 — Enable Authorization Services on the Client

**Clients → grafana → Settings → Authorization: ON → Save**

### Step 6 — Create the `Keycloak Client Resource`

**Authorization tab → Resources → Create resource:**
- Name: `Keycloak Client Resource`

This is the magic name the SPI looks for. If a client has a resource with this exact name, it's restricted. If not, it's open.

### Step 7 — Create a Role Policy

**Authorization tab → Policies → Create policy → Role:**
- Name: `grafana-access-only`
- Realm roles: `grafana-access`
- Logic: Positive

### Step 8 — Create a Resource-Based Permission

**Authorization tab → Permissions → Create permission → Resource-based:**
- Name: `grafana-access-permission`
- Resources: `Keycloak Client Resource`
- Policies: `grafana-access-only`
- Decision strategy: Unanimous

### Result

Users with `grafana-access` (directly or via group) → token issued → Grafana. Everyone else → "Access denied" at Keycloak.

## Adding Another Restricted App

Repeat Steps 5-8 for the new client:

1. Enable Authorization Services on the client
2. Create a `Keycloak Client Resource`
3. Create a role policy for the app-specific role (e.g., `jira-access`)
4. Create a permission linking the resource to the policy
5. Assign the role to the appropriate group

The authentication flow doesn't change — it's the same ONE flow for all clients.

## Pitfalls

### Authorization Services alone do NOT block OIDC login

Without the SPI, Authorization Services only evaluate during UMA ticket grants. Users still receive valid tokens through the standard OIDC authorization code and direct grant flows. The `keycloak-restrict-client-auth` SPI bridges this gap by evaluating policies during authentication.

### The resource must be named exactly `Keycloak Client Resource`

The SPI looks for this exact string. A differently named resource won't trigger policy evaluation.

### Remove or replace the Default Permission

When enabling Authorization Services, Keycloak may create a `Default Permission` with a `Default Policy` that always grants access. This default overrides your restrictions. Remove it or ensure it doesn't apply to `Keycloak Client Resource`.

### Built-in client scopes are not auto-created during `--import-realm`

In Keycloak 26.x, `--import-realm` does not create the standard OIDC scopes. The `realm-export.json` defines them explicitly.

## Alternative: Role-Based Mode (Simpler)

The SPI also supports a `client-role` mode that doesn't need Authorization Services:

1. Set `accessProviderId = client-role` in the authenticator config
2. For each restricted client, create a **client role** named `restricted-access`
3. Assign that client role to users/groups who should have access

This is simpler but less flexible — Authorization Services support group policies, time-based policies, JavaScript policies, and more.

## Tech Stack

- **Keycloak 26.5.6** (Docker)
- **keycloak-restrict-client-auth** v26.1.0 ([GitHub](https://github.com/sventorben/keycloak-restrict-client-auth), MIT license)
- **Grafana OSS 11.6.0** (Docker)
- **PostgreSQL 16** (Docker)
