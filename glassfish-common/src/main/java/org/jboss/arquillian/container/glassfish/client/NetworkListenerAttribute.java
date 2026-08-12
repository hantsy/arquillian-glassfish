package org.jboss.arquillian.container.glassfish.client;

import java.util.Map;

/**
 * URL: GET /configs/config/{config}/network-config/network-listeners/network-listener/{name}
 * XML: entity map with name, enabled, port, protocol.
 */
public record NetworkListenerAttribute(String name, Boolean enabled, String port, String protocol) {

    @SuppressWarnings("unchecked")
    public static NetworkListenerAttribute fromParsedMap(Map<String, Object> map) {
        var extraProps = (Map<String, Object>) map.get("extraProperties");
        if (extraProps == null) return null;
        var entity = (Map<String, Object>) extraProps.get("entity");
        if (entity == null) return null;
        String rawEnabled = (String) entity.get("enabled");
        return new NetworkListenerAttribute(
            (String) entity.get("name"),
            "true".equalsIgnoreCase(rawEnabled),
            (String) entity.get("port"),
            (String) entity.get("protocol")
        );
    }
}
