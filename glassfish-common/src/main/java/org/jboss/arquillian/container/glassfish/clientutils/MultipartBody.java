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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight multipart/form-data body builder for {@link java.net.http.HttpClient}.
 * Replaces Jersey's {@code FormDataMultiPart} / {@code StreamDataBodyPart}.
 */
public class MultipartBody {

    private static final String CRLF = "\r\n";
    private static final String TWO_HYPHENS = "--";

    private final String boundary;
    private final List<TextPart> textParts = new ArrayList<>();
    private FilePart filePart;

    public MultipartBody() {
        this.boundary = "Boundary-" + System.currentTimeMillis();
    }

    /**
     * Add a text field to the multipart form.
     */
    public void addField(String name, String value, String contentType) {
        textParts.add(new TextPart(name, value, contentType));
    }

    /**
     * Add a binary file part to the multipart form.
     */
    public void addFilePart(String name, String filename, InputStream data) {
        this.filePart = new FilePart(name, filename, data);
    }

    /**
     * Returns the Content-Type header value for this multipart body.
     */
    public String getContentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    /**
     * Builds an {@link HttpRequest.BodyPublisher} for this multipart form.
     */
    public HttpRequest.BodyPublisher toBodyPublisher() throws IOException {
        if (filePart != null) {
            return buildWithFilePart();
        }
        return buildTextOnly();
    }

    private HttpRequest.BodyPublisher buildTextOnly() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (TextPart part : textParts) {
            writeBoundary(out);
            writeTextPart(out, part);
        }
        writeClosingBoundary(out);
        return HttpRequest.BodyPublishers.ofByteArray(out.toByteArray());
    }

    private HttpRequest.BodyPublisher buildWithFilePart() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (TextPart part : textParts) {
            writeBoundary(out);
            writeTextPart(out, part);
        }
        writeBoundary(out);
        writeFilePartHeader(out, filePart);
        byte[] preamble = out.toByteArray();

        byte[] fileData = filePart.data.readAllBytes();
        byte[] closing = (CRLF + TWO_HYPHENS + boundary + TWO_HYPHENS + CRLF).getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream all = new ByteArrayOutputStream();
        all.write(preamble);
        all.write(fileData);
        all.write(closing);
        return HttpRequest.BodyPublishers.ofByteArray(all.toByteArray());
    }

    private void writeBoundary(ByteArrayOutputStream out) {
        try {
            out.write((TWO_HYPHENS + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeClosingBoundary(ByteArrayOutputStream out) {
        try {
            out.write((TWO_HYPHENS + boundary + TWO_HYPHENS + CRLF).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeTextPart(ByteArrayOutputStream out, TextPart part) {
        try {
            out.write(("Content-Disposition: form-data; name=\"" + part.name + "\"" + CRLF).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: " + part.contentType + CRLF).getBytes(StandardCharsets.UTF_8));
            out.write(CRLF.getBytes(StandardCharsets.UTF_8));
            out.write(part.value.getBytes(StandardCharsets.UTF_8));
            out.write(CRLF.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeFilePartHeader(ByteArrayOutputStream out, FilePart part) {
        try {
            out.write(("Content-Disposition: form-data; name=\"" + part.name
                + "\"; filename=\"" + part.filename + "\"" + CRLF).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: application/octet-stream" + CRLF).getBytes(StandardCharsets.UTF_8));
            out.write(CRLF.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private record TextPart(String name, String value, String contentType) {}
    private record FilePart(String name, String filename, InputStream data) {}
}
