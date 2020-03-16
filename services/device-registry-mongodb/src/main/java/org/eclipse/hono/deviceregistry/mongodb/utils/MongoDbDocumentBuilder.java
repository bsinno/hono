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

import org.eclipse.hono.util.AuthenticationConstants;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistryManagementConstants;

import io.vertx.core.json.JsonObject;

/**
 * Utility class for building Json documents for mongodb.
 */
public class MongoDbDocumentBuilder {

    private final JsonObject document = new JsonObject();

    /**
     * TODO.
     *
     * @param tenantId The tenant id.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withTenantId(final String tenantId) {
        document.put(RegistrationConstants.FIELD_PAYLOAD_TENANT_ID, tenantId);
        return this;
    }

    /**
     * TODO.
     *
     * @param deviceId The device id.
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withDeviceId(final String deviceId) {
        document.put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId);
        return this;
    }

    /**
     * add nested CA subjectDn property .
     *
     * @param entityPropKey key where entity properties are stored. e.g.: "tenant" for the {@link org.eclipse.hono.deviceregistry.mongodb.model.TenantDto}
     * @param caSubjectDn   certificate Authority subjectDn
     * @return a reference to this for fluent use.
     */
    public MongoDbDocumentBuilder withCa(final String entityPropKey, final String caSubjectDn) {
        document.put(
                String.format("%s.%s.%s", entityPropKey, RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA, AuthenticationConstants.FIELD_SUBJECT_DN),
                new JsonObject().put("$eq", caSubjectDn)
        );
        return this;
    }

    /**
     * @return the json document.
     */
    public JsonObject create() {
        return document;
    }
}
