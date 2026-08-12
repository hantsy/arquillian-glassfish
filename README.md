# Arquillian GlassFish Container Integration

[![Build Status](https://github.com/hantsy/arquillian-container-glassfish-jakarta/actions/workflows/build.yml/badge.svg)](https://github.com/hantsy/arquillian-container-glassfish-jakarta/actions/workflows/build.yml)

> [!NOTE]
> This is a personal fork of the official [arquillian-container-glassfish6](https://github.com/arquillian/arquillian-container-glassfish6). For production use, consider [OmniFish EE Arquillian GlassFish](https://github.com/OmniFish-EE/arquillian-container-glassfish) which includes contributions from the OmniFish community.

The upstream project is inactive. This fork updates it to GlassFish 7.x/8.x and Jakarta EE 10+.

## Changes from upstream

- Upgraded to GlassFish 7.x/8.x and Jakarta EE 10 APIs
- Renamed project group ID for Maven Central publishing
- Updated build baseline to Java 21
- Migrated tests to Arquillian Core 1.10.x and JUnit 5
- Replaced Jersey REST client with `java.net.http.HttpClient`

## Usage

Artifacts are published to Maven Central since version 7.0.13. For earlier versions, see [JITPACK.adoc](JITPACK.adoc).

This project provides two container adapters for Arquillian:

- **Managed Container** — Arquillian controls the GlassFish lifecycle. It downloads, configures, starts, and stops GlassFish automatically. Best for CI and local development where you want full automation.
- **Remote Container** — Connects to an already-running GlassFish instance via the admin REST API. Use this when you want to manage GlassFish yourself or test against a shared server.

### Managed Container

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.hantsy.arquillian</groupId>
    <artifactId>arquillian-glassfish-managed</artifactId>
    <version>${arquillian-glassfish.version}</version>
    <scope>test</scope>
</dependency>
```

### Remote Container

Add the following dependency to connect to a running GlassFish instance:

```xml
<dependency>
    <groupId>io.github.hantsy.arquillian</groupId>
    <artifactId>arquillian-glassfish-remote</artifactId>
    <version>${arquillian-glassfish.version}</version>
    <scope>test</scope>
</dependency>
```

For Arquillian configuration examples, see [Jakarta EE 10 Example Codes](https://github.com/hantsy/jakartaee10-sandbox). If you are new to Arquillian, check out the [Jakarta EE Starter Boilerplate](https://github.com/hantsy/jakartaee9-starter-boilerplate).

## Build

### Prerequisites

- **JDK 21** or later
- **Maven 3.9+** (the project includes Maven Wrapper, so you can use `mvnw` instead)
- Git

### Clone and Build

```bash
git clone https://github.com/hantsy/arquillian-container-glassfish-jakarta.git
cd arquillian-container-glassfish-jakarta

# Build all modules (skip tests)
./mvnw clean install -DskipTests

# Run unit tests
./mvnw test

# Run integration tests against a managed GlassFish v8 server
./mvnw verify -pl integration-tests -Pmanaged,v8

# Run integration tests (requires a running GlassFish v8 server)
./mvnw verify -pl integration-tests -Premote,v8
```
