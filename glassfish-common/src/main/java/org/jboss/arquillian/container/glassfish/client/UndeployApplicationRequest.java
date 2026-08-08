package org.jboss.arquillian.container.glassfish.client;

/**
 * Request body for the GlassFish REST undeploy endpoint.
 */
public record UndeployApplicationRequest(String id, String target) {
}
