package org.jboss.arquillian.container.glassfish.client;

import java.util.Map;

/**
 * URL: GET /version
 */
public record VersionInfo(String versionNumber) {

    @SuppressWarnings("unchecked")
    public static VersionInfo fromParsedMap(Map<String, Object> map) {
        var extraProps = (Map<String, Object>) map.get("extraProperties");
        if (extraProps == null) {
            return new VersionInfo(null);
        }
        String version = (String) extraProps.get("version-number");
        return new VersionInfo(version);
    }
}
