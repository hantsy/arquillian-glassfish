package org.jboss.arquillian.container.glassfish.it;

import org.jboss.arquillian.container.glassfish.GlassFishAdminClient;
import org.jboss.arquillian.container.glassfish.GlassFishContainerConfiguration;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GlassFishAdminClientIT {

    private static GlassFishAdminClient adminClient;
    private static WebArchive war;

    @BeforeAll
    static void setUp() {
        var config = new GlassFishContainerConfiguration();
        config.setAdminHost("localhost");
        config.setAdminPort(4848);
        config.setAdminUser("admin");
        config.setAdminPassword("adminadmin");
        adminClient = new GlassFishAdminClient(config);

        war = ShrinkWrap.create(WebArchive.class, "hello.war")
            .addClass(HelloServlet.class);
    }

    @Test
    @Order(1)
    void shouldStartAndDiscoverTopology() throws LifecycleException {
        adminClient.start();
        assertTrue(adminClient.isDASRunning());
    }

    @Test
    @Order(2)
    void shouldDeployHelloWorld() throws DeploymentException {
        var metadata = adminClient.deploy(war);
        assertNotNull(metadata);
        assertFalse(metadata.getContexts().isEmpty());

        var httpContext = metadata.getContexts(org.jboss.arquillian.container.spi.client.protocol.metadata.HTTPContext.class).stream()
            .findFirst()
            .orElseThrow();
        assertNotNull(httpContext.getHost());
        assertNotEquals(0, httpContext.getPort());
        assertFalse(httpContext.getServlets().isEmpty());
    }

    @Test
    @Order(3)
    void shouldUndeployHelloWorld() throws DeploymentException {
        adminClient.undeploy(war);
    }
}
