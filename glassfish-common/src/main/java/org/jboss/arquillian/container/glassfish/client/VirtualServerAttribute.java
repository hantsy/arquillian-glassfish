package org.jboss.arquillian.container.glassfish.client;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * URL: GET /configs/config/{config}/http-service/virtual-server/{name}
 * XML: entity map with id, networkListeners, hosts, state.
 */
public record VirtualServerAttribute(String id, List<String> networkListeners, String hosts, String state) {

    @SuppressWarnings("unchecked")
    public static VirtualServerAttribute fromParsedMap(Map<String, Object> map) {
        var extraProps = (Map<String, Object>) map.get("extraProperties");
        if (extraProps == null) return null;
        var entity = (Map<String, Object>) extraProps.get("entity");
        if (entity == null) return null;
        String rawListeners = (String) entity.get("networkListeners");
        return new VirtualServerAttribute(
            (String) entity.get("id"),
            rawListeners != null
                ? Arrays.stream(rawListeners.split(",")).map(String::trim).toList()
                : List.of(),
            (String) entity.get("hosts"),
            (String) entity.get("state")
        );
    }
}
