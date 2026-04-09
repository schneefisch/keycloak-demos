# Keycloak Demo Projects

Companion code for my technical articles on Keycloak, IAM, OAuth2, and OIDC.

Each directory is a self-contained, runnable demo. Clone the repo, navigate into the demo, and follow its README.

## Demos

| Demo | Description | Article |
|------|-------------|---------|
| [custom-spi-permission-mapper](./custom-spi-permission-mapper) | Keycloak custom protocol mapper that composes permission claims from group attributes using a configurable template | — |

## Quick Start

Every demo includes a `docker-compose.yml`. The general pattern:

```bash
# 1. Build the artifact (for SPI demos)
cd <demo-name>
./gradlew shadowJar

# 2. Start Keycloak with the demo realm pre-configured
docker-compose up

# 3. Follow the demo-specific README for next steps
```

## Tech Stack

- **Keycloak 26.x** (Docker)
- **Java 21** (SPI demos, Gradle + Shadow plugin)
- **JUnit 5** + Mockito (tests)

## Sparse Checkout (clone only one demo)

If you only need a single demo:

```bash
git clone --filter=blob:none --sparse https://github.com/schneefisch/keycloak-demos.git
cd keycloak-demos
git sparse-checkout set custom-spi-permission-mapper
```

## License

MIT — see [LICENSE](./LICENSE)
