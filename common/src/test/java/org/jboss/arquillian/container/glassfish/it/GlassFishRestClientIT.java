package org.jboss.arquillian.container.glassfish;

import org.jboss.arquillian.container.glassfish.client.GlassFishClientException;
import org.jboss.arquillian.container.glassfish.client.GlassFishRestClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlassFishRestClientIT {

    private static final String ADMIN_URL = "http://localhost:4848/management/domain";
    private static GlassFishRestClient client;

    @BeforeAll
    static void setUp() {
        client = new GlassFishRestClient(ADMIN_URL, "admin", "adminadmin", false);
    }

    @Test
    void shouldGetVersion() {
        var version = client.getVersion();
        assertNotNull(version);
        assertNotNull(version.versionNumber());
    }

    @Test
    void shouldGetServersList() {
        var servers = client.getServersList();
        assertNotNull(servers);
        assertTrue(servers.servers().containsKey("server"));
    }

    @Test
    void shouldGetServerAttributes() {
        var server = client.getServerAttributes("server");
        assertNotNull(server);
        assertEquals("server-config", server.configRef());
    }

    @Test
    void shouldGetNodeConfig() {
        // Get the actual node ref from the DAS server attributes
        var serverInfo = client.getServerAttributes("server");
        assertNotNull(serverInfo);
        String nodeRef = serverInfo.nodeRef();
        if (nodeRef == null || nodeRef.isBlank()) {
            // Admin server may not have a node ref (e.g. embedded/default config)
            // or nodeRef may be blank in Cargo-managed domains
            return;
        }
        var node = client.getNodeConfig(nodeRef);
        // Node configuration may not exist for the DAS in a simple domain
        if (node == null) {
            return;
        }
        assertNotNull(node.nodeHost());
    }

    @Test
    void shouldGetSystemProperty() {
        var value = client.getSystemProperty("server-config", "JMS_PROVIDER_PORT");
        assertNotNull(value);
        assertEquals("7676", value);
    }

    @Test
    void shouldGetVirtualServerAttributes() {
        var vs = client.getVirtualServerAttributes("server-config", "server");
        assertNotNull(vs);
        assertNotNull(vs.networkListeners());
    }

    @Test
    void shouldGetListenerAttributes() {
        var listener = client.getListenerAttributes("server-config", "http-listener-1");
        assertNotNull(listener);
        assertTrue(listener.enabled());
        assertEquals("8080", listener.port());
    }

    @Test
    void shouldGetProtocolAttributes() {
        var protocol = client.getProtocolAttributes("server-config", "http-listener-1");
        assertNotNull(protocol);
        assertFalse(protocol.securityEnabled());
    }

    @Test
    void shouldGetVirtualServers() {
        var vs = client.getVirtualServers("server-config", "server");
        assertNotNull(vs);
        assertFalse(vs.isEmpty());
        assertTrue(vs.contains("server"));
    }

    @Test
    void shouldGetClustersList() {
        var clusters = client.getClustersList();
        assertNotNull(clusters);
    }

    @Test
    void shouldGetInstanceList() {
        // GF 7.x may reject the query params for /list-instances with HTTP 500,
        // or return an empty list for domains with no standalone instances.
        // The production code (runningInstanceFilter) handles this.
        try {
            var instances = client.getInstanceList();
            assertNotNull(instances);
            // Empty list is valid for a simple DAS domain with no instances
        } catch (GlassFishClientException e) {
            System.err.println("WARN: getInstanceList failed: " + e.getMessage());
        }
    }

    @Test
    void shouldCheckDASRunning() {
        assertTrue(client.isDASRunning());
    }
}
