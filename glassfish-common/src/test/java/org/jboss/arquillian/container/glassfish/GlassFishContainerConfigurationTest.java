package org.jboss.arquillian.container.glassfish;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlassFishContainerConfigurationTest {

    @AfterEach
    void clearSystemProperties() {
        System.clearProperty("glassfish.adminHost");
        System.clearProperty("glassfish.adminPort");
        System.clearProperty("glassfish.adminHttps");
        System.clearProperty("glassfish.authorisation");
        System.clearProperty("glassfish.debugRequests");
        System.clearProperty("glassfish.adminUser");
        System.clearProperty("glassfish.adminPassword");
        System.clearProperty("glassfish.target");
        System.clearProperty("glassfish.libraries");
        System.clearProperty("glassfish.properties");
        System.clearProperty("glassfish.type");
        System.clearProperty("glassfish.waitTimeMs");
        System.clearProperty("glassfish.retries");
    }

    @Test
    void shouldUseDefaultValues() {
        var config = new GlassFishContainerConfiguration();

        assertEquals("localhost", config.getAdminHost());
        assertEquals(4848, config.getAdminPort());
        assertFalse(config.isAdminHttps());
        assertFalse(config.isAuthorisation());
        assertFalse(config.isDebugRequests());
        assertNull(config.getAdminUser());
        assertNull(config.getAdminPassword());
        assertEquals("server", config.getTarget());
        assertNull(config.getLibraries());
        assertNull(config.getProperties());
        assertNull(config.getType());
        assertEquals(100, config.getWaitTimeMs());
        assertEquals(5, config.getRetries());
    }

    @Test
    void shouldReadValuesFromSystemProperties() {
        System.setProperty("glassfish.adminHost", "192.168.1.100");
        System.setProperty("glassfish.adminPort", "5050");
        System.setProperty("glassfish.adminHttps", "true");
        System.setProperty("glassfish.authorisation", "true");
        System.setProperty("glassfish.debugRequests", "true");
        System.setProperty("glassfish.adminUser", "admin");
        System.setProperty("glassfish.adminPassword", "secret");
        System.setProperty("glassfish.target", "myInstance");
        System.setProperty("glassfish.libraries", "lib1,lib2");
        System.setProperty("glassfish.properties", "key=value");
        System.setProperty("glassfish.type", "osgi");
        System.setProperty("glassfish.waitTimeMs", "200");
        System.setProperty("glassfish.retries", "10");

        var config = new GlassFishContainerConfiguration();

        assertEquals("192.168.1.100", config.getAdminHost());
        assertEquals(5050, config.getAdminPort());
        assertTrue(config.isAdminHttps());
        assertTrue(config.isAuthorisation());
        assertTrue(config.isDebugRequests());
        assertEquals("admin", config.getAdminUser());
        assertEquals("secret", config.getAdminPassword());
        assertEquals("myInstance", config.getTarget());
        assertEquals("lib1,lib2", config.getLibraries());
        assertEquals("key=value", config.getProperties());
        assertEquals("osgi", config.getType());
        assertEquals(200, config.getWaitTimeMs());
        assertEquals(10, config.getRetries());
    }
}
