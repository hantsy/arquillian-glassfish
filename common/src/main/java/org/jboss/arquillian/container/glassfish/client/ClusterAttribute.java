package org.jboss.arquillian.container.glassfish.client;

import java.util.Map;

/**
 * URL: GET /clusters/cluster/{name}
 * XML: entity map with name, configRef.
 */
public record ClusterAttribute(String name, String configRef) {

    @SuppressWarnings("unchecked")
    public static ClusterAttribute fromParsedMap(Map<String, Object> map) {
        var extraProps = (Map<String, Object>) map.get("extraProperties");
        if (extraProps == null) return null;
        var entity = (Map<String, Object>) extraProps.get("entity");
        if (entity == null) return null;
        return new ClusterAttribute(
            (String) entity.get("name"),
            (String) entity.get("configRef")
        );
    }
}
