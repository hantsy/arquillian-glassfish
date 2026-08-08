# Restructure GlassFish Client Classes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge GlassFishClient (interface) + GlassFishClientService + CommonGlassFishManager into a single GlassFishAdminClient, rename GlassFishClientUtil to GlassFishHttpClient, remove meaningless abstraction layers.

**Architecture:** After restructuring, GlassFishAdminClient is the single entry point for GlassFish admin REST API (startup, deploy, undeploy, health). GlassFishHttpClient handles low-level HTTP transport. Both containers (managed, remote) use GlassFishAdminClient directly — no interface, no Manager facade.

**Tech Stack:** Java 21, Maven multi-module (glassfish-common, glassfish-managed, glassfish-remote, integration-tests), java.net.http.HttpClient

## Global Constraints

- Java 21 (`maven.compiler.release=21`)
- No Jersey/jakarta.ws.rs dependencies (already removed)
- Compile target: `./mvnw -B compile -DskipTests` must pass
- Branch: `refactor/replace-jersey-client`

---

### Task 1: Rename GlassFishClientUtil → GlassFishHttpClient + move USER_AGENT_VALUE

**Files:**
- Rename: `glassfish-common/src/main/java/org/jboss/arquillian/container/glassfish/clientutils/GlassFishClientUtil.java` → `GlassFishClientUtil.java — to be deleted`
- Create: `glassfish-common/src/main/java/org/jboss/arquillian/container/glassfish/clientutils/GlassFishHttpClient.java`

**Interfaces:**
- Consumes: `GlassFishClientService.USER_AGENT_VALUE` (static field)
- Produces: `GlassFishHttpClient` class — same methods as GlassFishClientUtil plus static `USER_AGENT_VALUE` field

- [ ] **Step 1: Copy GlassFishClientUtil.java to GlassFishHttpClient.java**

```bash
cp glassfish-common/src/main/java/org/jboss/arquillian/container/glassfish/clientutils/GlassFishClientUtil.java \
   glassfish-common/src/main/java/org/jboss/arquillian/container/glassfish/clientutils/GlassFishHttpClient.java
```

- [ ] **Step 2: Edit GlassFishHttpClient.java — rename class**

Change the class declaration:
```java
// Before:
public class GlassFishClientUtil {

// After:
public class GlassFishHttpClient {
```

- [ ] **Step 3: Edit GlassFishHttpClient.java — add USER_AGENT_VALUE constant**

Add after `CSRF_HEADER`/`CSRF_VALUE` constants:
```java
private static final String USER_AGENT_HEADER = "User-Agent";
public static final String USER_AGENT_VALUE = "arquillian-glassfish-managed-jakarta";
```

- [ ] **Step 4: Edit GlassFishHttpClient.java — update self-references in USER_AGENT header**

Change:
```java
.header(USER_AGENT_HEADER, GlassFishClientService.USER_AGENT_VALUE)
```
to:
```java
.header(USER_AGENT_HEADER, USER_AGENT_VALUE)
```
(Two occurrences: `newGetBuilder()` and `newPostBuilder()`)

- [ ] **Step 5: Delete old GlassFishClientUtil.java**

```bash
rm glassfish-common/src/main/java/org/jboss/arquillian/container/glassfish/clientutils/GlassFishClientUtil.java
```

- [ ] **Step 6: Update GlassFishClientService.java (temporary, will be replaced in Task 2)**

Change import and all references:
```java
// Import: no import needed — both are in same package

// All occurrences of GlassFishClientUtil → GlassFishHttpClient
// - Field declaration: private GlassFishHttpClient clientUtil;
// - Constructor: this.clientUtil = new GlassFishHttpClient(configuration, adminBaseUrl);
// - Getter return type: private GlassFishHttpClient getClientUtil() { return clientUtil; }
```

Also remove the `USER_AGENT_VALUE` field from this class (moved to GlassFishHttpClient):
```java
// Remove: public static final String USER_AGENT_VALUE = "arquillian-glassfish-managed-jakarta";
```

- [ ] **Step 7: Update CommonGlassFishManager.java (temporary, will be replaced in Task 2)**

No changes needed — CommonGlassFishManager uses GlassFishClient (interface) and GlassFishClientService (impl) but doesn't reference GlassFishClientUtil directly.

- [ ] **Step 8: Verify compile**

```bash
./mvnw -B compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "refactor: rename GlassFishClientUtil to GlassFishHttpClient, move USER_AGENT_VALUE"
```

---

### Task 2: Create GlassFishAdminClient by merging GlassFishClient + GlassFishClientService + CommonGlassFishManager

**Files:**
- Create: `glassfish-common/src/main/java/org/jboss/arquillian/container/glassfish/clientutils/GlassFishAdminClient.java`
- Remove: `glassfish-common/src/main/java/org/jboss/arquillian/container/glassfish/clientutils/GlassFishClient.java`
- Remove: `glassfish-common/src/main/java/org/jboss/arquillian/container/glassfish/common/CommonGlassFishManager.java` (note: this is in `glassfish` package, not `clientutils`)

**Interfaces:**
- Consumes: `GlassFishHttpClient`, `CommonGlassFishConfiguration`, `GlassFishClientException`, `MultipartBody`, `NodeAddress`, `HTTPContext`, `Archive`
- Produces: `GlassFishAdminClient` with methods: `start()`, `deploy(Archive)`, `undeploy(Archive)`, `isDASRunning()`

- [ ] **Step 1: Read the three source files to understand exactly what to merge**

Read these files in full:
- `glassfish-common/src/main/java/org/jboss/arquillian/container/glassfish/clientutils/GlassFishClient.java`
- `glassfish-common/src/main/java/org/jboss/arquillian/container/glassfish/clientutils/GlassFishClientService.java`
- `glassfish-common/src/main/java/org/jboss/arquillian/container/glassfish/CommonGlassFishManager.java`

- [ ] **Step 2: Create GlassFishAdminClient.java**

**Package:** `org.jboss.arquillian.container.glassfish.clientutils`

Start with a copy of `GlassFishClientService.java` as the base, then:

1. **Class declaration:** Change from `implements GlassFishClient` to standalone:
```java
public class GlassFishAdminClient {
```

2. **Add imports** needed from CommonGlassFishManager:
```java
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import java.io.IOException;
import java.io.InputStream;
```

3. **Add fields and constants** from CommonGlassFishManager:
```java
private static final String DELETE_OPERATION = "__deleteoperation";
private String deploymentName;
```

4. **Add deploy(Archive) method** — ported from CommonGlassFishManager:
```java
public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
    if (archive == null) {
        throw new IllegalArgumentException("archive must not be null");
    }
    final String archiveName = archive.getName();
    final ProtocolMetaData protocolMetaData = new ProtocolMetaData();
    try {
        InputStream deployment = archive.as(ZipExporter.class).exportAsInputStream();
        deploymentName = createDeploymentName(archiveName);
        MultipartBody.Builder builder = MultipartBody.newBuilder()
            .addFilePart("id", archiveName, deployment);
        addDeployFormFields(deploymentName, builder);
        final MultipartBody form = builder.build();
        HTTPContext httpContext = doDeploy(deploymentName, form);
        protocolMetaData.addContext(httpContext);
    } catch (GlassFishClientException | IOException e) {
        throw new DeploymentException("Could not deploy " + archiveName, e);
    }
    return protocolMetaData;
}
```

5. **Add undeploy(Archive) method** — ported from CommonGlassFishManager:
```java
public void undeploy(Archive<?> archive) throws DeploymentException {
    if (archive == null) {
        throw new IllegalArgumentException("archive must not be null");
    }
    deploymentName = createDeploymentName(archive.getName());
    try {
        final MultipartBody form = MultipartBody.newBuilder()
            .addField("target", getConfiguration().getTarget(), "text/plain")
            .addField("operation", DELETE_OPERATION, "text/plain")
            .build();
        doUndeploy(this.deploymentName, form);
    } catch (GlassFishClientException | IOException e) {
        throw new DeploymentException("Could not undeploy " + archive.getName(), e);
    }
}
```

6. **Add helper methods** from CommonGlassFishManager:
```java
private String createDeploymentName(String archiveName) {
    String correctedName = archiveName;
    if (correctedName.startsWith("/")) {
        correctedName = correctedName.substring(1);
    }
    if (correctedName.contains(".")) {
        correctedName = correctedName.substring(0, correctedName.lastIndexOf("."));
    }
    return correctedName;
}

private void addDeployFormFields(String name, MultipartBody.Builder builder) {
    builder.addField("name", name, "text/plain");
    builder.addField("target", getConfiguration().getTarget(), "text/plain");
    if (getConfiguration().getLibraries() != null) {
        builder.addField("libraries", getConfiguration().getLibraries(), "text/plain");
    }
    if (getConfiguration().getProperties() != null) {
        builder.addField("properties", getConfiguration().getProperties(), "text/plain");
    }
    if (getConfiguration().getType() != null && "osgi".equals(getConfiguration().getType())) {
        builder.addField("type", getConfiguration().getType(), "text/plain");
    }
}
```

7. **Change method visibility** — `doDeploy` and `doUndeploy` become private:
```java
// Before: public HTTPContext doDeploy(String name, MultipartBody form)
// After:  private HTTPContext doDeploy(String name, MultipartBody form)

// Before: public Map<String, Object> doUndeploy(String name, MultipartBody form)
// After:  private Map<String, Object> doUndeploy(String name, MultipartBody form)
```

8. **Rename startUp() → start()** and keep the LifecycleException wrapping that was in CommonGlassFishManager:
```java
// Before (in Manager): public void start() calls glassFishClient.startUp() and wraps GlassFishClientException → LifecycleException
// After (directly in GlassFishAdminClient):
public void start() throws LifecycleException {
    try {
        // ... existing startUp() body (topology discovery) ...
    } catch (GlassFishClientException e) {
        log.log(Level.SEVERE, "Startup failure", e);
        throw new LifecycleException(e.getMessage());
    }
}
```
Add import: `import org.jboss.arquillian.container.spi.client.container.LifecycleException;`
Remove the old `startUp()` method entirely — `start()` replaces it.

9. **Update all GlassFishHttpClient references** — the field and getter:
```java
// The field was already updated to GlassFishHttpClient in Task 1
```

10. **Remove the GlassFishClient interface reference** — no `implements` clause

- [ ] **Step 3: Verify compile**

```bash
./mvnw -B compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: merge GlassFishClient + GlassFishClientService + CommonGlassFishManager into GlassFishAdminClient"
```

---

### Task 3: Update container classes to use GlassFishAdminClient directly

**Files:**
- Modify: `glassfish-remote/src/main/java/org/jboss/arquillian/container/glassfish/remote/GlassFishRestDeployableContainer.java`
- Modify: `glassfish-managed/src/main/java/org/jboss/arquillian/container/glassfish/managed/GlassFishManagedDeployableContainer.java`

- [ ] **Step 1: Update GlassFishRestDeployableContainer.java**

Change imports:
```java
// Remove:
import org.jboss.arquillian.container.glassfish.CommonGlassFishManager;

// Add:
import org.jboss.arquillian.container.glassfish.clientutils.GlassFishAdminClient;
```

Change field and setup:
```java
// Before:
private CommonGlassFishManager<CommonGlassFishConfiguration> glassFishManager;
// setup():
this.glassFishManager = new CommonGlassFishManager<CommonGlassFishConfiguration>(configuration);

// After:
private GlassFishAdminClient adminClient;
// setup():
this.adminClient = new GlassFishAdminClient(configuration);
```

Change method bodies:
```java
// Before: glassFishManager.start()
// After:  adminClient.start()

// Before: glassFishManager.deploy(archive)
// After:  adminClient.deploy(archive)

// Before: glassFishManager.undeploy(archive)
// After:  adminClient.undeploy(archive)
```

- [ ] **Step 2: Update GlassFishManagedDeployableContainer.java**

Change imports:
```java
// Remove:
import org.jboss.arquillian.container.glassfish.CommonGlassFishManager;

// Add:
import org.jboss.arquillian.container.glassfish.clientutils.GlassFishAdminClient;
```

Change field and setup:
```java
// Before:
private CommonGlassFishManager<GlassFishManagedContainerConfiguration> glassFishManager;
// setup():
this.glassFishManager = new CommonGlassFishManager<>(configuration);

// After:
private GlassFishAdminClient adminClient;
// setup():
this.adminClient = new GlassFishAdminClient(configuration);
```

Change all `glassFishManager.` references to `adminClient.`:
```java
// Before: glassFishManager.isDASRunning()
// After:  adminClient.isDASRunning()

// Before: glassFishManager.start()
// After:  adminClient.start()

// Before: glassFishManager.deploy(archive)
// After:  adminClient.deploy(archive)

// Before: glassFishManager.undeploy(archive)
// After:  adminClient.undeploy(archive)
```

- [ ] **Step 3: Verify compile**

```bash
./mvnw -B compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: update containers to use GlassFishAdminClient directly"
```

---

### Task 4: Delete old files

**Files:**
- Remove: `glassfish-common/src/main/java/org/jboss/arquillian/container/glassfish/clientutils/GlassFishClient.java`
- Remove: `glassfish-common/src/main/java/org/jboss/arquillian/container/glassfish/CommonGlassFishManager.java`

- [ ] **Step 1: Delete old files**

```bash
rm glassfish-common/src/main/java/org/jboss/arquillian/container/glassfish/clientutils/GlassFishClient.java
rm glassfish-common/src/main/java/org/jboss/arquillian/container/glassfish/CommonGlassFishManager.java
```

- [ ] **Step 2: Verify compile**

```bash
./mvnw -B compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: remove GlassFishClient interface and CommonGlassFishManager"
```

---

### Task 5: Final verification

- [ ] **Step 1: Full build**

```bash
./mvnw -B install -DskipTests
```
Expected: BUILD SUCCESS (all 5 modules)

- [ ] **Step 2: Verify file structure**

```bash
ls glassfish-common/src/main/java/org/jboss/arquillian/container/glassfish/clientutils/
```
Expected output:
```
GlassFishAdminClient.java
GlassFishHttpClient.java
GlassFishClientException.java
MultipartBody.java
NodeAddress.java
```

- [ ] **Step 3: Push**

```bash
git push
```
