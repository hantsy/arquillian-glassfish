package org.jboss.arquillian.container.glassfish.client;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * URL: GET /clusters/cluster
 * XML: childResources map of cluster-name to URL.
 */
public record ClustersList(Map<String, String> clusters) {

    @SuppressWarnings("unchecked")
    public static ClustersList fromParsedMap(Map<String, Object> map) {
        var extraProps = (Map<String, Object>) map.get("extraProperties");
        if (extraProps == null) return new ClustersList(Map.of());
        var childResourcesRaw = (Map<String, Object>) extraProps.get("childResources");
        if (childResourcesRaw == null) return new ClustersList(Map.of());
        var childResources = childResourcesRaw.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
        return new ClustersList(childResources);
    }
}
