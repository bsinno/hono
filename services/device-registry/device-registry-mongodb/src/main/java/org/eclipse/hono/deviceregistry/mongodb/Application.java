/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.deviceregistry.mongodb;

import org.eclipse.hono.service.AbstractBaseApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 *  A Spring Boot application implements a mongodb based Hono's device registry.
 */
@Configuration
@EnableAutoConfiguration
public class Application extends AbstractBaseApplication {

    /**
     * Starts the Device Registry Server.
     *
     * @param args command line arguments to pass to the server.
     */
    public static void main(final String[] args) {
        System.out.println("Dev reg mongo start");
        SpringApplication.run(Application.class, args);
    }
}
