package org.jboss.arquillian.container.glassfish.client;

import java.util.Map;

/**
 * URL: GET /applications/application/{name}
 * XML: entity map with name, contextRoot.
 */
public record ApplicationAttribute(String name, String contextRoot) {

    @SuppressWarnings("unchecked")
    public static ApplicationAttribute fromParsedMap(Map<String, Object> map) {
        var extraProps = (Map<String, Object>) map.get("extraProperties");
        if (extraProps == null) return null;
        var entity = (Map<String, Object>) extraProps.get("entity");
        if (entity == null) return null;
        return new ApplicationAttribute(
            (String) entity.get("name"),
            (String) entity.get("contextRoot")
        );
    }
}
