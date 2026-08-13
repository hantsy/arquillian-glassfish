package org.jboss.arquillian.container.glassfish.client;

import java.util.Map;

/**
 * URL: GET /servers/server/{name}
 * XML: entity map with name, configRef, nodeRef, lbWeight.
 */
public record ServerAttribute(String name, String configRef, String nodeRef, String lbWeight) {

    @SuppressWarnings("unchecked")
    public static ServerAttribute fromParsedMap(Map<String, Object> map) {
        var extraProps = (Map<String, Object>) map.get("extraProperties");
        if (extraProps == null) return null;
        var entity = (Map<String, Object>) extraProps.get("entity");
        if (entity == null) return null;
        return new ServerAttribute(
            (String) entity.get("name"),
            (String) entity.get("configRef"),
            (String) entity.get("nodeRef"),
            (String) entity.get("lbWeight")
        );
    }
}
