# Rename GlassFish Client Classes

**Date:** 2026-08-08
**Status:** Approved

## Context

`glassfish-common/clientutils/` contains four classes for GlassFish admin API interaction:

| Current | Responsibility |
|---------|---------------|
| `GlassFishClient` (interface) | Contract for admin API: deploy, undeploy, startUp, health check |
| `GlassFishClientService` | Implements the interface — topology discovery, deployment logic |
| `GlassFishClientUtil` | HTTP transport + XML response parsing |
| `GlassFishClientException` | Exception wrapper |

The naming is confusing: "Service", "Util" are vague suffixes. `GlassFishClientUtil` wraps `java.net.http.HttpClient` but that isn't obvious from the name. `GlassFishClientService` implements `GlassFishClient` and speaks the GlassFish admin REST API — "AdminClient" would be clearer.

## Design

Rename two classes:

| Before | After | Rationale |
|--------|-------|-----------|
| `GlassFishClientUtil` | `GlassFishHttpClient` | Wraps `java.net.http.HttpClient` — name says what it is |
| `GlassFishClientService` | `GlassFishAdminClient` | Implements `GlassFishClient`, speaks the GlassFish *admin* REST API |

Move `USER_AGENT_VALUE` constant from `GlassFishAdminClient` to `GlassFishHttpClient` — it is an HTTP header, belongs in the HTTP transport layer. Removes the upward reference where the lower layer referenced a constant in the higher layer.

## Files modified

| File | Change |
|------|--------|
| `GlassFishClientUtil.java` → `GlassFishHttpClient.java` | Rename class; add `USER_AGENT_VALUE` constant |
| `GlassFishClientService.java` → `GlassFishAdminClient.java` | Rename class; remove `USER_AGENT_VALUE` constant |
| `CommonGlassFishManager.java` | Update import and instantiation |
| `GlassFishClient.java` | No change |

## What stays unchanged

- `GlassFishClient` interface name
- `GlassFishClientException` name
- All method signatures, logic, and behavior
- No new files, no deletions
