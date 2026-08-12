package org.jboss.arquillian.container.glassfish.client;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses GlassFish admin API XML responses into Maps.
 * Handles {@code <map>}, {@code <entry key="...">}, {@code <string>},
 * {@code <number>}, and {@code <list>} elements.
 */
final class GlassFishXMLParser {

    private GlassFishXMLParser() {
        // utility class
    }

    /**
     * Parse a GlassFish admin API XML response document into a {@code Map<String, Object>}.
     * Values are {@link String}, {@link Long}, {@link Double}, nested {@code Map<String, Object>},
     * or {@code List<Object>}.
     *
     * @param document the XML string, or null/blank
     * @return parsed map, or empty map for null/blank input
     */
    static Map<String, Object> xmlToMap(String document) {
        if (document == null || document.isBlank()) {
            return new HashMap<>();
        }
        try (InputStream input = new ByteArrayInputStream(document.trim().getBytes(StandardCharsets.UTF_8))) {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_VALIDATING, false);
            XMLStreamReader stream = factory.createXMLStreamReader(input);
            while (stream.hasNext()) {
                int event = stream.next();
                if (event == XMLStreamConstants.START_ELEMENT && "map".equals(stream.getLocalName())) {
                    return resolveXmlMap(stream);
                }
            }
        } catch (Exception e) {
            throw new GlassFishClientException("Failed to parse XML response", e);
        }
        return new HashMap<>();
    }

    private static Map<String, Object> resolveXmlMap(XMLStreamReader stream) throws XMLStreamException {
        Map<String, Object> map = new HashMap<>();
        String key = null;
        String elementName = null;

        while (true) {
            int event = stream.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (stream.getLocalName()) {
                    case "entry":
                        key = stream.getAttributeValue(null, "key");
                        String value = stream.getAttributeValue(null, "value");
                        if (value != null) {
                            map.put(key, value);
                            key = null;
                        }
                        break;
                    case "map":
                        Map<String, Object> nestedMap = resolveXmlMap(stream);
                        if (key != null) {
                            map.put(key, nestedMap);
                        }
                        break;
                    case "list":
                        List<Object> nestedList = resolveXmlList(stream);
                        if (key != null) {
                            map.put(key, nestedList);
                        }
                        break;
                    default:
                        elementName = stream.getLocalName();
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("map".equals(stream.getLocalName())) {
                    return map;
                }
                elementName = null;
            } else if (event == XMLStreamConstants.CHARACTERS) {
                String text = stream.getText().trim();
                if (!text.isEmpty() && elementName != null) {
                    if ("number".equals(elementName)) {
                        if (text.contains(".")) {
                            map.put(key, Double.parseDouble(text));
                        } else {
                            map.put(key, Long.parseLong(text));
                        }
                    } else if ("string".equals(elementName)) {
                        map.put(key, text);
                    }
                    elementName = null;
                }
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Object> resolveXmlList(XMLStreamReader stream) throws XMLStreamException {
        List<Object> list = new ArrayList<>();
        String elementName = null;

        while (true) {
            int event = stream.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if ("map".equals(stream.getLocalName())) {
                    list.add(resolveXmlMap(stream));
                } else {
                    elementName = stream.getLocalName();
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("list".equals(stream.getLocalName())) {
                    return list;
                }
                elementName = null;
            } else if (event == XMLStreamConstants.CHARACTERS) {
                String text = stream.getText().trim();
                if (!text.isEmpty() && elementName != null) {
                    if ("number".equals(elementName)) {
                        if (text.contains(".")) {
                            list.add(Double.parseDouble(text));
                        } else {
                            list.add(Long.parseLong(text));
                        }
                    } else if ("string".equals(elementName)) {
                        list.add(text);
                    }
                    elementName = null;
                }
            }
        }
    }
}
