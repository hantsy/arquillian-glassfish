package org.jboss.arquillian.container.glassfish.managed;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedContainerConfigurationTest {

    @AfterEach
    void clearSystemProperties() {
        System.clearProperty("glassfish.home");
        System.clearProperty("glassfish.domain");
        System.clearProperty("glassfish.outputToConsole");
        System.clearProperty("glassfish.debug");
        System.clearProperty("glassfish.allowConnectingToRunningServer");
        System.clearProperty("glassfish.enableDerby");
    }

    @Test
    void shouldUseDefaultValuesWhenEnvNotSet() {
        var config = new ManagedContainerConfiguration();

        assertNull(config.getGlassFishHome());
        assertNull(config.getDomain());
        assertTrue(config.isOutputToConsole());
        assertFalse(config.isDebug());
        assertFalse(config.isAllowConnectingToRunningServer());
        assertFalse(config.isEnableDerby());
        assertEquals("server", config.getTarget());
    }

    @Test
    void shouldReadValuesFromSystemProperties() {
        System.setProperty("glassfish.home", "/opt/glassfish7");
        System.setProperty("glassfish.domain", "domain1");
        System.setProperty("glassfish.outputToConsole", "false");
        System.setProperty("glassfish.debug", "true");
        System.setProperty("glassfish.allowConnectingToRunningServer", "true");
        System.setProperty("glassfish.enableDerby", "true");

        var config = new ManagedContainerConfiguration();

        assertEquals("/opt/glassfish7", config.getGlassFishHome());
        assertEquals("domain1", config.getDomain());
        assertFalse(config.isOutputToConsole());
        assertTrue(config.isDebug());
        assertTrue(config.isAllowConnectingToRunningServer());
        assertTrue(config.isEnableDerby());
    }
}
