# keycloak-demos

Companion code for technical articles on Keycloak, IAM, and OAuth2/OIDC.
Each subdirectory is a self-contained demo project that maps to one article.

## Repo Structure

```
keycloak-demos/
├── custom-spi-permission-mapper/   # Java SPI — template-based permission claims
├── <next-demo>/
└── .github/workflows/ # github ci workflows per demo-project
```

## Adding a New Demo

1. Create a subdirectory named after the article concept (kebab-case, no `demo` suffix)
2. Include a standalone `README.md` with: problem statement, how it works, quick start, and article link
3. Include a `docker-compose.yml` so readers can run it with a single command
4. Include a `realm-export.json` if Keycloak configuration is part of the demo
5. Include a github ci workflow for the demo
6. Update the demo table in the root `README.md`

## Per-Demo Tech Stacks

| Demo | Language | Build |
|------|----------|-------|
| `custom-spi-permission-mapper` | Java 21 | Gradle + Shadow |

## Commands

**Build SPI JAR:** `./gradlew shadowJar` (run inside demo directory)
**Run tests:** `./gradlew test`
**Start Keycloak:** `docker-compose up` (run inside demo directory)
**Format:** no formatter configured — Java only, keep consistent with existing style

## Conventions

- Each demo is fully self-contained: no shared dependencies across demos
- Keycloak SPI deps are always `compileOnly` (provided by the server at runtime)
- `realm-export.json` lives at the demo root and is mounted into Keycloak via `docker-compose.yml`
- Article links go in the demo's `README.md` header once the article is published
- Java is preferred over Kotlin for SPI demos (zero runtime deps, no classloader risk — see `custom-spi-permission-mapper/README.md`)

## Keycloak Version

Target **Keycloak 26.x** unless the demo is explicitly version-specific.
Match the SPI dependency version in `build.gradle` to the Keycloak image tag in `docker-compose.yml`.
