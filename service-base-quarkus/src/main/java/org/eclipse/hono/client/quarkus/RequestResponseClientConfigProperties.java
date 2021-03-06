/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.client.quarkus;

import io.quarkus.arc.config.ConfigProperties;


/**
 * Standard {@link RequestResponseClientConfigProperties} which can be bound to environment
 * variables by Quarkus.
 *
 */
@ConfigProperties(namingStrategy = ConfigProperties.NamingStrategy.VERBATIM, failOnMismatchingMember = false)
public class RequestResponseClientConfigProperties extends org.eclipse.hono.client.RequestResponseClientConfigProperties {
}
