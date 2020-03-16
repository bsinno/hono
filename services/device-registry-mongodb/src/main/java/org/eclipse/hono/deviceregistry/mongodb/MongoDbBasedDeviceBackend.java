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

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedCredentialsService;
import org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedRegistrationService;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.device.AutoProvisioningEnabledDeviceBackend;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.RegistrationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import io.opentracing.Span;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;

/**
 * TODO.
 */
@Repository
@Qualifier("backend")
@ConditionalOnProperty(name = "hono.app.type", havingValue = "mongodb", matchIfMissing = true)
public class MongoDbBasedDeviceBackend extends AbstractVerticle
        implements AutoProvisioningEnabledDeviceBackend, Verticle {

    @Override
    public Future<CredentialsResult<JsonObject>> get(final String tenantId, final String type, final String authId, final Span span) {
        return null;
    }

    @Override
    public Future<CredentialsResult<JsonObject>> get(final String tenantId, final String type, final String authId,
                                                     final JsonObject clientContext, final Span span) {
        return null;
    }

    @Override
    public Future<OperationResult<Void>> updateCredentials(final String tenantId, final String deviceId,
                                                           final List<CommonCredential> credentials,
                                                           final Optional<String> resourceVersion, final Span span) {
        return null;
    }

    @Override
    public Future<OperationResult<List<CommonCredential>>> readCredentials(final String tenantId, final String deviceId, final Span span) {
        return null;
    }

    @Override
    public Future<OperationResult<Id>> createDevice(final String tenantId, final Optional<String> deviceId, final Device device, final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        return null;
    }

    @Override
    public Future<OperationResult<Device>> readDevice(final String tenantId, final String deviceId, final Span span) {
        return null;
    }

    @Override
    public Future<OperationResult<Id>> updateDevice(final String tenantId, final String deviceId, final Device device,
                                                    final Optional<String> resourceVersion, final Span span) {
        return null;
    }

    @Override
    public Future<Result<Void>> deleteDevice(final String tenantId, final String deviceId,
                                             final Optional<String> resourceVersion, final Span span) {
        return null;
    }

    @Override
    public Future<RegistrationResult> assertRegistration(final String tenantId, final String deviceId) {
        return null;
    }

    @Override
    public Future<RegistrationResult> assertRegistration(final String tenantId, final String deviceId, final String gatewayId) {
        return null;
    }
}
