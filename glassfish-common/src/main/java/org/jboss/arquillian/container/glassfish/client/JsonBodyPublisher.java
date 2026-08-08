package org.jboss.arquillian.container.glassfish.client;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow;

/**
 * An {@link HttpRequest.BodyPublisher} for JSON-encoded request bodies.
 * Serializes objects to JSON using Jakarta JSONB.
 */
public class JsonBodyPublisher implements HttpRequest.BodyPublisher {

    private final byte[] body;

    private JsonBodyPublisher(byte[] body) {
        this.body = body;
    }

    /**
     * Create a publisher from a raw JSON string.
     */
    public static JsonBodyPublisher of(String json) {
        return new JsonBodyPublisher(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Create a publisher by serializing an object to JSON using Jakarta JSONB.
     */
    public static JsonBodyPublisher of(Object object) {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            return new JsonBodyPublisher(jsonb.toJson(object).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new GlassFishClientException("Failed to serialize JSON body: " + object, e);
        }
    }

    @Override
    public long contentLength() {
        return body.length;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        HttpRequest.BodyPublishers.ofByteArray(body).subscribe(subscriber);
    }
}
