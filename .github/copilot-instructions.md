# Copilot Instructions for Arquillian GlassFish Container

This is a Jakarta EE testing framework that provides Arquillian container adapters for GlassFish 7.x/8.x. It's a personal fork of the upstream [arquillian-container-glassfish6](https://github.com/arquillian/arquillian-container-glassfish6) project, which is no longer actively maintained.

## Build & Test Commands

### Prerequisites
- **JDK 21+** (Java 21 is the baseline)
- **Maven 3.9+** (Maven Wrapper `./mvnw` included)

### Build & Run Tests

```bash
# Build all modules (skip tests)
./mvnw clean install -DskipTests

# Run unit tests for a single module
./mvnw test -pl common
./mvnw test -pl managed
./mvnw test -pl remote

# Run integration tests with GlassFish 7 managed (default)
./mvnw verify -pl integration-tests

# Run integration tests with GlassFish 8 managed
./mvnw verify -pl integration-tests -Pmanaged,v8

# Run integration tests with GlassFish 7 remote container
./mvnw verify -pl integration-tests -Premote

# Run integration tests with GlassFish 8 remote container
./mvnw verify -pl integration-tests -Premote,v8

# Run integration tests with GlassFish 8 + Derby database
./mvnw verify -pl integration-tests -Pmanaged,enableDerby,v8

# Run only one integration test class
./mvnw verify -pl integration-tests -Pmanaged,v8 -Dtest=TestClassName
```

## Architecture

The project has four Maven modules:

### 1. **common** (`arquillian-glassfish-common`)
- Shared utilities and configuration for both managed and remote containers
- **Key classes:**
  - `GlassFishContainerConfiguration` — Base configuration with properties like `adminHost`, `adminPort`, `adminHttps`, `adminUser`, `adminPassword`
  - `GlassFishAdminClient` — REST API client for GlassFish admin interface
  - `GlassFishRestClient` — HTTP client wrapper (uses `java.net.http.HttpClient`)
  - `GlassFishXMLParser` — Parses GlassFish XML configuration responses
  - `MultipartBodyPublisher` — Handles multipart/form-data for file deployments

### 2. **managed** (`arquillian-glassfish-managed`)
- Managed container adapter — Arquillian controls GlassFish lifecycle
- **Key classes:**
  - `ManagedDeployableContainer` — Implements `DeployableContainer` SPI
  - `ManagedServerControl` — Starts/stops GlassFish process
  - `ManagedContainerConfiguration` — Extends base config with managed-specific properties like `glassfishHome`, `serverArgs`
- Uses Arquillian Servlet protocol and test enrichers (CDI, EJB, resource injection)

### 3. **remote** (`arquillian-glassfish-remote`)
- Remote container adapter — Connects to a pre-running GlassFish instance via admin REST API
- Similar structure to managed, but no server lifecycle control
- Best for testing against shared/production-like environments

### 4. **integration-tests** (`arquillian-glassfish-tests`)
- Integration test suite for both managed and remote adapters
- Uses Arquillian JUnit 5 container integration
- Deployable tests via ShrinkWrap (creates WAR/JAR archives dynamically)
- Uses Apache Cargo Maven plugin for remote container lifecycle management

## Key Conventions

### Maven Multi-Module Structure
- Parent POM at root manages versions and dependency BOMs
- Dependencies use Jakarta EE 10 APIs (not javax.* packages)
- Arquillian 1.10.x, JUnit 5, Mockito 5.x across all test scopes

### Test Profiles
- **managed** (default) — Managed container with GlassFish 7.1.1
- **remote** — Remote container (requires Cargo to start GlassFish)
- **v8** — Overrides GlassFish version to 8.0.4
- **enableDerby** — Adds Derby database to managed container tests

Use profile combinations: `-Pmanaged,v8` or `-Premote,v8`

### Test Organization
- `*Test.java` naming convention in `src/test/java/`
- Integration tests (in `integration-tests`) run via Failsafe plugin, not Surefire
- Server logs on test failure uploaded to CI artifacts (see workflows)

### REST API Client
- Replaced Jersey REST client with `java.net.http.HttpClient` (Java 11+ native HTTP client)
- Admin REST API operations use multipart form-data for deployments
- GlassFish admin user/password required for remote operations

### Configuration Properties
- System properties take precedence over defaults
- Common properties (prefix `glassfish.*`):
  - `glassfish.adminHost` (default: localhost)
  - `glassfish.adminPort` (default: 4848)
  - `glassfish.adminHttps` (default: false)
  - `glassfish.adminUser` (optional)
  - `glassfish.adminPassword` (optional)

### GitHub Actions Workflows
- **build.yml** — Compiles all modules, runs unit tests in common
- **integration-tests.yml** — Runs full integration test matrix (GF7/8 × managed/remote)
- **maven-publish.yml** — Publishes releases to Maven Central
- Builds are triggered by changes to source modules or workflow files

## Important Notes

- This is a **personal fork** for maintaining GlassFish 7.x/8.x support with Jakarta EE 10+
- For production use with OmniFish EE contributions, use [OmniFish EE Arquillian GlassFish](https://github.com/OmniFish-EE/arquillian-container-glassfish)
- Upstream project (arquillian-container-glassfish6) is inactive
- Artifacts published to Maven Central under `io.github.hantsy.arquillian` group ID
