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
package org.eclipse.hono.deviceregistry.mongodb.utils;

import java.util.Optional;

@SuppressWarnings( {"checkstyle:MissingJavadocType", "checkstyle:HideUtilityClassConstructor"})
/**
 * Utility class for common functions across device registry services.
 */
public class MongoDbServiceUtils {

    /**
     * Check if a Version is different or not set.
     *
     * @param expectedVersion new version, can unset
     * @param actualValue     current version
     * @return {@code true}, if different version or {@code expectedVersion} is unset
     */
    public static boolean isVersionDifferent(@SuppressWarnings("OptionalUsedAsFieldOrParameterType") final Optional<String> expectedVersion, final String actualValue) {
        return !actualValue.equals(expectedVersion.orElse(actualValue));
    }
}
