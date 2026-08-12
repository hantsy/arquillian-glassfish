package org.jboss.arquillian.container.glassfish;

import org.jboss.arquillian.container.glassfish.client.DeployApplicationRequest;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GlassFishAdminClientIT {

    private static GlassFishAdminClient adminClient;
    private static GlassFishContainerConfiguration config;

    @BeforeAll
    static void setUp() {
        config = new GlassFishContainerConfiguration();
        config.setAdminHost("localhost");
        config.setAdminPort(4848);
        config.setAdminUser("admin");
        config.setAdminPassword("adminadmin");
        adminClient = new GlassFishAdminClient(config);
    }

    @Test
    @Order(1)
    void shouldStartAndDiscoverTopology() throws LifecycleException {
        adminClient.start();
        assertTrue(adminClient.isDASRunning());
    }

    @Test
    @Order(2)
    void shouldDeployHelloWorld() throws IOException {
        try (InputStream war = getClass().getResourceAsStream("/hello.war")) {
            assertNotNull(war, "hello.war not found in test resources");
            var request = DeployApplicationRequest.builder("hello.war", war, "hello", "server").build();
            var response = adminClient.restClient.deployApplication(request);
            assertNotNull(response);
            assertEquals("SUCCESS", response.exitCode());
        }
    }

    @Test
    @Order(3)
    void shouldUndeployHelloWorld() throws IOException {
        // Uses multipart POST to /applications/application/{name}
        // with operation=__deleteoperation — the same approach as the old
        // Jersey-based CommonGlassFishManager.undeploy().
        var response = adminClient.restClient.undeployApplication("hello", "server");
        // Response may be null if GlassFish returns an empty body — that's fine,
        // the undeploy operation succeeded if no exception was thrown.
        if (response != null) {
            assertEquals("SUCCESS", response.exitCode());
        }
    }
}
