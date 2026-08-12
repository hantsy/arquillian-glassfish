package org.jboss.arquillian.container.glassfish;

import org.jboss.arquillian.container.glassfish.client.ActionResult;
import org.jboss.arquillian.container.glassfish.client.ApplicationAttribute;
import org.jboss.arquillian.container.glassfish.client.GlassFishRestClient;
import org.jboss.arquillian.container.glassfish.client.SubComponents;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlassFishAdminClientTest {

    private GlassFishRestClient restClient;
    private GlassFishAdminClient adminClient;
    private WebArchive war;

    @BeforeEach
    void setUp() {
        restClient = mock(GlassFishRestClient.class);
        var config = new GlassFishContainerConfiguration();
        config.setAdminHost("localhost");
        config.setAdminPort(4848);
        config.setAdminUser("admin");
        config.setAdminPassword("adminadmin");
        adminClient = new GlassFishAdminClient(config, restClient);
        adminClient.setNodeAddress(new GlassFishAdminClient.NodeAddress("server", "localhost", 8080, 8181));

        war = ShrinkWrap.create(WebArchive.class, "test.war")
            .add(new StringAsset("<html/>"), "index.html");
    }

    @Test
    void shouldThrowOnNullArchive() {
        assertThrows(NullPointerException.class, () -> adminClient.deploy(null));
        assertThrows(NullPointerException.class, () -> adminClient.undeploy(null));
    }

    @Test
    void shouldDeploySuccessfully() throws IOException, DeploymentException {
        when(restClient.deployApplication(any())).thenReturn(
            ActionResult.fromParsedMap(Map.of("exit_code", "SUCCESS")));
        when(restClient.getSubComponents("test")).thenReturn(SubComponents.empty());
        when(restClient.getApplicationAttributes("test")).thenReturn(
            new ApplicationAttribute("test", "/test"));

        var metadata = adminClient.deploy(war);
        assertNotNull(metadata);
    }

    @Test
    void shouldUndeploySuccessfully() throws IOException, DeploymentException {
        when(restClient.undeployApplication("test", "server")).thenReturn(
            ActionResult.fromParsedMap(Map.of("exit_code", "SUCCESS")));

        assertDoesNotThrow(() -> adminClient.undeploy(war));
    }

    @Test
    void shouldReportDASNotRunning() {
        when(restClient.isDASRunning()).thenReturn(false);
        assertFalse(adminClient.isDASRunning());
    }

    @Test
    void shouldReportDASRunning() {
        when(restClient.isDASRunning()).thenReturn(true);
        assertTrue(adminClient.isDASRunning());
    }
}
