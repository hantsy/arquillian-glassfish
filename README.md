# Arquillian GlassFish Container Integration

[![Build Status](https://github.com/hantsy/arquillian-container-glassfish-jakarta/actions/workflows/build.yml/badge.svg)](https://github.com/hantsy/arquillian-container-glassfish-jakarta/actions/workflows/build.yml)

Arquillian container adapters for **GlassFish 7.x and 8.x** with **Jakarta EE 10+** support. This project provides both managed and remote container integration for testing Jakarta EE applications.

> [!NOTE]
> This is a maintained fork of [arquillian-container-glassfish6](https://github.com/arquillian/arquillian-container-glassfish6) (the upstream project is no longer actively maintained).

### What's New in This Fork

This fork brings the original project up to date with:

- Support for GlassFish 7.x/8.x and Jakarta EE 10 APIs
- Java 21+ baseline
- Arquillian Core 1.10.x with JUnit 5
- Native `java.net.http.HttpClient` instead of Jersey
- Published to Maven Central for easy dependency management

>[!IMPORTANT]
> For production use with broader community support and additional features, consider [OmniFish EE Arquillian GlassFish](https://github.com/OmniFish-EE/arquillian-container-glassfish)—an alternative fork built on Payara's ecosystem.


## Quick Start

### Managed Adapter

Use the **managed adapter** when you want Arquillian to automatically control the GlassFish lifecycle (download, configure, start, and stop). This is ideal for CI/CD pipelines and local development.

```xml
<dependency>
    <groupId>io.github.hantsy.arquillian</groupId>
    <artifactId>arquillian-glassfish-managed</artifactId>
    <version>7.0.15</version>
    <scope>test</scope>
</dependency>
```

### Remote Adapter

Use the **remote adapter** to connect to an already-running GlassFish instance via the admin REST API. This is useful for testing against shared servers or production-like environments.

```xml
<dependency>
    <groupId>io.github.hantsy.arquillian</groupId>
    <artifactId>arquillian-glassfish-remote</artifactId>
    <version>7.0.15</version>
    <scope>test</scope>
</dependency>
```

For example Arquillian configurations, see `integration-tests/src/test/resources`.

> [!NOTE]
> Artifacts are published to [Maven Central](https://search.maven.org/artifact/io.github.hantsy.arquillian) starting from v7.0.13. For earlier versions, see [JITPACK.adoc](JITPACK.adoc).

## Build from Source

### Prerequisites

Ensure you have the following software installed:

- **JDK 21** or later
- **Maven 3.9+** (Maven Wrapper `./mvnw` is included)
- Git

### Clone

Get the source code:

```bash
git clone https://github.com/hantsy/arquillian-glassfish-jakarta.git
cd arquillian-glassfish-jakarta
```

### Project Structure

Once you import the source code into your IDE (such as IntelliJ IDEA), you'll see this structure in the Project view:

```
arquillian-container-glassfish-jakarta/
├── common/                           # Shared utilities: REST client, config, XML parsing
├── managed/                          # Managed container adapter (Arquillian controls lifecycle)
├── remote/                           # Remote container adapter (connects to running GlassFish)
├── integration-tests/                # Integration test suite for both adapters
├── pom.xml                           # Parent POM with dependency BOMs
└── .github/copilot-instructions.md   # Detailed architecture and build commands
```

Each module is independently testable. The integration-tests module validates both managed and remote adapters against real GlassFish instances using Arquillian JUnit 5 and ShrinkWrap for dynamic deployments.

### Build

Build the project with Maven:

```bash
# Build all modules (skip tests)
./mvnw clean install -DskipTests

# Run unit tests
./mvnw test

# Run integration tests (managed GlassFish 7 by default)
./mvnw verify -pl integration-tests

# Run integration tests with GlassFish 8
./mvnw verify -pl integration-tests -Pmanaged,v8
```

## Contributing

We welcome contributions! Please [file an issue](https://github.com/hantsy/arquillian-container-glassfish-jakarta/issues) for bug reports and feature requests, or [submit a pull request](https://github.com/hantsy/arquillian-container-glassfish-jakarta/pulls) with your improvements.

## Resources

### Learning Arquillian
- [Jakarta EE 10 Example Codes](https://github.com/hantsy/jakartaee10-sandbox) — Sample projects using this adapter
- [Jakarta EE Starter Boilerplate](https://github.com/hantsy/jakartaee9-starter-boilerplate) — Get started with Arquillian
- [Arquillian Documentation](https://arquillian.org/) — Official documentation

### Related Projects
- [Upstream Arquillian GlassFish](https://github.com/arquillian/arquillian-container-glassfish6) — Original project (no longer maintained)
- [OmniFish EE Arquillian GlassFish](https://github.com/OmniFish-EE/arquillian-container-glassfish) — Community fork with Payara's ecosystem
- [Arquillian Project](https://arquillian.org/)
- [GlassFish Project](https://glassfish.org/)
- [OmniFish EE](https://github.com/OmniFish-EE) — Community-maintained GlassFish ecosystem

