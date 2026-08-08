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
package org.jboss.arquillian.container.glassfish.clientutils;

import org.jboss.arquillian.container.glassfish.CommonGlassFishConfiguration;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
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
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GlassFishClientUtil {

    /**
     * Status for a successful GlassFish exit code deployment.
     */
    public static final String SUCCESS = "SUCCESS";

    /**
     * Status for a GlassFish exit code deployment which ended in warning.
     */
    public static final String WARNING = "WARNING";

    private static final String CSRF_HEADER = "X-Requested-By";
    private static final String CSRF_VALUE = "GlassFish REST Client";
    private static final String USER_AGENT_HEADER = "User-Agent";

    private CommonGlassFishConfiguration configuration;

    private String adminBaseUrl;

    private HttpClient httpClient;

    private static final Logger log = Logger.getLogger(GlassFishClientUtil.class.getName());

    public GlassFishClientUtil(CommonGlassFishConfiguration configuration, String adminBaseUrl) {
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

    /**
     * Returns the pre-configured {@link HttpClient} for callers that need to build custom requests.
     */
    public HttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Returns the admin base URL for callers that need to build custom request URIs.
     */
    public String getAdminBaseUrl() {
        return adminBaseUrl;
    }

    public Map<String, String> getAttributes(String additionalResourceUrl) {
        Map<String, Object> responseMap = GETRequest(additionalResourceUrl);
        Map<String, String> attributes = new HashMap<>();

        Map<String, Map<String, String>> resultExtraProperties = (Map<String, Map<String, String>>) responseMap.get("extraProperties");
        if (resultExtraProperties != null) {
            attributes = resultExtraProperties.get("entity");
        }

        return attributes;
    }

    public Map<String, String> getChildResources(String additionalResourceUrl) throws GlassFishClientException {
        Map<String, Object> responseMap = GETRequest(additionalResourceUrl);
        Map<String, String> childResources = new HashMap<>();

        Map<String, Object> resultExtraProperties = (Map<String, Object>) responseMap.get("extraProperties");
        if (resultExtraProperties != null) {
            childResources = (Map<String, String>) resultExtraProperties.get("childResources");
        }

        return childResources;
    }

    /**
     * Invoke a GET request against the adminSubPath.
     * @param adminSubPath - subpath of the admin command
     * @return map of the parsed XML response
     */
    public Map<String, Object> GETRequest(String adminSubPath) {
        try {
            HttpRequest request = newGetBuilder()
                .uri(URI.create(adminBaseUrl + adminSubPath))
                .GET()
                .build();
            HttpResponse<String> response = sendRequest(request);
            return getResponseMap(response);
        } catch (Exception e) {
            throw new GlassFishClientException(e);
        }
    }

    /**
     * Invoke a GET request with path template resolution and query parameters.
     *
     * @param pathTemplate a URI path template with {var} placeholders
     * @param templateVars variables to resolve in the path template
     * @param queryParams  query parameters to append (can be null or empty)
     * @return map of the parsed XML response
     */
    public Map<String, Object> GETRequest(String pathTemplate, Map<String, String> templateVars,
                                           Map<String, String> queryParams) {
        String resolvedPath = resolveTemplates(pathTemplate, templateVars);
        String fullPath = resolvedPath;
        if (queryParams != null && !queryParams.isEmpty()) {
            fullPath += "?" + buildQueryString(queryParams);
        }
        return GETRequest(fullPath);
    }

    public List<Map<String, Object>> getInstancesList(String additionalResourceUrl) throws GlassFishClientException {
        Map<String, Object> responseMap = GETRequest(additionalResourceUrl);
        List<Map<String, Object>> instancesList = new ArrayList<>();

        Map<String, Object> resultExtraProperties = (Map<String, Object>) responseMap.get("extraProperties");
        if (resultExtraProperties != null) {
            instancesList = (List<Map<String, Object>>) resultExtraProperties.get("instanceList");
        }

        return instancesList;
    }

    public Map<String, Object> POSTMultiPartRequest(String additionalResourceUrl, MultipartBody form) {
        try {
            HttpRequest request = newPostBuilder()
                .uri(URI.create(adminBaseUrl + additionalResourceUrl))
                .header("Content-Type", form.getContentType())
                .POST(form.toBodyPublisher())
                .build();
            HttpResponse<String> response = sendRequest(request);
            return getResponseMap(response);
        } catch (Exception e) {
            throw new GlassFishClientException(e);
        }
    }

    /**
     * Build a {@link HttpRequest.Builder} pre-configured with common headers for GET requests.
     */
    private HttpRequest.Builder newGetBuilder() {
        return HttpRequest.newBuilder()
            .header("Accept", "application/xml")
            .header(CSRF_HEADER, CSRF_VALUE)
            .header(USER_AGENT_HEADER, GlassFishClientService.USER_AGENT_VALUE);
    }

    /**
     * Build a {@link HttpRequest.Builder} pre-configured with common headers for POST requests.
     */
    private HttpRequest.Builder newPostBuilder() {
        return HttpRequest.newBuilder()
            .header("Accept", "application/xml")
            .header(CSRF_HEADER, CSRF_VALUE)
            .header(USER_AGENT_HEADER, GlassFishClientService.USER_AGENT_VALUE);
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

    Map<String, Object> getResponseMap(HttpResponse<String> response) throws GlassFishClientException {
        Map<String, Object> responseMap = new HashMap<>();
        String message = "";
        final String xmlDoc = response.body();

        // Marshalling the XML format response to a java Map
        if (xmlDoc != null && !xmlDoc.isEmpty()) {
            responseMap = xmlToMap(xmlDoc);

            message = "exit_code: " + responseMap.get("exit_code")
                + ", message: " + responseMap.get("message");
        }

        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            // O.K. — the HTTP call was successful, what about the GlassFish server response?
            if (responseMap.get("exit_code") == null) {
                throw new GlassFishClientException(message);
            } else if (WARNING.equals(responseMap.get("exit_code"))) {
                // Warning is not a failure - some warnings in GlassFish are inevitable (i.e. persistence-related: ARQ-606)
                log.warning("Deployment resulted in a warning: " + message);
            } else if (!SUCCESS.equals(responseMap.get("exit_code"))) {
                // Response is not a warning nor success - it's surely a failure.
                throw new GlassFishClientException(message);
            }
        } else if (statusCode == 404) {
            // the REST resource can not be found (for optional resources it can be O.K.)
            message += " [status: " + statusCode + "]";
            log.warning(message);
        } else {
            message += " [status: " + statusCode + "]";
            log.severe(message);
            throw new GlassFishClientException(message);
        }

        return responseMap;
    }

    /**
     * Marshalling a Glassfish Mng API response XML document to a java Map object.
     *
     * @param document XML
     * @return map containing the XML doc representation in java map format
     */
    public Map<String, Object> xmlToMap(String document) {

        if (document == null) {
            return new HashMap<>();
        }

        InputStream input = null;
        Map<String, Object> map = null;
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_VALIDATING, false);
            input = new ByteArrayInputStream(document.trim().getBytes("UTF-8"));
            XMLStreamReader stream = factory.createXMLStreamReader(input);
            while (stream.hasNext()) {
                int currentEvent = stream.next();
                if (currentEvent == XMLStreamConstants.START_ELEMENT) {
                    if ("map".equals(stream.getLocalName())) {
                        map = resolveXmlMap(stream);
                    }
                }
            }
        } catch (Exception ex) {
            log.log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        } finally {
            try {
                input.close();
            } catch (IOException ex) {
                log.log(Level.SEVERE, null, ex);
            }
        }

        return map;
    }

    private Map<String, Object> resolveXmlMap(XMLStreamReader stream) throws XMLStreamException {

        boolean endMapFlag = false;
        Map<String, Object> entry = new HashMap<>();
        String key = null;
        String elementName = null;

        while (!endMapFlag) {

            int currentEvent = stream.next();
            if (currentEvent == XMLStreamConstants.START_ELEMENT) {

                if ("entry".equals(stream.getLocalName())) {
                    key = stream.getAttributeValue(null, "key");
                    String value = stream.getAttributeValue(null, "value");
                    if (value != null) {
                        entry.put(key, value);
                        key = null;
                    }
                } else if ("map".equals(stream.getLocalName())) {
                    Map<String, Object> value = resolveXmlMap(stream);
                    entry.put(key, value);
                } else if ("list".equals(stream.getLocalName())) {
                    List<Object> value = resolveXmlList(stream);
                    entry.put(key, value);
                } else {
                    elementName = stream.getLocalName();
                }
            } else if (currentEvent == XMLStreamConstants.END_ELEMENT) {

                if ("map".equals(stream.getLocalName())) {
                    endMapFlag = true;
                }
                elementName = null;
            } else {

                String document = stream.getText();
                if (elementName != null) {
                    if ("number".equals(elementName)) {
                        if (document.contains(".")) {
                            entry.put(key, Double.parseDouble(document));
                        } else {
                            entry.put(key, Long.parseLong(document));
                        }
                    } else if ("string".equals(elementName)) {
                        entry.put(key, document);
                    }
                    elementName = null;
                }
            } // end if
        } // end while
        return entry;
    }

    private List<Object> resolveXmlList(XMLStreamReader stream) throws XMLStreamException {

        boolean endListFlag = false;
        List<Object> list = new ArrayList<>();
        String elementName = null;

        while (!endListFlag) {

            int currentEvent = stream.next();
            if (currentEvent == XMLStreamConstants.START_ELEMENT) {
                if ("map".equals(stream.getLocalName())) {
                    list.add(resolveXmlMap(stream));
                } else {
                    elementName = stream.getLocalName();
                }
            } else if (currentEvent == XMLStreamConstants.END_ELEMENT) {

                if ("list".equals(stream.getLocalName())) {
                    endListFlag = true;
                }
                elementName = null;
            } else {

                String document = stream.getText();
                if (elementName != null) {
                    if ("number".equals(elementName)) {
                        if (document.contains(".")) {
                            list.add(Double.parseDouble(document));
                        } else {
                            list.add(Long.parseLong(document));
                        }
                    } else if ("string".equals(elementName)) {
                        list.add(document);
                    }
                    elementName = null;
                }
            } // end if
        } // end while
        return list;
    }

    /**
     * Resolve a URI path template with {var} placeholders.
     */
    static String resolveTemplates(String pathTemplate, Map<String, String> templateVars) {
        String result = pathTemplate;
        if (templateVars != null) {
            for (var entry : templateVars.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return result;
    }

    /**
     * Build a URL-encoded query string from a map of parameters.
     */
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
