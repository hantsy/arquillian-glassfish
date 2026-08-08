# Restructure GlassFish Client Classes

**Date:** 2026-08-08
**Status:** Approved

## Context

Four classes in `glassfish-common/clientutils/` handle GlassFish admin API interaction, with confusing names and unclear boundaries:

| Current class | Role |
|---------------|------|
| `GlassFishClient` (interface) | Contract for admin API — but has only one implementation |
| `GlassFishClientService` (impl) | Topology discovery, deploy/undeploy logic |
| `GlassFishClientUtil` | HTTP transport + XML parsing |
| `GlassFishClientException` | Exception wrapper |

Additionally, `CommonGlassFishManager` (in `glassfish-common/`) builds multipart HTTP bodies from ShrinkWrap Archives, further splitting HTTP concerns across classes.

## Design

### Merge `GlassFishClient` + `GlassFishClientService` + `CommonGlassFishManager`

The interface is meaningless with a single implementation. The Manager's body-building is half an HTTP operation spread across two classes. Consolidate into one:

| Before | After |
|--------|-------|
| `GlassFishClient` (interface) | **Removed** |
| `GlassFishClientService` | → `GlassFishAdminClient` |
| `CommonGlassFishManager` | **Removed** — body-building and deploy/undeploy flow move into `GlassFishAdminClient` |
| `GlassFishClientUtil` | → `GlassFishHttpClient` |

### `GlassFishAdminClient` — the consolidated class

```java
public class GlassFishAdminClient {
    private final GlassFishHttpClient httpClient;

    public GlassFishAdminClient(CommonGlassFishConfiguration config);

    // Lifecycle (was startUp on GlassFishClient)
    public void start();

    // Deployment (was CommonGlassFishManager.deploy/undeploy)
    public HTTPContext deploy(Archive<?> archive);
    public void undeploy(Archive<?> archive);

    // Health (was GlassFishClient.isDASRunning)
    public boolean isDASRunning();
}
```

Body-building (`MultipartBody` from `Archive`) moves from `CommonGlassFishManager` into `GlassFishAdminClient` — since `deploy(Archive)` takes the archive directly, there's no reason to spread body construction and HTTP POST across two classes.

### Container simplification

```java
// Remote
class GlassFishRestDeployableContainer {
    private GlassFishAdminClient adminClient;
}

// Managed
class GlassFishManagedDeployableContainer {
    private GlassFishServerControl serverControl;   // process lifecycle
    private GlassFishAdminClient adminClient;       // REST admin API
}
```

Clean separation: `GlassFishServerControl` handles the OS process, `GlassFishAdminClient` handles everything REST. No interface, no Manager facade.

### Files

| File | Action |
|------|--------|
| `GlassFishClient.java` | **Remove** |
| `GlassFishClientService.java` → `GlassFishAdminClient.java` | Rename + add body-building from Manager |
| `GlassFishClientUtil.java` → `GlassFishHttpClient.java` | Rename + add `USER_AGENT_VALUE` |
| `CommonGlassFishManager.java` | **Remove** — logic moves into `GlassFishAdminClient` |
| `GlassFishRestDeployableContainer.java` | Update: use `GlassFishAdminClient` directly |
| `GlassFishManagedDeployableContainer.java` | Update: use `GlassFishAdminClient` directly |
| `GlassFishClientException.java` | Keep as-is |
