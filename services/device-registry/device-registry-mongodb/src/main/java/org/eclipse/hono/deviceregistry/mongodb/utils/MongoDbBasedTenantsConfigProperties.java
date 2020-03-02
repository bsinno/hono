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

/**
 * Configuration properties for Hono's tenant API.
 */
public final class MongoDbBasedTenantsConfigProperties extends AbstractMongoDbBasedRegistryConfigProperties {

    private static final String DEFAULT_TENANTS_COLLECTION_NAME = "tenants";
    private static int DEFAULT_MAX_AGE_SECONDS_CACHE_MAX_AGE = 180;

    public static int getDefaultMaxAgeSecondsCacheMaxAge() {
        return DEFAULT_MAX_AGE_SECONDS_CACHE_MAX_AGE;
    }

    public static void setDefaultMaxAgeSecondsCacheMaxAge(final int defaultMaxAgeSecondsCacheMaxAge) {
        DEFAULT_MAX_AGE_SECONDS_CACHE_MAX_AGE = defaultMaxAgeSecondsCacheMaxAge;
    }

    @Override
    protected String getDefaultCollectionName() {
        return DEFAULT_TENANTS_COLLECTION_NAME;
    }

}
