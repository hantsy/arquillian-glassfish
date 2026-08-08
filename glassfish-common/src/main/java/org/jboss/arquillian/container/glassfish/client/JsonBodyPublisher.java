package org.jboss.arquillian.container.glassfish.client;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow;

/**
 * An {@link HttpRequest.BodyPublisher} for JSON-encoded request bodies.
 */
public class JsonBodyPublisher implements HttpRequest.BodyPublisher {

    private final byte[] body;

    private JsonBodyPublisher(byte[] body) {
        this.body = body;
    }

    public static JsonBodyPublisher of(String json) {
        return new JsonBodyPublisher(json.getBytes(StandardCharsets.UTF_8));
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
