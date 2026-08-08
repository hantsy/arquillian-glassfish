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
package org.jboss.arquillian.container.glassfish.clientutils;

import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.protocol.metadata.HTTPContext;

import java.util.Map;

public interface GlassFishClient {

    /**
     * Admin Server key for the REST request.
     */
    String ADMINSERVER = "server";

    /**
     * Start-up the server
     */
    void startUp();

    /**
     * Do deploy an application defined by a multipart form's data
     * to the target server or cluster of GlassFish.
     *
     * @param name - name of the application
     * @param form - multipart form containing the deployment archive and fields
     * @return subComponents - a map of SubComponents of the application
     */
    HTTPContext doDeploy(String name, MultipartBody form) throws DeploymentException;

    /**
     * Do undeploy the application
     *
     * @param name - application name
     * @param form - multipart form containing undeploy fields
     * @return responseMap
     */
    Map<String, Object> doUndeploy(String name, MultipartBody form);

    /**
     * Verify whether the Domain Administration Server is running.
     */
    boolean isDASRunning();
}
