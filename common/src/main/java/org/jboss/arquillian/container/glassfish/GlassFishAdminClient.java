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

import org.jboss.arquillian.container.glassfish.client.*;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.metadata.HTTPContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.client.protocol.metadata.Servlet;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
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

    private final String target;

    private final String adminBaseUrl;

    private final String DASUrl;

    private final GlassFishContainerConfiguration configuration;

    private ServerStrategy serverInstance = null;

    private final GlassFishRestClient restClient;

    private NodeAddress nodeAddress = null;

    private int majorVersion = 6;
    private int minorVersion;

    // private String deploymentName;

    private static final Logger log = Logger.getLogger(GlassFishAdminClient.class.getName());

    // GlassFish admin client constructor
    public GlassFishAdminClient(GlassFishContainerConfiguration configuration) {
        this(configuration, createRestClient(configuration));
    }

    /**
     * Package-private constructor for testing with a mock {@link GlassFishRestClient}.
     */
    GlassFishAdminClient(GlassFishContainerConfiguration configuration, GlassFishRestClient restClient) {
        this.configuration = configuration;
        this.target = configuration.getTarget();

        String scheme = configuration.isAdminHttps() ? "https://" : "http://";
        DASUrl = scheme + configuration.getAdminHost() + ":" + configuration.getAdminPort();
        adminBaseUrl = DASUrl + "/management/domain";

        this.restClient = restClient;
    }

    private static GlassFishRestClient createRestClient(GlassFishContainerConfiguration configuration) {
        String scheme = configuration.isAdminHttps() ? "https://" : "http://";
        String url = scheme + configuration.getAdminHost() + ":" + configuration.getAdminPort() + "/management/domain";
        return new GlassFishRestClient(url,
            configuration.getAdminUser(), configuration.getAdminPassword(),
            configuration.isDebugRequests());
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
            try {
                standaloneServers = getServersList();
            } catch (Exception ch) {
                throw new GlassFishClientException(
                    "Could not connect to DAS on: " + getDASUrl() + " | " + ch);
            }

            if (ADMINSERVER.equals(getTarget())) {
                serverInstance = new AdminServer();
            } else if (standaloneServers.containsKey(getTarget())) {
                serverInstance = new StandaloneServer();
            } else {
                var clusters = getClustersList();
                if (clusters == null || !clusters.containsKey(getTarget())) {
                    throw new GlassFishClientException(
                        "The target property: " + getTarget() + " is not a valid target");
                }
                serverInstance = new ClusterServer();
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
        var versionResponse = restClient.getVersion();
        if (versionResponse != null && versionResponse.versionNumber() != null) {
            String version = versionResponse.versionNumber();
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
        var instanceList = restClient.getInstanceList();

        String instanceStatus = null;
        for (var instance : instanceList) {
            for (var node : nodeAddressList) {
                if (instance.name().equals(node.serverName())) {
                    instanceStatus = instance.status();
                    if (RUNNING_STATUS.equals(instanceStatus)) {
                        return node;
                    }
                }
            }
        }

        throw new GlassFishClientException(nodeAddressList.size() == 1
            ? "The " + nodeAddressList.getFirst().serverName() + " server-instance status is: " + instanceStatus
            : "Could not fund any instance with RUNNING status in cluster: " + getTarget());
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
        final String deploymentName = extractDeploymentName(archiveName);
        InputStream archiveData = archive.as(ZipExporter.class).exportAsInputStream();
        final ProtocolMetaData protocolMetaData = new ProtocolMetaData();
        try {
            DeployApplicationRequest request = DeployApplicationRequest
                .builder(archiveName, archiveData, deploymentName, configuration.getTarget())
                .libraries(configuration.getLibraries())
                .properties(configuration.getProperties())
                .type(configuration.getType())
                .build();
            restClient.deployApplication(request);
            HTTPContext httpContext = doDeploy(deploymentName);
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
        String archiveName = archive.getName();
        String deploymentName = extractDeploymentName(archiveName);
        try {
            doUndeploy(deploymentName);
        } catch (GlassFishClientException e) {
            throw new DeploymentException("Could not undeploy " + archiveName, e);
        }
    }

    /**
     * Fetch the deployed application's sub-components and build the HTTP context.
     * Sub-component discovery is best-effort — if it fails or returns no servlets,
     * a fallback entry is added using the application's context root so that
     * Arquillian can still construct a base URI for testable=false tests.
     */
    private HTTPContext doDeploy(String name) {
        int port = nodeAddress.httpPort();
        HTTPContext httpContext = new HTTPContext(nodeAddress.host(), port);
        String contextRoot = getApplicationContextRoot(name);
        boolean foundServlets = false;

        try {
            var response = restClient.getSubComponents(name);
            for (var entry : response.properties().entrySet()) {
                String componentName = entry.getKey();
                if (WEBMODULE.equals(entry.getValue())) {
                    // For EARs: resolve the web module's context root from children
                    contextRoot = resolveWebModuleContextRoot(componentName, response.children());
                    resolveWebModuleSubComponents(name, componentName, contextRoot, httpContext);
                    foundServlets = true;
                } else if (SERVLET.equals(entry.getValue())) {
                    httpContext.add(new Servlet(componentName, contextRoot));
                    foundServlets = true;
                }
            }
        } catch (GlassFishClientException e) {
            // list-sub-components may fail for EAR deployments where the module
            // name doesn't match the application name, or for apps without
            // web modules (EJB JARs, EARs without WARs).
            log.fine("Sub-component discovery failed for " + name + ": " + e.getMessage());
        }

        if (!foundServlets) {
            // Fallback: add the context root as a placeholder servlet so that
            // Arquillian can construct a base URI even without discovered servlets.
            // This is sufficient for testable=false tests which just need the
            // host, port, and context root to reach the deployed application.
            httpContext.add(new Servlet(name, contextRoot));
        }

        return httpContext;
    }

    /**
     * Resolve a web module's context root from the children list.
     * Each child has a parsed {@link SubComponents.ModuleInfo} with the
     * {@code moduleArchiveURI:moduleType:contextRoot} parsed from the API response.
     */
    private String resolveWebModuleContextRoot(String componentName, List<SubComponents.ModuleInfo> children) {
        for (var mi : children) {
            if (mi.moduleArchiveURI() != null
                && mi.moduleArchiveURI().startsWith(componentName)) {
                return mi.resolveContextRoot();
            }
        }
        throw new GlassFishClientException("Could not resolve web-module context root for " + componentName);
    }

    /**
     * Query the sub-components of a web module within an EAR and add its servlets.
     */
    private void resolveWebModuleSubComponents(String appName, String module, String context, HTTPContext httpContext) {
        var response = restClient.getSubComponents(appName, module, "servlets");
        for (var entry : response.properties().entrySet()) {
            httpContext.add(new Servlet(entry.getKey(), context));
        }
    }

    private void doUndeploy(String name) {
        try {
            restClient.undeployApplication(name, configuration.getTarget());
        } catch (IOException e) {
            throw new GlassFishClientException(e);
        }
    }

    /**
     * Verify if the DAS is running or not.
     */
    public boolean isDASRunning() {
        return restClient.isDASRunning();
    }

    /**
     * Get the standalone servers list associated with the DAS
     *
     * @return map of standalone servers
     */
    private Map<String, String> getServersList() {
        return restClient.getServersList().servers();
    }

    private Map<String, String> getClustersList() {
        return restClient.getClustersList().clusters();
    }

    private String getApplicationContextRoot(String name) {
        var info = restClient.getApplicationAttributes(name);
        if (info != null && info.contextRoot() != null) {
            return info.contextRoot();
        }
        // Fallback: for EARs the context root is per-module, not on the app
        // itself. Use "/" + name which is GlassFish's default convention.
        return "/" + name;
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
    private String extractDeploymentName(String archiveName) {
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
     * The GoF Strategy pattern is used to implement specific algorithm by server type (Admin,
     * Standalone or Clustered server)
     */
    abstract class ServerStrategy {

        /**
         * Address list of the node(s) on GlassFish Appserver
         */
        private List<NodeAddress> nodes = new ArrayList<>();

        protected GlassFishAdminClient adminClient;

        protected ServerStrategy() {
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

        protected GlassFishAdminClient getAdminClient() {
            return adminClient;
        }

        protected abstract List<NodeAddress> getNodeAddressList();

        protected String getHostAddress(ServerAttribute serverAttributes) {
            String nodeHost = restClient.getNodeConfig(serverAttributes.nodeRef()).nodeHost();
            if (nodeHost.equals("localhost")) {
                nodeHost = configuration.getAdminHost();
            }
            return nodeHost;
        }

        protected List<String> getVirtualServers(String configRef, String targetName) {
            return restClient.getVirtualServers(configRef, targetName);
        }

        protected List<String> getNetworkListeners(String configRef, List<String> virtualServers) {
            return virtualServers.stream()
                .flatMap(vs -> restClient.getVirtualServerAttributes(configRef, vs).networkListeners().stream())
                .toList();
        }

        protected String getActiveHttpPort(String configRef, List<String> networkListeners, boolean secure) {
            return networkListeners.stream()
                .map(nl -> restClient.getListenerAttributes(configRef, nl))
                .filter(l -> l.enabled())
                .filter(l -> secure == isSecureProtocol(configRef, l.protocol()))
                .map(NetworkListenerAttribute::port)
                .findFirst()
                .orElse(null);
        }

        protected boolean isSecureProtocol(String configRef, String protocolName) {
            return restClient.getProtocolAttributes(configRef, protocolName).securityEnabled();
        }

        protected int getPortValue(String configRef, String serverName, String portNum) {
            try {
                return Integer.parseInt(portNum);
            } catch (NumberFormatException e) {
                var m = Pattern.compile("\\$\\{(.*)\\}").matcher(portNum);
                if (m.find()) {
                    String name = m.group(1);
                    var cv = restClient.getSystemProperty(configRef, name);
                    var sv = restClient.getServerSystemProperty(serverName, name);
                    if (sv != null && !sv.isBlank()) return Integer.parseInt(sv);
                    return cv != null ? Integer.parseInt(cv) : -1;
                }
                return -1;
            }
        }
    }

    class AdminServer extends ServerStrategy {

        public AdminServer() {
            super();
        }

        @Override
        public List<NodeAddress> getNodeAddressList() {
            String nodeHost = "localhost"; // default host
            setNodes(new ArrayList<NodeAddress>());

            // getting the server attributes is happening too fast.  The admin server hasn't started yet.
            int count = 10;
            ServerAttribute serverAttributes = restClient.getServerAttributes(ADMINSERVER);
            while ((serverAttributes == null || serverAttributes.configRef() == null) && count-- > 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignore) {
                }
                serverAttributes = restClient.getServerAttributes(ADMINSERVER);
            }

            if (serverAttributes == null || serverAttributes.configRef() == null) {
                throw new GlassFishClientException("Could not retrieve admin server attributes after retries");
            }

            nodeHost = getConfiguration().getAdminHost();

            String configRef = serverAttributes.configRef();
            List<String> virtualServers = getVirtualServers(configRef, ADMINSERVER);
            List<String> networkListeners = getNetworkListeners(configRef, virtualServers);
            String httpPortNum = getActiveHttpPort(configRef, networkListeners, false);
            String httpsPortNum = getActiveHttpPort(configRef, networkListeners, true);

            int httpPort = getPortValue(configRef, ADMINSERVER, httpPortNum);
            int httpsPort = httpsPortNum != null ? getPortValue(configRef, ADMINSERVER, httpsPortNum) : -1;
            addNode(new NodeAddress(ADMINSERVER, nodeHost, httpPort, httpsPort));
            return getNodes();
        }
    }

    class StandaloneServer extends ServerStrategy {

        public StandaloneServer() {
            super();
        }

        @Override
        public List<NodeAddress> getNodeAddressList() {
            setNodes(new ArrayList<>());

            var serverAttributes = restClient.getServerAttributes(getTarget());
            String nodeHost = getHostAddress(serverAttributes);
            String configRef = serverAttributes.configRef();

            List<String> virtualServers = getVirtualServers(configRef, getTarget());
            List<String> networkListeners = getNetworkListeners(configRef, virtualServers);
            String httpPortNum = getActiveHttpPort(configRef, networkListeners, false);
            String httpsPortNum = getActiveHttpPort(configRef, networkListeners, true);

            int httpPort = getPortValue(configRef, getTarget(), httpPortNum);
            int httpsPort = httpsPortNum != null ? getPortValue(configRef, getTarget(), httpsPortNum) : -1;
            addNode(new NodeAddress(getTarget(), nodeHost, httpPort, httpsPort));
            return getNodes();
        }
    }

    class ClusterServer extends ServerStrategy {

        public ClusterServer() {
            super();
        }

        @Override
        public List<NodeAddress> getNodeAddressList() {
            var clusterAttributes = restClient.getClusterAttributes(getTarget());
            var serverInstances = restClient.getServerInstances(getTarget()).instances();
            String configRef = clusterAttributes.configRef();

            List<String> virtualServers = getVirtualServers(configRef, getTarget());
            List<String> networkListeners = getNetworkListeners(configRef, virtualServers);
            String httpPortNum = getActiveHttpPort(configRef, networkListeners, false);
            String httpsPortNum = getActiveHttpPort(configRef, networkListeners, true);

            List<NodeAddress> clusterNodes = serverInstances.keySet().stream()
                .map(serverName -> {
                    var attrs = restClient.getServerAttributes(serverName);
                    String host = getHostAddress(attrs);
                    int httpPort = getPortValue(configRef, serverName, httpPortNum);
                    int httpsPort = getPortValue(configRef, serverName, httpsPortNum);
                    return new NodeAddress(serverName, host, httpPort, httpsPort);
                })
                .toList();
            setNodes(new ArrayList<>(clusterNodes));
            return getNodes();
        }
    }

    public record NodeAddress(String serverName, String host, int httpPort, int httpsPort) {
        public static final String HTTP_PROTOCOL_PREFIX = "http://";
        public static final String HTTPS_PROTOCOL_PREFIX = "https://";

        public NodeAddress() {
            this("server", "localhost", 0, 0);
        }

        public NodeAddress(String host) {
            this("server", host, 0, 0);
        }

        public URI getURI() {
            return getURI(false);
        }

        public URI getURI(boolean secure) {
            return URI.create(getHttpProtocolPrefix(secure) + host + ":" + (!secure ? httpPort : httpsPort));
        }

        public static String getHttpProtocolPrefix(boolean secure) {
            return secure ? HTTPS_PROTOCOL_PREFIX : HTTP_PROTOCOL_PREFIX;
        }
    }
}
