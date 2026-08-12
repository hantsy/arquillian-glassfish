package org.jboss.arquillian.container.glassfish.client;

import java.util.Map;

/**
 * URL: GET /clusters/cluster/{target}/server-ref
 * XML: childResources map of instance-name to URL.
 */
public record ServerInstanceRefs(Map<String, String> instances) {

    @SuppressWarnings("unchecked")
    public static ServerInstanceRefs fromParsedMap(Map<String, Object> map) {
        var extraProps = (Map<String, Object>) map.get("extraProperties");
        if (extraProps == null) return new ServerInstanceRefs(Map.of());
        var childResources = (Map<String, Object>) extraProps.get("childResources");
        if (childResources == null) return new ServerInstanceRefs(Map.of());
        Map<String, String> instances = new java.util.LinkedHashMap<>();
        for (var entry : childResources.entrySet()) {
            instances.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return new ServerInstanceRefs(instances);
    }
}
