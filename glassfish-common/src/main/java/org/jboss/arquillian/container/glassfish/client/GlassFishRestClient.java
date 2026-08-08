/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author Z.Paulovics
 */
package org.jboss.arquillian.container.glassfish.client;

import org.jboss.arquillian.container.glassfish.GlassFishContainerConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST client for the GlassFish admin API using {@link java.net.http.HttpClient}
 * and Jakarta JSONB for JSON response parsing.
 */
public class GlassFishRestClient {

    // ── constants ──────────────────────────────────────────────────────────
    private static final String USER_AGENT_HEADER = "User-Agent";
    public static final String USER_AGENT_VALUE = "arquillian-glassfish";

    private static final String CSRF_HEADER = "X-Requested-By";
    private static final String CSRF_VALUE = "GlassFish REST Client";

    private static final Logger log = Logger.getLogger(GlassFishRestClient.class.getName());

    // ── fields ─────────────────────────────────────────────────────────────

    private final GlassFishContainerConfiguration configuration;
    private final String adminBaseUrl;
    private final HttpClient httpClient;

    // ── constructor ────────────────────────────────────────────────────────

    public GlassFishRestClient(GlassFishContainerConfiguration configuration, String adminBaseUrl) {
        this.configuration = configuration;
        this.adminBaseUrl = adminBaseUrl;
        HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30));
        if (configuration.isAuthorisation()) {
            builder.authenticator(new GlassFishAuthenticator(
                configuration.getAdminUser(), configuration.getAdminPassword()));
        }
        this.httpClient = builder.build();
    }

    public GlassFishContainerConfiguration getConfiguration() {
        return configuration;
    }

    public String getAdminBaseUrl() {
        return adminBaseUrl;
    }

    // ── high-level operations ──────────────────────────────────────────────

    /** Get GlassFish server version information. */
    public GlassFishResponse getVersion() {
        return executeGet("/version", GlassFishResponse.class);
    }

    /** Deploy an application via multipart POST. */
    public GlassFishResponse deployApplication(DeployApplicationRequest request,
                                                String archiveName,
                                                InputStream archiveData) throws IOException {
        MultipartBodyPublisher form = MultipartBodyPublisher.newBuilder()
            .addFilePart("id", archiveName, archiveData)
            .addField("name", request.name(), "text/plain")
            .addField("target", request.target(), "text/plain")
            .addFieldIf("libraries", request.libraries(), "text/plain")
            .addFieldIf("properties", request.properties(), "text/plain")
            .addFieldIf("type", "osgi".equals(request.type()) ? request.type() : null, "text/plain")
            .build();
        return executePost("/applications/application", form, GlassFishResponse.class);
    }

    /** Undeploy an application via JSON POST. */
    public GlassFishResponse undeployApplication(String name, String target) {
        var request = new UndeployApplicationRequest(name, target);
        return executePostJson("/applications/application/" + name + "/undeploy",
            JsonBodyPublisher.of(request), GlassFishResponse.class);
    }

    /** Execute a POST with JSON body. */
    public <T> T executePostJson(String path, JsonBodyPublisher body, Class<T> type) {
        try {
            HttpRequest request = newPostJsonBuilder()
                .uri(URI.create(adminBaseUrl + path))
                .header("Content-Type", "application/json")
                .POST(body)
                .build();
            return send(request, type);
        } catch (IOException | InterruptedException e) {
            throw new GlassFishClientException(e);
        }
    }

    /** List sub-components of a deployed application. */
    public GlassFishResponse listSubComponents(String name, Map<String, String> queryParams) {
        return executeGet("/applications/application/" + name + "/list-sub-components",
            queryParams, GlassFishResponse.class);
    }

    /** Get the list of all server instances with their statuses. */
    public List<Map<String, Object>> getInstancesList() {
        Map<String, Object> responseMap = executeGet("/list-instances", Map.class);
        List<Map<String, Object>> instancesList = new ArrayList<>();
        Map<String, Object> extraProperties = extraProperties(responseMap);
        if (extraProperties != null) {
            instancesList = (List<Map<String, Object>>) extraProperties.get("instanceList");
        }
        return instancesList;
    }

    /** Get the list of virtual servers for a configuration. */
    public List<Map<String, Object>> getVirtualServersList(String configRef) {
        String path = "/configs/config/" + configRef + "/http-service/list-virtual-servers";
        return (List<Map<String, Object>>) executeGet(path, GlassFishResponse.class)
            .extraProperties().get("children");
    }

    // ── resource-level accessors ──────────────────────────────────────────

    /** Get the map of standalone server instances. */
    public Map<String, String> getServersList() {
        return getChildResources("/servers/server");
    }

    /** Get the map of clusters. */
    public Map<String, String> getClustersList() {
        return getChildResources("/clusters/cluster");
    }

    /** Get the attributes (contextRoot, etc.) for a deployed application. */
    public Map<String, String> getApplicationAttributes(String name) {
        return getAttributes("/applications/application/" + name);
    }

    /** Get the list of server instances belonging to a cluster. */
    public Map<String, String> getServerInstances(String target) {
        return getChildResources("/clusters/cluster/" + target + "/server-ref");
    }

    /**
     * Get the server attributes map. Contains keys:
     * {@code nodeRef} — reference to the node object,
     * {@code configRef} — reference to the server's configuration object.
     */
    public Map<String, String> getServerAttributes(String server) {
        return getAttributes("/servers/server/" + server);
    }

    /**
     * Get the cluster attributes map. Contains key:
     * {@code configRef} — reference to the cluster's configuration object.
     */
    public Map<String, String> getClusterAttributes(String cluster) {
        return getAttributes("/clusters/cluster/" + cluster);
    }

    /** Get the host name (IP or FQDN) for a node reference. */
    public String getNodeHost(String nodeRef) {
        return getAttributes("/nodes/node/" + nodeRef).get("nodeHost");
    }

    /**
     * Get a system property value from a server or cluster configuration.
     * The property name typically references a port number
     * (e.g. {@code HTTP_LISTENER_PORT}, {@code HTTP_SSL_LISTENER_PORT}).
     */
    public Map<String, String> getSystemProperty(String configRef, String propertyName) {
        return getAttributes("/configs/config/" + configRef
            + "/system-property/" + propertyName);
    }

    /**
     * Get a system property overridden at the server instance level.
     * If not overridden, falls back to the configuration-level system property.
     */
    public Map<String, String> getServerSystemProperty(String server, String propertyName) {
        return getAttributes("/servers/server/" + server
            + "/system-property/" + propertyName);
    }

    /** Get attributes for a virtual server, including its network listeners. */
    public Map<String, String> getVirtualServerAttributes(String configRef, String virtualServer) {
        return getAttributes("/configs/config/" + configRef
            + "/http-service/virtual-server/" + virtualServer);
    }

    /** Get attributes for a network listener (enabled, port, protocol). */
    public Map<String, String> getListenerAttributes(String configRef, String listener) {
        return getAttributes("/configs/config/" + configRef
            + "/network-config/network-listeners/network-listener/" + listener);
    }

    /** Get attributes for a protocol, including its {@code securityEnabled} flag. */
    public Map<String, String> getProtocolAttributes(String configRef, String protocol) {
        return getAttributes("/configs/config/" + configRef
            + "/network-config/protocols/protocol/" + protocol);
    }

    /** Check if the DAS is running. */
    public boolean isDASRunning() {
        try {
            executeGet("", Map.class);
            return true;
        } catch (GlassFishClientException e) {
            return e.getCause() == null
                || e.getCause().getMessage() == null
                || !e.getCause().getMessage().contains("ConnectException");
        }
    }

    // ── generic typed GET/POST ─────────────────────────────────────────────

    /** Execute a GET and parse the JSON response as the given type. */
    public <T> T executeGet(String path, Class<T> type) {
        try {
            HttpRequest request = newGetBuilder()
                .uri(URI.create(adminBaseUrl + path))
                .GET()
                .build();
            return send(request, type);
        } catch (IOException | InterruptedException e) {
            throw new GlassFishClientException(e);
        }
    }

    /** Execute a GET with query parameters. */
    public <T> T executeGet(String path, Map<String, String> queryParams, Class<T> type) {
        String fullPath = path;
        if (queryParams != null && !queryParams.isEmpty()) {
            fullPath += "?" + buildQueryString(queryParams);
        }
        return executeGet(fullPath, type);
    }

    /** Execute a POST with multipart body and parse the JSON response. */
    public <T> T executePost(String path, MultipartBodyPublisher form, Class<T> type) {
        try {
            HttpRequest request = newPostBuilder()
                .uri(URI.create(adminBaseUrl + path))
                .header("Content-Type", form.getContentType())
                .POST(form)
                .build();
            return send(request, type);
        } catch (IOException | InterruptedException e) {
            throw new GlassFishClientException(e);
        }
    }

    // ── low-level accessors ────────────────────────────────────────────────

    /** Get the attributes (entity map) for a REST resource. */
    public Map<String, String> getAttributes(String resourceUrl) {
        Map<String, Object> responseMap = executeGet(resourceUrl, Map.class);
        Map<String, String> attributes = new HashMap<>();
        Map<String, Map<String, String>> extraProperties = extraProperties(responseMap);
        if (extraProperties != null) {
            attributes = extraProperties.get("entity");
        }
        return attributes;
    }

    /** Get the child resources map for a REST resource. */
    public Map<String, String> getChildResources(String resourceUrl) {
        Map<String, Object> responseMap = executeGet(resourceUrl, Map.class);
        Map<String, String> childResources = new HashMap<>();
        Map<String, Object> extraProperties = extraProperties(responseMap);
        if (extraProperties != null) {
            childResources = (Map<String, String>) extraProperties.get("childResources");
        }
        return childResources;
    }



    // ── private methods ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <T> Map<String, T> extraProperties(Map<String, Object> responseMap) {
        return (Map<String, T>) responseMap.get("extraProperties");
    }

    private <T> T send(HttpRequest request, Class<T> type) throws IOException, InterruptedException {
        if (configuration.isDebugRequests()) {
            log.info("HTTP " + request.method() + " " + request.uri());
            request.headers().map().forEach((name, values) ->
                log.info("  " + name + ": " + String.join(", ", values)));
        }
        HttpResponse<T> response = httpClient.send(request, new JsonBodyHandler<>(type));
        if (configuration.isDebugRequests()) {
            log.info("HTTP response status: " + response.statusCode());
            log.info("HTTP response body: " + response.body());
        }
        return response.body();
    }

    private HttpRequest.Builder newGetBuilder() {
        return HttpRequest.newBuilder()
            .header("Accept", "application/json")
            .header(CSRF_HEADER, CSRF_VALUE)
            .header(USER_AGENT_HEADER, USER_AGENT_VALUE);
    }

    private HttpRequest.Builder newPostBuilder() {
        return HttpRequest.newBuilder()
            .header("Accept", "application/json")
            .header(CSRF_HEADER, CSRF_VALUE)
            .header(USER_AGENT_HEADER, USER_AGENT_VALUE);
    }

    private HttpRequest.Builder newPostJsonBuilder() {
        return HttpRequest.newBuilder()
            .header("Accept", "application/json")
            .header(CSRF_HEADER, CSRF_VALUE)
            .header(USER_AGENT_HEADER, USER_AGENT_VALUE);
    }

    private String buildQueryString(Map<String, String> queryParams) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (var entry : queryParams.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            sb.append("=");
            sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        return sb.toString();
    }

    // ── inner classes ────────────────────────────────────────────────────

    /**
     * HTTP Basic authenticator for the GlassFish admin API.
     */
    private static class GlassFishAuthenticator extends java.net.Authenticator {
        private final String user;
        private final String password;

        GlassFishAuthenticator(String user, String password) {
            this.user = user;
            this.password = password;
        }

        @Override
        protected java.net.PasswordAuthentication getPasswordAuthentication() {
            return new java.net.PasswordAuthentication(user, password.toCharArray());
        }
    }
}
