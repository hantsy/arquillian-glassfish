package org.jboss.arquillian.container.glassfish.client;

import java.io.InputStream;

/**
 * Request data for deploying an application to GlassFish via REST.
 */
public record DeployApplicationRequest(
    String archiveName,
    InputStream archiveData,
    String name,
    String target,
    String libraries,
    String properties,
    String type) {

    public static Builder builder(String archiveName, InputStream archiveData, String name, String target) {
        return new Builder(archiveName, archiveData, name, target);
    }

    public static final class Builder {
        private final String archiveName;
        private final InputStream archiveData;
        private final String name;
        private final String target;
        private String libraries;
        private String properties;
        private String type;

        Builder(String archiveName, InputStream archiveData, String name, String target) {
            this.archiveName = archiveName;
            this.archiveData = archiveData;
            this.name = name;
            this.target = target;
        }

        public Builder libraries(String libraries) {
            this.libraries = libraries;
            return this;
        }

        public Builder properties(String properties) {
            this.properties = properties;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public DeployApplicationRequest build() {
            return new DeployApplicationRequest(archiveName, archiveData, name, target, libraries, properties, type);
        }
    }
}
