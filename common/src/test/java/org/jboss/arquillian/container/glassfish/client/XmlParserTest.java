package org.jboss.arquillian.container.glassfish.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class XmlParserTest {

    @Test
    void shouldParseFlatMap() {
        String xml = """
            <map>
              <entry key="exit_code" value="SUCCESS"/>
              <entry key="message" value=""/>
            </map>
            """;

        Map<String, Object> result = GlassFishXMLParser.xmlToMap(xml);

        assertEquals("SUCCESS", result.get("exit_code"));
        assertEquals("", result.get("message"));
    }

    @Test
    void shouldParseNestedMap() {
        String xml = """
            <map>
              <entry key="extraProperties">
                <map>
                  <entry key="childResources">
                    <map>
                      <entry key="server" value="http://localhost:4848/management/domain/servers/server/server"/>
                    </map>
                  </entry>
                </map>
              </entry>
            </map>
            """;

        Map<String, Object> result = GlassFishXMLParser.xmlToMap(xml);

        @SuppressWarnings("unchecked")
        var extraProps = (Map<String, Object>) result.get("extraProperties");
        assertNotNull(extraProps);
        @SuppressWarnings("unchecked")
        var childResources = (Map<String, String>) extraProps.get("childResources");
        assertEquals("http://localhost:4848/management/domain/servers/server/server", childResources.get("server"));
    }

    @Test
    void shouldParseList() {
        String xml = """
            <map>
              <entry key="children">
                <list>
                  <map>
                    <entry key="message" value="__asadmin"/>
                    <entry key="properties">
                      <map/>
                    </entry>
                  </map>
                  <map>
                    <entry key="message" value="server"/>
                    <entry key="properties">
                      <map>
                        <entry key="id" value="server"/>
                        <entry key="networkListeners" value="http-listener-1,http-listener-2"/>
                      </map>
                    </entry>
                  </map>
                </list>
              </entry>
            </map>
            """;

        Map<String, Object> result = GlassFishXMLParser.xmlToMap(xml);

        @SuppressWarnings("unchecked")
        var children = (List<Map<String, Object>>) result.get("children");
        assertEquals(2, children.size());
        assertEquals("__asadmin", children.get(0).get("message"));
        assertEquals("server", children.get(1).get("message"));
    }

    @Test
    void shouldParseNumbers() {
        String xml = """
            <map>
              <entry key="port">
                <number>8080</number>
              </entry>
              <entry key="weight">
                <number>1.5</number>
              </entry>
            </map>
            """;

        Map<String, Object> result = GlassFishXMLParser.xmlToMap(xml);

        assertEquals(8080L, result.get("port"));
        assertEquals(1.5, result.get("weight"));
    }

    @Test
    void shouldParseStringElements() {
        String xml = """
            <map>
              <entry key="entity">
                <map>
                  <entry key="name">
                    <string>server</string>
                  </entry>
                  <entry key="configRef">
                    <string>server-config</string>
                  </entry>
                </map>
              </entry>
            </map>
            """;

        Map<String, Object> result = GlassFishXMLParser.xmlToMap(xml);

        @SuppressWarnings("unchecked")
        var entity = (Map<String, String>) result.get("entity");
        assertEquals("server", entity.get("name"));
        assertEquals("server-config", entity.get("configRef"));
    }

    @Test
    void shouldParseNetworkListenerFromValueAttributes() {
        String xml = """
            <map>
              <entry key="extraProperties">
                <map>
                  <entry key="entity">
                    <map>
                      <entry key="name" value="http-listener-1"/>
                      <entry key="enabled" value="true"/>
                      <entry key="port" value="8080"/>
                      <entry key="protocol" value="http-listener"/>
                    </map>
                  </entry>
                </map>
              </entry>
            </map>
            """;

        Map<String, Object> map = GlassFishXMLParser.xmlToMap(xml);
        var attr = NetworkListenerAttribute.fromParsedMap(map);

        assertEquals("http-listener-1", attr.name());
        assertTrue(attr.enabled());
        assertEquals("8080", attr.port());
        assertEquals("http-listener", attr.protocol());
    }

    @Test
    void shouldReturnNullServerAttributeWhenEntityMissing() {
        String xml = """
            <map>
              <entry key="exit_code" value="SUCCESS"/>
              <entry key="extraProperties">
                <map/>
              </entry>
            </map>
            """;

        Map<String, Object> map = GlassFishXMLParser.xmlToMap(xml);
        var attr = ServerAttribute.fromParsedMap(map);

        assertNull(attr);
    }

    @Test
    void shouldReturnNullClusterAttributeWhenEntityMissing() {
        String xml = """
            <map>
              <entry key="exit_code" value="SUCCESS"/>
              <entry key="extraProperties">
                <map/>
              </entry>
            </map>
            """;

        Map<String, Object> map = GlassFishXMLParser.xmlToMap(xml);
        var attr = ClusterAttribute.fromParsedMap(map);

        assertNull(attr);
    }

    @Test
    void shouldHandleNullInput() {
        Map<String, Object> result = GlassFishXMLParser.xmlToMap(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldHandleEmptyString() {
        Map<String, Object> result = GlassFishXMLParser.xmlToMap("");
        assertTrue(result.isEmpty());
    }
}
