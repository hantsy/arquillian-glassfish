package org.jboss.arquillian.container.glassfish.client;

import java.util.List;
import java.util.Map;

/**
 * URL: GET /applications/application/{name}/list-sub-components
 * XML: properties map + children list with moduleInfo entries.
 */
public record SubComponents(Map<String, Object> properties, List<ModuleInfo> children) {

    /**
     * Parsed representation of a {@code moduleInfo} string from the GlassFish
     * {@code list-sub-components} API response.
     * <p>
     * Format: {@code moduleArchiveURI:moduleType:contextRoot}
     */
    public record ModuleInfo(String moduleArchiveURI, String moduleType, String contextRoot) {

        static ModuleInfo fromString(String moduleInfo) {
            if (moduleInfo == null || moduleInfo.isBlank()) {
                return null;
            }
            String[] elements = moduleInfo.split(":");
            if (elements.length < 3) {
                return null;
            }
            return new ModuleInfo(elements[0], elements[1], elements[2]);
        }

        public String resolveContextRoot() {
            return contextRoot.contains("/") ? contextRoot.substring(contextRoot.indexOf("/")) : contextRoot;
        }
    }

    public static SubComponents empty() {
        return new SubComponents(Map.of(), List.of());
    }

    @SuppressWarnings("unchecked")
    public static SubComponents fromParsedMap(Map<String, Object> map) {
        var rawProperties = (Map<String, Object>) map.get("properties");
        var rawChildren = (List<Map<String, Object>>) map.get("children");
        return new SubComponents(
            rawProperties != null ? rawProperties : Map.of(),
            rawChildren != null
                ? rawChildren.stream()
                    .map(SubComponents::parseModuleInfo)
                    .filter(java.util.Objects::nonNull)
                    .toList()
                : List.of()
        );
    }

    private static ModuleInfo parseModuleInfo(Map<String, Object> childMap) {
        var rawProps = (Map<String, Object>) childMap.get("properties");
        String rawModuleInfo = rawProps != null ? (String) rawProps.get("moduleInfo") : null;
        return rawModuleInfo != null ? ModuleInfo.fromString(rawModuleInfo) : null;
    }
}
