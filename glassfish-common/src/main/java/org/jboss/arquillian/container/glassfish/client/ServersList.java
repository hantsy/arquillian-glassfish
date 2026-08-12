package org.jboss.arquillian.container.glassfish.client;

import java.util.Map;

/**
 * URL: GET /servers/server
 * XML: childResources map of server-name to URL.
 */
public record ServersList(Map<String, String> servers) {

    @SuppressWarnings("unchecked")
    public static ServersList fromParsedMap(Map<String, Object> map) {
        var extraProps = (Map<String, Object>) map.get("extraProperties");
        if (extraProps == null) return new ServersList(Map.of());
        var childResources = (Map<String, Object>) extraProps.get("childResources");
        if (childResources == null) return new ServersList(Map.of());
        Map<String, String> converted = childResources.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> String.valueOf(e.getValue())));
        return new ServersList(converted);
    }
}
