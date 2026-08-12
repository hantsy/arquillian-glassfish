package org.jboss.arquillian.container.glassfish.client;

import java.util.Map;

/**
 * URL: GET /configs/config/{config}/system-property/{name}
 * XML: entity map with name, value.
 */
public record SystemPropertyValue(String name, String value) {

    @SuppressWarnings("unchecked")
    public static SystemPropertyValue fromParsedMap(Map<String, Object> map) {
        var extraProps = (Map<String, Object>) map.get("extraProperties");
        if (extraProps == null) return null;
        var entity = (Map<String, Object>) extraProps.get("entity");
        if (entity == null) return null;
        return new SystemPropertyValue(
            (String) entity.get("name"),
            (String) entity.get("value")
        );
    }
}
