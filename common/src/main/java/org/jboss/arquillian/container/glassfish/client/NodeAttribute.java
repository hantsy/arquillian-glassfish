package org.jboss.arquillian.container.glassfish.client;

import java.util.Map;

/**
 * URL: GET /nodes/node/{name}
 * XML: entity map with name, nodeHost, type, installDir, nodeDir.
 */
public record NodeAttribute(String name, String nodeHost, String type, String installDir, String nodeDir) {

    @SuppressWarnings("unchecked")
    public static NodeAttribute fromParsedMap(Map<String, Object> map) {
        var extraProps = (Map<String, Object>) map.get("extraProperties");
        if (extraProps == null) return null;
        var entity = (Map<String, Object>) extraProps.get("entity");
        if (entity == null) return null;
        return new NodeAttribute(
            (String) entity.get("name"),
            (String) entity.get("nodeHost"),
            (String) entity.get("type"),
            (String) entity.get("installDir"),
            (String) entity.get("nodeDir")
        );
    }
}
