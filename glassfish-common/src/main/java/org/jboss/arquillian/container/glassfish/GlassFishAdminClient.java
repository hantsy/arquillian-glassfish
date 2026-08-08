/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author Z.Paulovics
 */
package org.jboss.arquillian.container.glassfish;

import org.jboss.arquillian.container.glassfish.client.GlassFishResponse;
import org.jboss.arquillian.container.glassfish.client.GlassFishClientException;
import org.jboss.arquillian.container.glassfish.client.GlassFishRestClient;
import org.jboss.arquillian.container.glassfish.client.MultipartBodyPublisher;
import org.jboss.arquillian.container.glassfish.client.NodeAddress;
import org.jboss.arquillian.container.glassfish.client.GlassFishResponse;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.metadata.HTTPContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.client.protocol.metadata.Servlet;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single entry point for the GlassFish admin REST API. It encapsulates the
 * server topology discovery, the deployment/undeployment of ShrinkWrap
 * {@link Archive}s and the DAS health check.
 * <p>
 * Extracted from the GlassFish 3.1 remote container.
 */
public class GlassFishAdminClient {

    /**
     * Admin Server key for the REST request.
     */
    public static final String ADMINSERVER = "server";

    private static final String WEBMODULE = "WebModule";
    private static final String SERVLET = "Servlet";
    private static final String RUNNING_STATUS = "RUNNING";
    private static final String DELETE_OPERATION = "__deleteoperation";

    private final String target;

    private final String adminBaseUrl;

    private final String DASUrl;

    private final GlassFishContainerConfiguration configuration;

    private ServerStartegy serverInstance = null;

    private final GlassFishRestClient restClient;

    private NodeAddress nodeAddress = null;

    private int majorVersion = 6;
    private int minorVersion;

    private String deploymentName;

    private static final Logger log = Logger.getLogger(GlassFishAdminClient.class.getName());

    // GlassFish admin client constructor
    public GlassFishAdminClient(GlassFishContainerConfiguration configuration) {
        this.configuration = configuration;
        this.target = configuration.getTarget();

        NodeAddress dasAddress = new NodeAddress("server",
            configuration.getAdminHost(), configuration.getAdminPort(), 0);
        DASUrl = dasAddress.getURI(configuration.isAdminHttps()).toString();
        adminBaseUrl = dasAddress.getURI(configuration.isAdminHttps())
            .resolve("/management/domain").toString();

        this.restClient = new GlassFishRestClient(configuration, adminBaseUrl);
    }

    /**
     * Start-up the server
     * <p>
     * -   Get the node addresses list associated with the target -   Pull the server instances
     * status form mgm API -   In case of cluster tries to fund an instance which has RUNNING
     * status
     */
    public void start() throws LifecycleException {
        try {
            Map<String, String> standaloneServers;
            Map<String, String> clusters;
            String message;

            try {
                standaloneServers = getServersList();
            } catch (Exception ch) {
                message = "Could not connect to DAS on: " + getDASUrl() + " | "
                    + ch.getCause().getMessage();
                throw new GlassFishClientException(message);
            }

            if (ADMINSERVER.equals(getTarget())) {

                // The "target" is the Admin Server Instance
                serverInstance = new AdminServer();
            } else if (standaloneServers.containsKey(getTarget())) {

                // The "target" is an Standalone Server Instance
                serverInstance = new StandaloneServer();
            } else {

                // The "target" shall be clustered instance(s)
                clusters = getClustersList();

                if (clusters != null && clusters.containsKey(getTarget())) {

                    // Now we have found the cluster specified by the Target attribute
                    serverInstance = new ClusterServer();
                } else {
                    // The "target" attribute can be a domain or misspelled, but neither can be accepted
                    message = "The target property: " + getTarget() + " is not a valid target";
                    throw new GlassFishClientException(message);
                }
            }

            setGlassFishVersion();

            // Fetch the HOST address & HTTP port info from the DAS server
            List<NodeAddress> nodeAddressList = serverInstance.getNodeAddressList();

            if (ADMINSERVER.equals(configuration.getTarget())) {
                // Admin Server must running, otherwise we can not be here
                this.nodeAddress = nodeAddressList.getFirst();
            } else {
                // Returns the nodeAddress if the target instance status is RUNNING
                // In case of cluster, returns the first RUNNING instance (if any) from the list
                this.nodeAddress = runningInstanceFilter(nodeAddressList);
            }
        } catch (GlassFishClientException e) {
            log.log(Level.SEVERE, "Startup failure", e);
            throw new LifecycleException(e.getMessage());
        }
    }

    private void setGlassFishVersion() {
        GlassFishResponse versionResponse = restClient.getVersion();
        if (versionResponse != null && versionResponse.extraProperties() != null) {
            Object versionNumberObj = versionResponse.extraProperties().get("version-number");
            if (versionNumberObj instanceof String version) {
                String[] parts = version.split("\\.");
                if (parts.length > 0) {
                    try {
                        majorVersion = Integer.parseInt(parts[0]);
                    } catch (NumberFormatException ignore) {
                        log.info("Exception getting major version for: " + version);
                    }
                }
                if (parts.length > 1) {
                    try {
                        minorVersion = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException ignore) {
                        log.info("Exception getting minor version for: " + version);
                    }
                }
            }
        }
    }

    /**
     * Filtering on the status of the instances -	If the standalone server instance status is
     * RUNNING, returns the nodeAddress, but throws an exception otherwise. -	In case of cluster,
     * returns the first RUNNING instance from the list, but throws an exception if can not find
     * any.
     *
     * @param nodeAddressList - list of server node addresses
     * @return nodeAddress - if any has RUNNING status
     */
    private NodeAddress runningInstanceFilter(List<NodeAddress> nodeAddressList) {
        var instanceList = restClient.getInstancesList();

        String instanceStatus = null;
        for (var instance : instanceList) {
            for (var node : nodeAddressList) {
                if (instance.get("name").equals(node.serverName())) {
                    instanceStatus = (String) instance.get("status");
                    if (RUNNING_STATUS.equals(instanceStatus)) {
                        return node;
                    }
                }
            }
        }

        String message;
        if (nodeAddressList.size() == 1) {
            message =
                "The " + nodeAddressList.get(0).serverName() + " server-instance status is: "
                    + instanceStatus;
        } else {
            message = "Could not fund any instance with RUNNING status in cluster: " + getTarget();
        }
        throw new GlassFishClientException(message);
    }

    /**
     * Deploy a ShrinkWrap {@link Archive} to the target server or cluster of GlassFish.
     *
     * @param archive - archive to be deployed
     * @return protocolMetaData - the metadata describing the deployed application
     * @throws DeploymentException when the deployment fails
     */
    public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
        if (archive == null) {
            throw new IllegalArgumentException("archive must not be null");
        }
        final String archiveName = archive.getName();
        final ProtocolMetaData protocolMetaData = new ProtocolMetaData();
        try {
            InputStream deployment = archive.as(ZipExporter.class).exportAsInputStream();
            deploymentName = createDeploymentName(archiveName);
            MultipartBodyPublisher.Builder builder = MultipartBodyPublisher.newBuilder()
                .addFilePart("id", archiveName, deployment);
            addDeployFormFields(deploymentName, builder);
            final MultipartBodyPublisher form = builder.build();
            HTTPContext httpContext = doDeploy(deploymentName, form);
            protocolMetaData.addContext(httpContext);
        } catch (GlassFishClientException | IOException e) {
            throw new DeploymentException("Could not deploy " + archiveName, e);
        }
        return protocolMetaData;
    }

    /**
     * Undeploy a previously deployed ShrinkWrap {@link Archive}.
     *
     * @param archive - archive to be undeployed
     * @throws DeploymentException when the undeployment fails
     */
    public void undeploy(Archive<?> archive) throws DeploymentException {
        if (archive == null) {
            throw new IllegalArgumentException("archive must not be null");
        }
        deploymentName = createDeploymentName(archive.getName());
        try {
            final MultipartBodyPublisher form = MultipartBodyPublisher.newBuilder()
                .addField("target", getConfiguration().getTarget(), "text/plain")
                .addField("operation", DELETE_OPERATION, "text/plain")
                .build();
            doUndeploy(this.deploymentName, form);
        } catch (GlassFishClientException | IOException e) {
            throw new DeploymentException("Could not undeploy " + archive.getName(), e);
        }
    }

    /**
     * Do deploy an application defined by a multipart form's fileds to a target server or a cluster
     * of GlassFish 6.x
     *
     * @param name - name of the appliacation
     * @param form - a form of MediaType.MULTIPART_FORM_DATA_TYPE
     * @return subComponents - a map of SubComponents of the application
     */
    private HTTPContext doDeploy(String name, MultipartBodyPublisher form) {
        // Deploy the application on the GlassFish server
        restClient.deployApplication(form);

        // Fetch the list of SubComponents of the application
        GlassFishResponse subComponentsResponse = restClient
            .listSubComponents(name, null);
        var subComponents = (Map<String, String>) subComponentsResponse.extraProperties().get("properties");

        // Build up the HTTPContext object using the nodeAddress information
        int port = nodeAddress.httpPort();
        HTTPContext httpContext = new HTTPContext(nodeAddress.host(), port);

        // Add the servlets to the HTTPContext
        String componentName;
        String contextRoot = getApplicationContextRoot(name);

        if (subComponents != null) {
            for (var subComponent : subComponents.entrySet()) {
                componentName = subComponent.getKey();
                if (WEBMODULE.equals(subComponent.getValue())) {

                    @SuppressWarnings("unchecked")
                    var children = (List<Map<String, Map<String, String>>>) (Object) subComponentsResponse.extraProperties().get("children");
                    // Override the application contextRoot by the webmodul's contextRoot
                    contextRoot = resolveWebModuleContextRoot(componentName, children);
                    resolveWebModuleSubComponents(name, componentName, contextRoot, httpContext);
                } else if (SERVLET.equals(subComponent.getValue())) {
                    httpContext.add(new Servlet(componentName, contextRoot));
                }
            }
        }

        return httpContext;
    }

    /**
     * Undeploy the component
     *
     * @param name - application name form 	- form that include the target & operation fields
     * @return resultMap
     */
    private void doUndeploy(String name, MultipartBodyPublisher form) {
        restClient.undeployApplication(name, form);
    }

    /**
     * Verify if the DAS is running or not.
     */
    public boolean isDASRunning() {
        return restClient.isDASRunning();
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    /**
     * Get the standalone servers list associated with the DAS
     *
     * @param none
     * @return map of standalone servers
     */
    private Map<String, String> getServersList() {
        return restClient.getServersList();
    }

    private Map<String, String> getClustersList() {
        return restClient.getClustersList();
    }

    private String getApplicationContextRoot(String name) {
        Map<String, String> applicationAttributes = restClient.getApplicationAttributes(name);
        return applicationAttributes.get("contextRoot").toString();
    }

    private String resolveWebModuleContextRoot(String componentName,
                                               List<Map<String, Map<String, String>>> modules) {
        String contextRoot = null;
        for (Map<String, Map<String, String>> module : modules) {
            Map<String, String> moduleProperties = module.get("properties");
            if (moduleProperties != null && !moduleProperties.isEmpty()) {
                String moduleInfo = moduleProperties.get("moduleInfo");
                if (moduleInfo.startsWith(componentName)) {
                    // Get the webmodule's contextRoot
                    // The moduleInfo property has the format - moduleArchiveURI:moduleType:contextRoot
                    // The contextRoot is extracted, and removed of any prefixed slash.
                    String[] moduleInfoElements = moduleInfo.split(":");
                    contextRoot = moduleInfoElements[2];
                    contextRoot =
                        contextRoot.contains("/") ? contextRoot.substring(contextRoot.indexOf("/"))
                            : contextRoot;
                }
            } else {
                throw new GlassFishClientException("Cuold not resolve the web-module contextRoot");
            }
        }
        return contextRoot;
    }

    /**
     * Lookup the servlets of WebModule & putt them to the httpContext associated with the
     * application
     *
     * @param name        - application name
     * @param module      - webmodule name
     * @param context     - contextRoot of the web-module
     * @param httpContext - httpContext to be updated
     */
    private void resolveWebModuleSubComponents(String name, String module, String context,
                                               HTTPContext httpContext) {
        // Fetch the list of SubComponents of the application
        Map<String, String> queryParams = Map.of(
            "appname", name,
            "id", module,
            "type", "servlets");

        GlassFishResponse subComponentsResponse = restClient
            .listSubComponents(name, queryParams);
        Map<String, String> subComponents = (Map<String, String>) subComponentsResponse.extraProperties().get("properties");

        String componentName;
        for (Map.Entry<String, String> subComponent : subComponents.entrySet()) {
            componentName = subComponent.getKey();
            httpContext.add(new Servlet(componentName, context));
        }
    }

    // the REST resource path template to retrieve the list of server instances
    protected Map<String, String> getServerInstances(String target) {
        return restClient.getServerInstances(target);
    }

    protected Map<String, String> getServerAttributes(String server) {
        return restClient.getServerAttributes(server);
    }

    protected Map<String, String> getClusterAttributes(String cluster) {
        return restClient.getClusterAttributes(cluster);
    }

    protected String getHostAddress(Map<String, String> serverAttributes) {
        String nodeHost = restClient.getNodeHost(serverAttributes.get("nodeRef"));
        if (nodeHost.equals("localhost")) {
            nodeHost = configuration.getAdminHost();
        }
        return nodeHost;
    }

    private int getSystemProperty(Map<String, String> attributes, String propertyName) {
        Map<String, String> listener = restClient.getSystemProperty(
            attributes.get("configRef"), propertyName);
        return Integer.parseInt(listener.get("value"));
    }

    /**
     * Get the port number defined as a system property in a configuration, and overridden at the
     * level of the server instance.
     *
     * @param server       The name of the server instance
     * @param propertyName The name of the system property to resolve
     * @param defaultValue The default port number to be used, in case the system property is not
     *                     overridden
     * @return The port number stored in the system property
     */
    private int getServerSystemProperty(String server, String propertyName, int defaultValue) {
        Map<String, String> listener = restClient.getServerSystemProperty(server, propertyName);
        return (listener.get("value") != null) ? Integer.parseInt(listener.get("value"))
            : defaultValue;
    }

    protected int getServerInstanceHttpPort(String server, int default_port, boolean secure) {
        String propertyName = (!secure) ? "HTTP_LISTENER_PORT" : "HTTP_SSL_LISTENER_PORT";
        Map<String, String> listener = restClient.getServerSystemProperty(server, propertyName);
        return (listener.get("value") != null) ? Integer.parseInt(listener.get("value"))
            : default_port;
    }

    /**
     * Obtains the list of virtual servers associated with the deployment target. This method omits
     * '__asadmin' in the result, as no deployments can target this virtual server.
     */
    private List<String> getVirtualServers(Map<String, String> attributes) {
        String configRef = attributes.get("configRef")
            .replace("{target}", attributes.get("name"));
        List<Map<String, Object>> virtualServers = restClient.getVirtualServersList(configRef);
        List<String> virtualServerNames = new ArrayList<>();
        for (Map<String, Object> virtualServer : virtualServers) {
            String virtualServerName = (String) virtualServer.get("message");
            if (!virtualServerName.equals("__asadmin")) {
                virtualServerNames.add(virtualServerName);
            }
        }
        return virtualServerNames;
    }

    private List<String> getNetworkListeners(Map<String, String> attributes,
                                             List<String> virtualServers) {
        List<String> networkListeners = new ArrayList<>();
        String configRef = attributes.get("configRef");
        for (String virtualServer : virtualServers) {
            Map<String, String> virtualServerAttributes =
                restClient.getVirtualServerAttributes(configRef, virtualServer);
            String listenerList = virtualServerAttributes.get("networkListeners");
            for (String listener : listenerList.split(",")) {
                networkListeners.add(listener.trim());
            }
        }
        return networkListeners;
    }

    private String getActiveHttpPort(Map<String, String> attributes, List<String> networkListeners,
                                     boolean secure) {
        String configRef = attributes.get("configRef");
        for (String networkListener : networkListeners) {
            Map<String, String> listenerAttributes =
                restClient.getListenerAttributes(configRef, networkListener);
            boolean enabled = Boolean.parseBoolean(listenerAttributes.get("enabled"));
            if (!enabled) {
                continue;
            }
            String protocolName = listenerAttributes.get("protocol");
            boolean secureProtocol = isSecureProtocol(configRef, protocolName);
            if (secure == secureProtocol) {
                return listenerAttributes.get("port");
            }
        }
        return null;
    }

    private boolean isSecureProtocol(String configRef, String protocolName) {
        Map<String, String> protocolAttributes =
            restClient.getProtocolAttributes(configRef, protocolName);
        return Boolean.parseBoolean(protocolAttributes.get("securityEnabled"));
    }

    private static final String SYSTEM_PROPERTY_REGEX = "\\$\\{(.*)\\}";

    /**
     * Get the port number of a network listener. Firstly, this method parses the provided String as
     * a number. If this fails, the provided String is parsed as a system property stored in the
     * format -
     * <blockquote>${systemProperty}</blockquote>. The value of the referenced
     * system property is then read from the GlassFish configuration.
     *
     * @param attributes The attributes which references the configuration (server or cluster
     *                   configuration)
     * @param serverName The name of the server instance
     * @param portNum    The port number or a system property that stores the port number
     * @return The port number as stored in the network listener configuration or in the system
     * property
     */
    private int getPortValue(Map<String, String> attributes, String serverName, String portNum) {
        int portValue = -1;
        try {
            portValue = Integer.parseInt(portNum);
        } catch (NumberFormatException formatEx) {
            Pattern propertyRegex = Pattern.compile(SYSTEM_PROPERTY_REGEX);
            Matcher matcher = propertyRegex.matcher(portNum);
            if (matcher.find()) {
                String propertyName = matcher.group(1);
                portValue = getSystemProperty(attributes, propertyName);
                portValue = getServerSystemProperty(serverName, propertyName, portValue);
            }
        }
        return portValue;
    }

    private GlassFishContainerConfiguration getConfiguration() {
        return configuration;
    }

    private String getTarget() {
        return target;
    }

    /**
     * Get the URL of the DAS server
     *
     * @return URL
     */
    private String getDASUrl() {
        return DASUrl;
    }

    /**
     * Create the deployment name from the archive filename, without the leading slash and without
     * the file extension.
     *
     * @param archiveName - name of the archive
     * @return deployment name
     */
    private String createDeploymentName(String archiveName) {
        String correctedName = archiveName;
        if (correctedName.startsWith("/")) {
            correctedName = correctedName.substring(1);
        }
        if (correctedName.contains(".")) {
            correctedName = correctedName.substring(0, correctedName.lastIndexOf("."));
        }
        return correctedName;
    }

    /**
     * Add the deploy form fields to the multipart form builder, based on the current configuration.
     *
     * @param name    - deployment name
     * @param builder - multipart form builder
     */
    private void addDeployFormFields(String name, MultipartBodyPublisher.Builder builder) {
        builder.addField("name", name, "text/plain");
        builder.addField("target", getConfiguration().getTarget(), "text/plain");
        if (getConfiguration().getLibraries() != null) {
            builder.addField("libraries", getConfiguration().getLibraries(), "text/plain");
        }
        if (getConfiguration().getProperties() != null) {
            builder.addField("properties", getConfiguration().getProperties(), "text/plain");
        }
        if (getConfiguration().getType() != null && "osgi".equals(getConfiguration().getType())) {
            builder.addField("type", getConfiguration().getType(), "text/plain");
        }
    }

    /**
     * The GoF Strategy pattern is used to implement specific algorithm by server type (Admin,
     * Standalone or Clustered server)
     */
    abstract class ServerStartegy {

        /**
         * Address list of the node(s) on GlassFish Appserver
         */
        private List<NodeAddress> nodes = new ArrayList<>();

        protected GlassFishAdminClient glassFishClient;

        protected ServerStartegy() {
        }

        protected List<NodeAddress> getNodes() {
            return nodes;
        }

        protected void setNodes(List<NodeAddress> nodes) {
            this.nodes = nodes;
        }

        protected void addNode(NodeAddress node) {
            nodes.add(node);
        }

        protected GlassFishAdminClient getGlassFishClient() {
            return glassFishClient;
        }

        /**
         * Get the the node address list associated with the target
         *
         * @return list of node address objects
         */
        protected abstract List<NodeAddress> getNodeAddressList();
    }

    class AdminServer extends ServerStartegy {

        public AdminServer() {
            super();
        }

        @Override
        public List<NodeAddress> getNodeAddressList() {
            String nodeHost = "localhost"; // default host
            setNodes(new ArrayList<NodeAddress>());

            // getting the server attributes is happening too fast.  The admin server hasn't started yet.
            int count = 10;
            Map<String, String> serverAttributes = getServerAttributes(ADMINSERVER);
            while (serverAttributes.isEmpty() && count-- > 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignore) {
                }
                serverAttributes = getServerAttributes(ADMINSERVER);
            }

            // Get the host address of the Admin Server
            nodeHost = (String) getConfiguration().getAdminHost();

            // Get the virtual servers and the associated network listeners for the DAS.
            // We'll not verify if the listeners are bound to private IP
            // addresses, or reachable from the Arquillian test client.
            List<String> virtualServers = getVirtualServers(serverAttributes);
            List<String> networkListeners = getNetworkListeners(serverAttributes, virtualServers);
            String httpPortNum = getActiveHttpPort(serverAttributes, networkListeners, false);
            String httpsPortNum = getActiveHttpPort(serverAttributes, networkListeners, true);

            int httpPort = getPortValue(serverAttributes, getTarget(), httpPortNum);
            // A HTTPS listener might not exist in the DAS config.
            // And Arquillian requires a HTTP port for now.
            // So, we'll parse the HTTPS config conditionally.
            int httpsPort = -1;
            if (httpsPortNum != null && !httpsPortNum.equals("")) {
                httpsPort = getPortValue(serverAttributes, getTarget(), httpsPortNum);
            }

            addNode(new NodeAddress(ADMINSERVER, nodeHost, httpPort, httpsPort));

            return getNodes();
        }
    }

    class StandaloneServer extends ServerStartegy {

        public StandaloneServer() {
            super();
        }

        @Override
        public List<NodeAddress> getNodeAddressList() {
            String nodeHost = "localhost"; // default host
            setNodes(new ArrayList<NodeAddress>());

            Map<String, String> serverAttributes = getServerAttributes(getTarget());

            // Get the host address of the Admin Server
            nodeHost = getHostAddress(serverAttributes);

            // Get the virtual servers and the associated network listeners for the DAS.
            // We'll not verify if the listeners are bound to private IP addresses,
            // or reachable from the Arquillian test client.
            List<String> virtualServers = getVirtualServers(serverAttributes);
            List<String> networkListeners = getNetworkListeners(serverAttributes, virtualServers);
            String httpPortNum = getActiveHttpPort(serverAttributes, networkListeners, false);
            String httpsPortNum = getActiveHttpPort(serverAttributes, networkListeners, true);

            int httpPort = getPortValue(serverAttributes, getTarget(), httpPortNum);
            // A HTTPS listener might not exist in the instance config.
            // And Arquillian requires a HTTP port for now.
            // So, we'll parse the HTTPS config conditionally.
            int httpsPort = -1;
            if (httpsPortNum != null && !httpsPortNum.equals("")) {
                httpsPort = getPortValue(serverAttributes, getTarget(), httpsPortNum);
            }

            addNode(new NodeAddress(getTarget(), nodeHost, httpPort, httpsPort));
            return getNodes();
        }
    }

    class ClusterServer extends ServerStartegy {

        public ClusterServer() {
            super();
        }

        @Override
        public List<NodeAddress> getNodeAddressList() {
            String nodeHost = "localhost"; // default host
            setNodes(new ArrayList<NodeAddress>());
            Map<String, String> serverAttributes;

            // Get the REST resource for the cluster attributes, to reference the config-ref later
            Map<String, String> clusterAttributes = getClusterAttributes(getTarget());
            // Fetch the list of server instances of the cluster
            Map<String, String> serverInstances = getServerInstances(getTarget());

            // Get the virtual servers and the associated network listeners for the cluster.
            // GlassFish clusters are homogeneous and the virtual servers and network listeners
            // will be present on every cluster instance; only port numbers for the listener may vary.
            // We'll not verify if the listeners are bound to private IP addresses,
            // or reachable from the Arquillian test client.
            List<String> virtualServers = getVirtualServers(clusterAttributes);
            List<String> networkListeners = getNetworkListeners(clusterAttributes, virtualServers);

            // Obtain a HTTP and a HTTPS port that have been enabled on the
            // virtual server.
            String httpPortNum = getActiveHttpPort(clusterAttributes, networkListeners, false);
            String httpsPortNum = getActiveHttpPort(clusterAttributes, networkListeners, true);

            for (Map.Entry<String, String> serverInstance : serverInstances.entrySet()) {
                String serverName = serverInstance.getKey().toString();

                serverAttributes = getServerAttributes(serverName);
                nodeHost = getHostAddress(serverAttributes);

                int httpPort = getPortValue(clusterAttributes, serverName, httpPortNum);
                // A HTTPS listener might not exist in the cluster config.
                // And Arquillian requires a HTTP port for now.
                // So, we'll parse the HTTPS config conditionally.
                int httpsPort = -1;
                if (httpsPortNum != null && !httpsPortNum.equals("")) {
                    httpsPort = getPortValue(clusterAttributes, serverName, httpsPortNum);
                }

                addNode(new NodeAddress(serverName, nodeHost, httpPort, httpsPort));
            }

            return getNodes();
        }
    }
}
