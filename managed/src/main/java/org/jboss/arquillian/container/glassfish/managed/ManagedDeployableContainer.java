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
package org.jboss.arquillian.container.glassfish.managed;

import java.util.Objects;

import org.jboss.arquillian.container.glassfish.GlassFishAdminClient;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.protocol.servlet5.v_5.ServletProtocol;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;

/**
 * Glassfish 6.x managed container using REST deployments
 *
 * @author <a href="http://community.jboss.org/people/LightGuard">Jason Porter</a>
 * @author <a href="http://community.jboss.org/people/dan.j.allen">Dan Allen</a>
 * @author Vineet Reynolds
 */
public class ManagedDeployableContainer implements DeployableContainer<ManagedContainerConfiguration> {

    private ManagedContainerConfiguration configuration;
    private ManagedServerControl serverControl;
    private GlassFishAdminClient adminClient;
    private boolean connectedToRunningServer;

    public Class<ManagedContainerConfiguration> getConfigurationClass() {
        return ManagedContainerConfiguration.class;
    }

    public void setup(ManagedContainerConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");

        this.configuration = configuration;
        this.serverControl = new ManagedServerControl(configuration);
        this.adminClient = new GlassFishAdminClient(configuration);
    }

    public void start() throws LifecycleException {
        if (adminClient.isDASRunning()) {
            if (!configuration.isAllowConnectingToRunningServer()) {
                throw new LifecycleException("The server is already running! "
                    + "Managed containers does not support connecting to running server instances due to the "
                    + "possible harmful effect of connecting to the wrong server. Please stop server before running or "
                    + "change to another type of container.\n"
                    + "To disable this check and allow Arquillian to connect to a running server, "
                    + "set allowConnectingToRunningServer to true in the container configuration");
            }
            // Allow connecting to a running server — skip start-domain
            connectedToRunningServer = true;
            adminClient.start();
            return;
        }

        serverControl.start();
        for (int i = 0; i < configuration.getRetries() && !adminClient.isDASRunning(); i++) {
            try {
                Thread.sleep(configuration.getWaitTimeMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        adminClient.start();
    }

    public void stop() throws LifecycleException {
        if (!connectedToRunningServer) {
            serverControl.stop();
        }
    }

    public ProtocolDescription getDefaultProtocol() {
        return new ProtocolDescription(ServletProtocol.PROTOCOL_NAME);
    }

    public ProtocolMetaData deploy(Archive<?> archive) throws DeploymentException {
        return adminClient.deploy(archive);
    }

    public void undeploy(Archive<?> archive) throws DeploymentException {
        adminClient.undeploy(archive);
    }

    public void deploy(Descriptor descriptor) throws DeploymentException {
        throw new UnsupportedOperationException("Not implemented");
    }

    public void undeploy(Descriptor descriptor) throws DeploymentException {
        throw new UnsupportedOperationException("Not implemented");
    }
}
