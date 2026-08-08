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

import java.net.URI;

public record NodeAddress(String serverName, String host, int httpPort, int httpsPort) {

    /** HTTP protocol URI prefix */
    public static final String HTTP_PROTOCOL_PREFIX = "http://";
    /** HTTPS protocol URI prefix */
    public static final String HTTPS_PROTOCOL_PREFIX = "https://";

    public NodeAddress() {
        this("server", "localhost", 0, 0);
    }

    public NodeAddress(String host) {
        this("server", host, 0, 0);
    }

    public URI getURI() {
        return getURI(false);
    }

    public URI getURI(boolean secure) {
        return URI.create(getHttpProtocolPrefix(secure) + host + ":" + (!secure ? httpPort : httpsPort));
    }

    public static String getHttpProtocolPrefix(boolean secure) {
        return secure ? HTTPS_PROTOCOL_PREFIX : HTTP_PROTOCOL_PREFIX;
    }
}
