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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An {@link HttpResponse.BodyHandler} that parses the JSON response body
 * into a typed object and validates the GlassFish exit code for raw Map responses.
 */
public class JsonBodyHandler<T> implements HttpResponse.BodyHandler<T> {

    private static final String SUCCESS = "SUCCESS";
    private static final String WARNING = "WARNING";

    private static final Logger log = Logger.getLogger(JsonBodyHandler.class.getName());

    private final Class<T> type;

    public JsonBodyHandler(Class<T> type) {
        this.type = type;
    }

    @Override
    public HttpResponse.BodySubscriber<T> apply(HttpResponse.ResponseInfo responseInfo) {
        int statusCode = responseInfo.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            return new JsonBodySubscriber<>(type);
        }
        if (statusCode == 404) {
            log.warning(" [status: " + statusCode + "]");
            return HttpResponse.BodySubscribers.replacing(null);
        }
        log.severe(" [status: " + statusCode + "]");
        throw new GlassFishClientException("HTTP " + statusCode);
    }

    /**
     * Extract the {@code extraProperties} map from a response map.
     */
    @SuppressWarnings("unchecked")
    public static <T> Map<String, T> extraProperties(Map<String, Object> responseMap) {
        return (Map<String, T>) responseMap.get("extraProperties");
    }

    // ── subscriber ───────────────────────────────────────────────────

    private static class JsonBodySubscriber<T> implements HttpResponse.BodySubscriber<T> {

        private final Class<T> type;
        private final CompletableFuture<T> result = new CompletableFuture<>();
        private final StringBuilder body = new StringBuilder();

        JsonBodySubscriber(Class<T> type) {
            this.type = type;
        }

        @Override
        public CompletionStage<T> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            for (ByteBuffer item : items) {
                body.append(StandardCharsets.UTF_8.decode(item));
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            try {
                String bodyString = body.toString();
                if (bodyString.isEmpty()) {
                    result.complete(null);
                    return;
                }
                try (Jsonb jsonb = JsonbBuilder.create()) {
                    T parsed = jsonb.fromJson(bodyString, type);
                    if (parsed instanceof Map<?, ?> m) {
                        validateExitCode(m);
                    }
                    result.complete(parsed);
                }
            } catch (Exception e) {
                result.completeExceptionally(
                    new GlassFishClientException("Failed to parse JSON: " + body, e));
            }
        }

        private void validateExitCode(Map<?, ?> map) {
            Object exitCode = map.get("exit_code");
            if (exitCode == null) {
                result.completeExceptionally(new GlassFishClientException(""));
                return;
            }
            if (WARNING.equals(exitCode)) {
                log.warning("Deployment resulted in a warning: exit_code: "
                    + exitCode + ", message: " + map.get("message"));
            } else if (!SUCCESS.equals(exitCode)) {
                result.completeExceptionally(new GlassFishClientException(
                    "exit_code: " + exitCode + ", message: " + map.get("message")));
            }
        }
    }
}
