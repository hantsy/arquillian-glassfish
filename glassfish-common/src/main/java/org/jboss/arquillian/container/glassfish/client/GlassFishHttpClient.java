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

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.jboss.arquillian.container.glassfish.CommonGlassFishConfiguration;

import java.io.IOException;
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
 * Low-level HTTP client for the GlassFish admin REST API.
 * Uses {@link java.net.http.HttpClient} and Jakarta JSONB for JSON parsing.
 */
public class GlassFishHttpClient {

    public static final String SUCCESS = "SUCCESS";
    public static final String WARNING = "WARNING";

    private static final String CSRF_HEADER = "X-Requested-By";
    private static final String CSRF_VALUE = "GlassFish REST Client";
    private static final String USER_AGENT_HEADER = "User-Agent";
    public static final String USER_AGENT_VALUE = "arquillian-glassfish-managed-jakarta";

    private final CommonGlassFishConfiguration configuration;
    private final String adminBaseUrl;
    private final HttpClient httpClient;

    private static final Logger log = Logger.getLogger(GlassFishHttpClient.class.getName());

    public GlassFishHttpClient(CommonGlassFishConfiguration configuration, String adminBaseUrl) {
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

    public CommonGlassFishConfiguration getConfiguration() {
        return configuration;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public String getAdminBaseUrl() {
        return adminBaseUrl;
    }

    // ── typed operations ────────────────────────────────────────────────

    /**
     * Get GlassFish server version information.
     */
    public VersionResponse getVersion() {
        return executeGet("/version", VersionResponse.class);
    }

    /**
     * Deploy an application via multipart POST.
     */
    public DeploymentResponse deployApplication(MultipartBodyPublisher form) {
        return executePost("/applications/application", form, DeploymentResponse.class);
    }

    /**
     * Undeploy an application via multipart POST.
     */
    public DeploymentResponse undeployApplication(String name, MultipartBodyPublisher form) {
        String path = "/applications/application/" + name;
        return executePost(path, form, DeploymentResponse.class);
    }

    /**
     * List sub-components of a deployed application.
     */
    public DeploymentResponse listSubComponents(String name, Map<String, String> queryParams) {
        return executeGet("/applications/application/" + name + "/list-sub-components",
            queryParams, DeploymentResponse.class);
    }

    /**
     * Check if the DAS is running.
     */
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

    // ── generic typed GET/POST ───────────────────────────────────────────

    /**
     * Execute a GET and parse the JSON response as the given type.
     */
    public <T> T executeGet(String path, Class<T> type) {
        try {
            HttpRequest request = newGetBuilder()
                .uri(URI.create(adminBaseUrl + path))
                .GET()
                .build();
            HttpResponse<String> response = sendRequest(request);
            return parseResponse(response, type);
        } catch (IOException | InterruptedException e) {
            throw new GlassFishClientException(e);
        }
    }

    /**
     * Execute a GET with query parameters and parse the JSON response as the given type.
     */
    public <T> T executeGet(String path, Map<String, String> queryParams, Class<T> type) {
        String fullPath = path;
        if (queryParams != null && !queryParams.isEmpty()) {
            fullPath += "?" + buildQueryString(queryParams);
        }
        return executeGet(fullPath, type);
    }

    /**
     * Execute a POST with multipart body and parse the JSON response as the given type.
     */
    public <T> T executePost(String path, MultipartBodyPublisher form, Class<T> type) {
        try {
            HttpRequest request = newPostBuilder()
                .uri(URI.create(adminBaseUrl + path))
                .header("Content-Type", form.getContentType())
                .POST(form)
                .build();
            HttpResponse<String> response = sendRequest(request);
            return parseResponse(response, type);
        } catch (IOException | InterruptedException e) {
            throw new GlassFishClientException(e);
        }
    }

    /**
     * Parse HTTP response, validating status and exit_code for deployment-style responses.
     */
    private <T> T parseResponse(HttpResponse<String> response, Class<T> type) throws GlassFishClientException {
        int statusCode = response.statusCode();
        String body = response.body();
        String message = "";

        T result = null;
        if (body != null && !body.isEmpty()) {
            result = jsonbFromBody(body, type);
        }

        if (statusCode >= 200 && statusCode < 300) {
            if (result instanceof Map<?, ?> m) {
                Object exitCode = m.get("exit_code");
                if (exitCode == null) {
                    throw new GlassFishClientException(message);
                } else if (WARNING.equals(exitCode)) {
                    message = "exit_code: " + exitCode + ", message: " + m.get("message");
                    log.warning("Deployment resulted in a warning: " + message);
                } else if (!SUCCESS.equals(exitCode)) {
                    message = "exit_code: " + exitCode + ", message: " + m.get("message");
                    throw new GlassFishClientException(message);
                }
            }
        } else if (statusCode == 404) {
            message = " [status: " + statusCode + "]";
            log.warning(message);
        } else {
            message = " [status: " + statusCode + "]";
            log.severe(message);
            throw new GlassFishClientException(message);
        }

        return result;
    }

    // ── convenience accessors (return typed maps) ────────────────────────

    /**
     * Get the attributes (entity map) for a REST resource.
     */
    public Map<String, String> getAttributes(String resourceUrl) {
        Map<String, Object> responseMap = executeGet(resourceUrl, Map.class);
        Map<String, String> attributes = new HashMap<>();
        Map<String, Map<String, String>> extraProperties = extractExtraProperties(responseMap);
        if (extraProperties != null) {
            attributes = extraProperties.get("entity");
        }
        return attributes;
    }

    /**
     * Get the child resources map for a REST resource.
     */
    public Map<String, String> getChildResources(String resourceUrl) {
        Map<String, Object> responseMap = executeGet(resourceUrl, Map.class);
        Map<String, String> childResources = new HashMap<>();
        Map<String, Object> extraProperties = extractExtraProperties(responseMap);
        if (extraProperties != null) {
            childResources = (Map<String, String>) extraProperties.get("childResources");
        }
        return childResources;
    }

    /**
     * Get the list of server instances from a REST resource.
     */
    public List<Map<String, Object>> getInstancesList(String resourceUrl) {
        Map<String, Object> responseMap = executeGet(resourceUrl, Map.class);
        List<Map<String, Object>> instancesList = new ArrayList<>();
        Map<String, Object> extraProperties = extractExtraProperties(responseMap);
        if (extraProperties != null) {
            instancesList = (List<Map<String, Object>>) extraProperties.get("instanceList");
        }
        return instancesList;
    }

    // ── JSONB parsing ────────────────────────────────────────────────────

    /**
     * Parse a JSON string to a typed object using Jakarta JSONB.
     */
    public <T> T jsonbFromBody(String body, Class<T> type) {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            return jsonb.fromJson(body, type);
        } catch (Exception e) {
            throw new GlassFishClientException("Failed to parse JSON response: " + body, e);
        }
    }

    // ── internal helpers ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static <T> Map<String, T> extractExtraProperties(Map<String, Object> responseMap) {
        return (Map<String, T>) responseMap.get("extraProperties");
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

    private HttpResponse<String> sendRequest(HttpRequest request) throws IOException, InterruptedException {
        if (configuration.isDebugRequests()) {
            log.info("HTTP " + request.method() + " " + request.uri());
            request.headers().map().forEach((name, values) ->
                log.info("  " + name + ": " + String.join(", ", values)));
        }
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (configuration.isDebugRequests()) {
            log.info("HTTP response status: " + response.statusCode());
            log.info("HTTP response body: " + response.body());
        }
        return response;
    }

    public static String resolveTemplates(String pathTemplate, Map<String, String> templateVars) {
        String result = pathTemplate;
        if (templateVars != null) {
            for (var entry : templateVars.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return result;
    }

    static String buildQueryString(Map<String, String> queryParams) {
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

    /**
     * Simple HTTP Basic authenticator for GlassFish admin API.
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
