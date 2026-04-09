# Keycloak Template Permission Mapper — Custom SPI Demo

A Keycloak custom protocol mapper that composes permission claims at **token time** by resolving `${placeholder}` variables in a
configurable template against group attributes.

## The Problem

When managing MQTT permissions for many customers, hard-coding permission scopes on each client is error-prone and doesn't scale. Instead,
store only a `customer_id` as a group attribute and let this mapper dynamically compose the full permission string when a token is issued.

**Before (static):** Each client gets manually configured claims like `mqtt/customers/abc123/telemetry/#`

**After (dynamic):** A single mapper template `mqtt/customers/${customer_id}/telemetry/#` resolves the `customer_id` from the user's group
attributes at token time.

## How It Works

1. An admin creates a **client scope** (e.g. `custom-group-mapper`) with the Template Permission Mapper configured
2. The client scope is assigned as a **default scope** to any client that needs the permissions
3. The client's **service account** is added to the relevant group(s)
4. When a token is requested via `client_credentials` grant, the mapper iterates over the service account's groups
5. For each group that has the configured attribute, it resolves all `${...}` placeholders from group attributes
6. The resulting permission strings are added to the token as a JSON array claim

## Configuration

| Property               | Description                                     | Example                                     |
|------------------------|-------------------------------------------------|---------------------------------------------|
| `permission.template`  | Template with `${...}` placeholders             | `mqtt/customers/${customer_id}/telemetry/#` |
| `group.attribute.name` | Group attribute that identifies relevant groups | `customer_id`                               |
| `claim.name`           | Name of the token claim                         | `permissions`                               |

## Quick Start

### 1. Build the SPI JAR

```bash
./gradlew shadowJar
```

The JAR is created at `build/libs/kc-custom-spi-demo-1.0-SNAPSHOT.jar`.

### 2. Start Keycloak with the SPI

```bash
docker-compose up
```

This starts Keycloak 26.x with PostgreSQL and imports a demo realm (`demo`) that includes:

- Two groups: `customer-a` (`customer_id=abc123`) and `customer-b` (`customer_id=def456`)
- A client scope `custom-group-mapper` with the Template Permission Mapper pre-configured
- A client `mqtt-client` (client credentials grant) with the scope assigned
- The `mqtt-client` service account assigned to `customer-a`

### 3. Get a Token and Verify the Claim

```bash
# Request a token using client credentials
TOKEN=$(curl -s -X POST "http://localhost:8080/realms/demo/protocol/openid-connect/token" \
  -d "client_id=mqtt-client" \
  -d "client_secret=mqtt-client-secret" \
  -d "grant_type=client_credentials" | jq -r '.access_token')

# Decode and inspect the token claims
echo "$TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null | jq '.permissions'
```

Expected output:

```json
[
  "mqtt/customers/abc123/telemetry/#"
]
```

## Integrating with an Existing Keycloak

### Deploy the JAR

Copy the built JAR into your Keycloak's `providers` directory:

```bash
cp build/libs/kc-custom-spi-demo-1.0-SNAPSHOT.jar /opt/keycloak/providers/
```

If running in Docker, add a volume mount:

```yaml
volumes:
  - ./build/libs/kc-custom-spi-demo-1.0-SNAPSHOT.jar:/opt/keycloak/providers/kc-custom-spi-demo.jar:ro
```

Restart Keycloak — it will discover and register the SPI automatically.

### Configure via Admin Console

1. Go to **Client scopes** → **Create client scope**
2. Name it (e.g. `custom-group-mapper`), set protocol to `openid-connect`
3. Go to the scope's **Mappers** tab → **Configure a new mapper** → select **Template Permission Mapper**
4. Fill in the configuration properties (see table above)
5. Go to **Clients** → select your client → **Client scopes** → add the new scope as a **Default** scope
6. Add the client's service account user to the relevant group(s)

## Running Tests

```bash
./gradlew test
```

## Design Decision: Java over Kotlin

This SPI is implemented in plain Java rather than Kotlin. The reasons:

1. **Zero runtime dependencies.** A Java SPI JAR contains only the mapper class and the service descriptor — nothing else to bundle. The
   Kotlin version required the entire kotlin-stdlib (~1.7 MB) for a ~100-line mapper, inflating the JAR by 400x.

2. **No classloader risk.** Keycloak runs on Quarkus with its own classloading hierarchy. Bundling kotlin-stdlib into a provider JAR creates
   a surface for classloader conflicts — we hit `ClassNotFoundException: kotlin.text.Regex` in practice. With Java, every dependency is
   either `compileOnly` (provided by Keycloak) or part of the JDK. Nothing to conflict.

3. **Ecosystem alignment.** Keycloak SPIs are documented and exemplified in Java. Staying in Java means the official docs, Stack Overflow
   answers, and community examples apply directly without translation.

4. **Marginal expressiveness gain.** For a mapper this simple (`Pattern.compile`, a stream pipeline, a `while` loop), Kotlin's syntactic
   advantages don't materially reduce complexity or improve readability.

For more complex Keycloak extensions (coroutines, sealed hierarchies, DSLs), Kotlin may be worth the tradeoffs. For a single protocol
mapper, Java is the lower-risk choice.

## SPI Imports Reference

The import list for a custom OIDC mapper is short but every entry is load-bearing. The official Keycloak docs scatter these across
multiple pages — here's the condensed reference.

### Base class and marker interfaces — `org.keycloak.protocol.oidc.mappers`

- `AbstractOIDCProtocolMapper` — base class for all OIDC mappers. Handles the SPI contract boilerplate (display metadata, config
  property wiring) and exposes `setClaim()` as the single method to override. Your logic goes here and nowhere else.
- `OIDCAccessTokenMapper` — marker interface. Tells Keycloak this mapper is eligible to run during access token generation. Without it,
  the mapper appears in the Admin UI but is silently skipped for access tokens.
- `OIDCIDTokenMapper` — same, for ID token generation.
- `UserInfoTokenMapper` — same, for the `/userinfo` endpoint response. Add or omit these three interfaces to control exactly which token
  types the mapper runs for.

### Models — `org.keycloak.models`

- `UserSessionModel` — represents the active user session. The entry point to everything about the authenticated user:
  `userSession.getUser()` returns the `UserModel`, from which you get group memberships, attributes, and roles.
- `GroupModel` — represents a single Keycloak group. Exposes `getFirstAttribute(name)` and `getAttributeStream(name)` for reading
  group-level attributes — the source of the `customer_id` values in this mapper.
- `ProtocolMapperModel` — the mapper's own configuration as stored in Keycloak. `mappingModel.getConfig()` returns a
  `Map<String, String>` of the values the admin entered in the UI (your template, attribute name, claim name).
- `KeycloakSession` — the full Keycloak session context. Gives access to realm data, user storage, other providers. Not needed for this
  mapper but required by the `setClaim()` signature.
- `ClientSessionContext` — context for the specific client request being serviced. Useful for mappers that need to vary output by client.
  Not used here.

### Other

- `ProviderConfigProperty` — describes one admin-configurable field on the mapper. Set `name`, `label`, `helpText`, `type`, and
  optionally `defaultValue`. These drive the form rendered in **Client Scopes > Mapper > config**.
- `IDToken` — the token object being constructed. `token.getOtherClaims()` is the map where custom claims are written. The same object
  is used for access tokens, ID tokens, and userinfo responses — `AbstractOIDCProtocolMapper` routes the call correctly based on which
  marker interfaces you implemented.

### Where to find more

- [Keycloak Server Developer Guide — Protocol Mappers](https://www.keycloak.org/docs/latest/server_development/index.html#_protocol-mappers) — the canonical reference for the SPI contract
- [keycloak/keycloak GitHub](https://github.com/keycloak/keycloak) — source of truth when docs lag; search `AbstractOIDCProtocolMapper`
  to see how built-in mappers use the same interfaces
- Maven coordinates for the SPI JARs: `org.keycloak:keycloak-server-spi`, `org.keycloak:keycloak-server-spi-private`,
  `org.keycloak:keycloak-services` — all `provided` scope, version must match your KC deployment exactly

## Tech Stack

- **Java 21** + Gradle with Shadow plugin
- **Keycloak 26.x** SPI (`AbstractOIDCProtocolMapper`)
- **JUnit 5** + Mockito for testing
