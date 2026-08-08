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
package org.jboss.arquillian.container.glassfish.clientutils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;

/**
 * Immutable multipart/form-data body that implements {@link HttpRequest.BodyPublisher}.
 * Use the {@link Builder} to assemble form data, then call {@link Builder#build()}.
 * <p>
 * Usage:
 * <pre>{@code
 * MultipartBody body = MultipartBody.newBuilder()
 *     .addField("name", "myapp", "text/plain")
 *     .addFilePart("id", "app.war", inputStream)
 *     .build();
 * httpClient.send(HttpRequest.newBuilder()
 *     .header("Content-Type", body.getContentType())
 *     .POST(body)
 *     .build(), BodyHandlers.ofString());
 * }</pre>
 */
public final class MultipartBody implements HttpRequest.BodyPublisher {

    private static final String CRLF = "\r\n";
    private static final String TWO_HYPHENS = "--";

    private final String boundary;
    private final byte[] body;

    private MultipartBody(String boundary, byte[] body) {
        this.boundary = boundary;
        this.body = body;
    }

    /**
     * Returns the Content-Type header value for this multipart body.
     */
    public String getContentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    @Override
    public long contentLength() {
        return body.length;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        HttpRequest.BodyPublishers.ofByteArray(body).subscribe(subscriber);
    }

    /**
     * Create a new {@link Builder}.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Mutable builder for assembling a {@link MultipartBody}.
     */
    public static final class Builder {

        private static final String DEFAULT_BOUNDARY = "Boundary-" + System.currentTimeMillis();

        private final String boundary;
        private final List<TextPart> textParts = new ArrayList<>();
        private FilePart filePart;

        Builder() {
            this.boundary = DEFAULT_BOUNDARY;
        }

        /**
         * Add a text field.
         */
        public Builder addField(String name, String value, String contentType) {
            textParts.add(new TextPart(name, value, contentType));
            return this;
        }

        /**
         * Add a binary file part.
         */
        public Builder addFilePart(String name, String filename, InputStream data) {
            this.filePart = new FilePart(name, filename, data);
            return this;
        }

        /**
         * Build the immutable {@link MultipartBody}.
         */
        public MultipartBody build() throws IOException {
            byte[] all;
            if (filePart != null) {
                all = buildWithFilePart();
            } else {
                all = buildTextOnly();
            }
            return new MultipartBody(boundary, all);
        }

        private byte[] buildTextOnly() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (TextPart part : textParts) {
                writeBoundary(out, boundary);
                writeTextPart(out, part);
            }
            writeClosingBoundary(out, boundary);
            return out.toByteArray();
        }

        private byte[] buildWithFilePart() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (TextPart part : textParts) {
                writeBoundary(out, boundary);
                writeTextPart(out, part);
            }
            writeBoundary(out, boundary);
            writeFilePartHeader(out, filePart);

            byte[] preamble = out.toByteArray();
            byte[] fileData = filePart.data.readAllBytes();
            byte[] closing = (CRLF + TWO_HYPHENS + boundary + TWO_HYPHENS + CRLF)
                .getBytes(StandardCharsets.UTF_8);

            ByteArrayOutputStream all = new ByteArrayOutputStream();
            all.write(preamble);
            all.write(fileData);
            all.write(closing);
            return all.toByteArray();
        }

        // --- serialization helpers ---

        private static void writeBoundary(ByteArrayOutputStream out, String boundary) {
            try {
                out.write((TWO_HYPHENS + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed writing multipart boundary", e);
            }
        }

        private static void writeClosingBoundary(ByteArrayOutputStream out, String boundary) {
            try {
                out.write((TWO_HYPHENS + boundary + TWO_HYPHENS + CRLF).getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed writing multipart closing boundary", e);
            }
        }

        private static void writeTextPart(ByteArrayOutputStream out, TextPart part) {
            try {
                out.write(("Content-Disposition: form-data; name=\"" + part.name + "\"" + CRLF)
                    .getBytes(StandardCharsets.UTF_8));
                out.write(("Content-Type: " + part.contentType + CRLF).getBytes(StandardCharsets.UTF_8));
                out.write(CRLF.getBytes(StandardCharsets.UTF_8));
                out.write(part.value.getBytes(StandardCharsets.UTF_8));
                out.write(CRLF.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed writing multipart text part", e);
            }
        }

        private static void writeFilePartHeader(ByteArrayOutputStream out, FilePart part) {
            try {
                out.write(("Content-Disposition: form-data; name=\"" + part.name
                    + "\"; filename=\"" + part.filename + "\"" + CRLF).getBytes(StandardCharsets.UTF_8));
                out.write(("Content-Type: application/octet-stream" + CRLF).getBytes(StandardCharsets.UTF_8));
                out.write(CRLF.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed writing multipart file header", e);
            }
        }

        private record TextPart(String name, String value, String contentType) {}
        private record FilePart(String name, String filename, InputStream data) {}
    }
}
