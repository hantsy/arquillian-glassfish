package org.jboss.arquillian.container.glassfish.client;

import java.util.List;
import java.util.Map;

/**
 * URL: GET /list-instances
 * XML: instances list with name, status, cluster, node.
 */
public record InstanceInfo(String name, String status, String cluster, String node) {

    @SuppressWarnings("unchecked")
    public static List<InstanceInfo> fromParsedMap(Map<String, Object> map) {
        var extraProps = (Map<String, Object>) map.get("extraProperties");
        if (extraProps == null) {
            return List.of();
        }
        var instances = (List<Map<String, Object>>) extraProps.get("instanceList");
        if (instances == null) {
            return List.of();
        }
        return instances.stream()
            .map(InstanceInfo::fromInstanceMap)
            .toList();
    }

    private static InstanceInfo fromInstanceMap(Map<String, Object> m) {
        return new InstanceInfo(
            String.valueOf(m.get("name")),
            String.valueOf(m.get("status")),
            String.valueOf(m.get("cluster")),
            String.valueOf(m.get("node"))
        );
    }
}
