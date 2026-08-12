package org.jboss.arquillian.container.glassfish.test;

import jakarta.servlet.annotation.WebServlet;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;

import static org.junit.jupiter.api.Assertions.assertEquals;


public abstract class GlassFishDeploymentTestTemplate {

    @ArquillianResource
    private URL deploymentUrl;

    @Test
    public void shouldBeAbleToDeployEnterpriseArchive() throws Exception {
        final String servletPath = greeterImplementationBasedOnDerbyEnabled()
            .getAnnotation(WebServlet.class)
            .urlPatterns()[0];

        final URLConnection response = URI.create(deploymentUrl.toString() + servletPath.substring(1))
            .toURL()
            .openConnection();

        try (var in = new BufferedReader(new InputStreamReader(response.getInputStream()))) {
            final String result = in.readLine();
            assertEquals("Hello", result);
        }
    }

    public static Class<?> greeterImplementationBasedOnDerbyEnabled() {
        if (Boolean.parseBoolean(System.getProperty("enableDerby"))) {
            return GreeterServletWithDerby.class;
        }
        return GreeterServlet.class;
    }
}
