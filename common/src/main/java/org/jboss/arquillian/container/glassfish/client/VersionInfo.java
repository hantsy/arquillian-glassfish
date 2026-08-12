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
        // GF7 returns camelCase (versionNumber), GF8 returns kebab-case (version-number)
        String version = (String) extraProps.getOrDefault("versionNumber",
            extraProps.get("version-number"));
        return new VersionInfo(version);
    }
}
