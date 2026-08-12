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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * REST client for the GlassFish admin API using {@link java.net.http.HttpClient}
 * and StAX-based XML response parsing.
 */
public class GlassFishRestClient {

    private static final Logger log = Logger.getLogger(GlassFishRestClient.class.getName());

    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String USER_AGENT_VALUE = "arquillian-glassfish";
    private static final String CSRF_HEADER = "X-Requested-By";
    private static final String CSRF_VALUE = "GlassFish REST Client";

    private final String adminBaseUrl;
    private final boolean debugRequests;
    private final HttpClient httpClient;

    public GlassFishRestClient(String adminBaseUrl, String adminUser,
                               String adminPassword, boolean debugRequests) {
        this.adminBaseUrl = adminBaseUrl;
        this.debugRequests = debugRequests;
        HttpClient.Builder builder = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30));
        if (adminUser != null) {
            builder.authenticator(new GlassFishAuthenticator(adminUser, adminPassword));
        }
        this.httpClient = builder.build();
    }

    public VersionInfo getVersion() {
        String xml = executeGet("/version");
        var map = GlassFishXMLParser.xmlToMap(xml);
        return VersionInfo.fromParsedMap(map);
    }

    public ServersList getServersList() {
        String xml = executeGet("/servers/server");
        return ServersList.fromParsedMap(GlassFishXMLParser.xmlToMap(xml));
    }

    public ClustersList getClustersList() {
        String xml = executeGet("/clusters/cluster");
        return ClustersList.fromParsedMap(GlassFishXMLParser.xmlToMap(xml));
    }

    public ServerInstanceRefs getServerInstances(String target) {
        String xml = executeGet("/clusters/cluster/" + target + "/server-ref");
        return ServerInstanceRefs.fromParsedMap(GlassFishXMLParser.xmlToMap(xml));
    }

    public ServerAttribute getServerAttributes(String server) {
        String xml = executeGet("/servers/server/" + server);
        return ServerAttribute.fromParsedMap(GlassFishXMLParser.xmlToMap(xml));
    }

    public ClusterAttribute getClusterAttributes(String cluster) {
        String xml = executeGet("/clusters/cluster/" + cluster);
        return ClusterAttribute.fromParsedMap(GlassFishXMLParser.xmlToMap(xml));
    }

    public NodeAttribute getNodeConfig(String nodeRef) {
        String xml = executeGet("/nodes/node/" + nodeRef);
        return NodeAttribute.fromParsedMap(GlassFishXMLParser.xmlToMap(xml));
    }

    public ApplicationAttribute getApplicationAttributes(String name) {
        String xml = executeGet("/applications/application/" + name);
        return ApplicationAttribute.fromParsedMap(GlassFishXMLParser.xmlToMap(xml));
    }

    public String getSystemProperty(String configRef, String propertyName) {
        String xml = executeGet("/configs/config/" + configRef
            + "/system-property/" + propertyName);
        return SystemPropertyValue.fromParsedMap(GlassFishXMLParser.xmlToMap(xml)).value();
    }

    public String getServerSystemProperty(String server, String propertyName) {
        String xml = executeGet("/servers/server/" + server
            + "/system-property/" + propertyName);
        return SystemPropertyValue.fromParsedMap(GlassFishXMLParser.xmlToMap(xml)).value();
    }

    public VirtualServerAttribute getVirtualServerAttributes(String configRef, String virtualServer) {
        String xml = executeGet("/configs/config/" + configRef
            + "/http-service/virtual-server/" + virtualServer);
        return VirtualServerAttribute.fromParsedMap(GlassFishXMLParser.xmlToMap(xml));
    }

    public NetworkListenerAttribute getListenerAttributes(String configRef, String listener) {
        String xml = executeGet("/configs/config/" + configRef
            + "/network-config/network-listeners/network-listener/" + listener);
        return NetworkListenerAttribute.fromParsedMap(GlassFishXMLParser.xmlToMap(xml));
    }

    public ProtocolAttribute getProtocolAttributes(String configRef, String protocol) {
        String xml = executeGet("/configs/config/" + configRef
            + "/network-config/protocols/protocol/" + protocol);
        return ProtocolAttribute.fromParsedMap(GlassFishXMLParser.xmlToMap(xml));
    }

    public List<InstanceInfo> getInstanceList() {
        String xml = executeGet("/list-instances");
        return InstanceInfo.fromParsedMap(GlassFishXMLParser.xmlToMap(xml));
    }

    public List<String> getVirtualServers(String configRef, String target) {
        String path = "/configs/config/" + configRef + "/http-service/list-virtual-servers";
        var queryParams = Map.of("target", target);
        String xml = executeGet(path, queryParams);
        var map = GlassFishXMLParser.xmlToMap(xml);
        @SuppressWarnings("unchecked")
        var children = (List<Map<String, Object>>) map.get("children");
        if (children == null) {
            return List.of();
        }
        return children.stream()
            .map(c -> (String) c.get("message"))
            .filter(name -> !"__asadmin".equals(name))
            .toList();
    }

    public SubComponents getSubComponents(String name) {
        return getSubComponents(name, Map.of());
    }

    public SubComponents getSubComponents(String name, String module, String type) {
        var params = new HashMap<String, String>();
        params.put("appname", name);
        params.put("id", module);
        params.put("type", type);
        return getSubComponents(name, params);
    }

    private SubComponents getSubComponents(String name, Map<String, String> queryParams) {
        String xml = executeGet("/applications/application/" + name + "/list-sub-components", queryParams);
        return SubComponents.fromParsedMap(GlassFishXMLParser.xmlToMap(xml));
    }

    public ActionResult deployApplication(DeployApplicationRequest request) throws IOException {
        MultipartBodyPublisher form = MultipartBodyPublisher.newBuilder()
            .addFilePart("id", request.archiveName(), request.archiveData())
            .addField("name", request.name(), "text/plain")
            .addField("target", request.target(), "text/plain")
            .addOptionalField("libraries", request.libraries(), "text/plain")
            .addOptionalField("properties", request.properties(), "text/plain")
            .addOptionalField("type", "osgi".equals(request.type()) ? request.type() : null, "text/plain")
            .build();
        String xml = executePost("/applications/application", form);
        return ActionResult.fromParsedMap(GlassFishXMLParser.xmlToMap(xml));
    }

    public ActionResult undeployApplication(String name, String target) throws IOException {
        MultipartBodyPublisher form = MultipartBodyPublisher.newBuilder()
            .addField("target", target, "text/plain")
            .addField("operation", "__deleteoperation", "text/plain")
            .build();
        String xml = executePost("/applications/application/" + name, form);
        return ActionResult.fromParsedMap(GlassFishXMLParser.xmlToMap(xml));
    }

    /**
     * Check if the DAS is running by attempting a connection to the admin root.
     * Any HTTP response (even an error or redirect) means the server is accepting
     * connections and therefore running.
     */
    public boolean isDASRunning() {
        try {
            executeGet("");
            return true;
        } catch (GlassFishClientException e) {
            return false;
        }
    }

    private String executeGet(String path) {
        try {
            HttpRequest request = newGetBuilder()
                .uri(URI.create(adminBaseUrl + path))
                .GET()
                .build();
            return send(request);
        } catch (IOException | InterruptedException e) {
            throw new GlassFishClientException(e);
        }
    }

    private String executeGet(String path, Map<String, String> queryParams) {
        String fullPath = path;
        if (queryParams != null && !queryParams.isEmpty()) {
            fullPath += "?" + buildQueryString(queryParams);
        }
        return executeGet(fullPath);
    }

    private String executePost(String path, MultipartBodyPublisher form) {
        try {
            HttpRequest request = newPostBuilder()
                .uri(URI.create(adminBaseUrl + path))
                .header("Content-Type", form.getContentType())
                .POST(form)
                .build();
            return send(request);
        } catch (IOException | InterruptedException e) {
            throw new GlassFishClientException(e);
        }
    }

    private String send(HttpRequest request) throws IOException, InterruptedException {
        if (debugRequests) {
            log.info("HTTP " + request.method() + " " + request.uri());
            request.headers().map().forEach((name, values) ->
                log.info("  " + name + ": " + String.join(", ", values)));
        }
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (debugRequests) {
                log.info("HTTP response status: " + response.statusCode());
                log.info("HTTP response body: " + response.body());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GlassFishClientException(e);
        } catch (Exception e) {
            throw new GlassFishClientException(e);
        }
    }

    private HttpRequest.Builder newGetBuilder() {
        return HttpRequest.newBuilder()
            .header("Accept", "application/xml")
            .header(CSRF_HEADER, CSRF_VALUE)
            .header(USER_AGENT_HEADER, USER_AGENT_VALUE);
    }

    private HttpRequest.Builder newPostBuilder() {
        return HttpRequest.newBuilder()
            .header("Accept", "application/xml")
            .header(CSRF_HEADER, CSRF_VALUE)
            .header(USER_AGENT_HEADER, USER_AGENT_VALUE);
    }

    private String buildQueryString(Map<String, String> queryParams) {
        return queryParams.entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    }

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
