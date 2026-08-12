package org.jboss.arquillian.container.glassfish.client;

import java.util.Map;

/**
 * URL: GET /configs/config/{config}/network-config/protocols/protocol/{name}
 * XML: entity map with name, securityEnabled.
 */
public record ProtocolAttribute(String name, Boolean securityEnabled) {

    @SuppressWarnings("unchecked")
    public static ProtocolAttribute fromParsedMap(Map<String, Object> map) {
        var extraProps = (Map<String, Object>) map.get("extraProperties");
        if (extraProps == null) return null;
        var entity = (Map<String, Object>) extraProps.get("entity");
        if (entity == null) return null;
        return new ProtocolAttribute(
            (String) entity.get("name"),
            entity.get("securityEnabled") != null ? Boolean.parseBoolean((String) entity.get("securityEnabled")) : false
        );
    }
}
