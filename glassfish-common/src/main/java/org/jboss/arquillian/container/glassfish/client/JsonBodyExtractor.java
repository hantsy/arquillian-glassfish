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
package org.jboss.arquillian.container.glassfish.client;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extracts typed Java objects from JSON HTTP responses.
 * Validates HTTP status codes and GlassFish exit codes.
 */
public class JsonBodyExtractor {

    private static final String SUCCESS = "SUCCESS";
    private static final String WARNING = "WARNING";

    private static final Logger log = Logger.getLogger(JsonBodyExtractor.class.getName());

    private final Jsonb jsonb;

    public JsonBodyExtractor() {
        this.jsonb = JsonbBuilder.create();
    }

    /**
     * Extract and validate a typed object from an HTTP response.
     */
    public <T> T extract(HttpResponse<String> response, Class<T> type) {
        String body = response.body();
        T result = body != null && !body.isEmpty() ? fromJson(body, type) : null;

        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            if (result instanceof Map<?, ?> m) {
                validateExitCode(m);
            }
        } else if (statusCode == 404) {
            log.warning(" [status: " + statusCode + "]");
        } else {
            log.severe(" [status: " + statusCode + "]");
            throw new GlassFishClientException("HTTP " + statusCode);
        }

        return result;
    }

    /**
     * Parse a JSON string to a typed object.
     */
    public <T> T fromJson(String body, Class<T> type) {
        try {
            return jsonb.fromJson(body, type);
        } catch (Exception e) {
            throw new GlassFishClientException("Failed to parse JSON response: " + body, e);
        }
    }

    /**
     * Extract the {@code extraProperties} map from a response map.
     */
    @SuppressWarnings("unchecked")
    public static <T> Map<String, T> extraProperties(Map<String, Object> responseMap) {
        return (Map<String, T>) responseMap.get("extraProperties");
    }

    private void validateExitCode(Map<?, ?> map) {
        Object exitCode = map.get("exit_code");
        if (exitCode == null) {
            throw new GlassFishClientException("");
        }
        if (WARNING.equals(exitCode)) {
            log.warning("Deployment resulted in a warning: exit_code: "
                + exitCode + ", message: " + map.get("message"));
        } else if (!SUCCESS.equals(exitCode)) {
            throw new GlassFishClientException("exit_code: "
                + exitCode + ", message: " + map.get("message"));
        }
    }

    public void close() {
        try {
            jsonb.close();
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to close JSONB instance", e);
        }
    }
}
