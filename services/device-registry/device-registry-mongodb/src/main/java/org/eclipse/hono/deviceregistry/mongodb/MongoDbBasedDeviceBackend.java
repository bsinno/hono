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

import io.opentracing.Span;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import org.eclipse.hono.deviceregistry.mongodb.services.MongoDbBasedCredentialsService;
import org.eclipse.hono.deviceregistry.mongodb.services.MongoDbBasedRegistrationService;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.device.AutoProvisioningEnabledDeviceBackend;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.RegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * TODO.
 */
@Repository
@Qualifier("backend")
@ConditionalOnProperty(name = "hono.app.type", havingValue = "mongodb", matchIfMissing = true)
public class MongoDbBasedDeviceBackend extends AbstractVerticle
        implements AutoProvisioningEnabledDeviceBackend, Verticle {

    private static final Logger log = LoggerFactory.getLogger(MongoDbBasedDeviceBackend.class);
    private final MongoDbBasedCredentialsService credentialsService;
    private MongoDbBasedRegistrationService registrationService;
    private MongoDbConfigProperties config;
    private MongoClient mongoClient;

    /**
     * Create a new instance.
     *
     * @param registrationService an implementation of registration service.
     * @param credentialsService  an implementation of credentials service.
     */
    @Autowired
    public MongoDbBasedDeviceBackend(
            @Qualifier("serviceImpl") final MongoDbBasedRegistrationService registrationService,
            @Qualifier("serviceImpl") final MongoDbBasedCredentialsService credentialsService) {
        this.registrationService = registrationService;
        this.credentialsService = credentialsService;
    }

    /**
     * Sets the configuration properties for this service.
     *
     * @param configuration The properties.
     */
    @Autowired
    public void setConfig(final MongoDbConfigProperties configuration) {
        this.config = configuration;
    }

    @Override public void get(final String tenantId, final String type, final String authId, final Span span,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

    }

    @Override public void get(final String tenantId, final String type, final String authId,
            final JsonObject clientContext, final Span span,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

    }

    @Override public void updateCredentials(final String tenantId, final String deviceId,
            final List<CommonCredential> credentials,
            final Optional<String> resourceVersion, final Span span,
            final Handler<AsyncResult<OperationResult<Void>>> resultHandler) {

    }

    @Override public void readCredentials(final String tenantId, final String deviceId, final Span span,
            final Handler<AsyncResult<OperationResult<List<CommonCredential>>>> resultHandler) {

    }

    @Override public void createDevice(final String tenantId, final Optional<String> deviceId, final Device device,
            final Span span, final Handler<AsyncResult<OperationResult<Id>>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resultHandler);

        final Promise<OperationResult<Id>> createAttempt = Promise.promise();
        registrationService.createDevice(tenantId, deviceId, device, span, createAttempt);

        createAttempt.future()
                .compose(r -> {

                    if (r.getStatus() != HttpURLConnection.HTTP_CREATED) {
                        return Future.succeededFuture(r);
                    }

                    // now create the empty credentials set
                    final Promise<OperationResult<Void>> f = Promise.promise();
                    credentialsService.updateCredentials(
                            tenantId,
                            r.getPayload().getId(),
                            Collections.emptyList(),
                            Optional.empty(),
                            span,
                            f);

                    // pass on the original result
                    return f.future().map(r);

                })
                .setHandler(resultHandler);

    }

    @Override public void readDevice(final String tenantId, final String deviceId, final Span span,
            final Handler<AsyncResult<OperationResult<Device>>> resultHandler) {
        registrationService.readDevice(tenantId, deviceId, span, resultHandler);
    }

    @Override public void updateDevice(final String tenantId, final String deviceId, final Device device,
            final Optional<String> resourceVersion, final Span span,
            final Handler<AsyncResult<OperationResult<Id>>> resultHandler) {
        registrationService.updateDevice(tenantId, deviceId, device, resourceVersion, span, resultHandler);

    }

    @Override public void deleteDevice(final String tenantId, final String deviceId,
            final Optional<String> resourceVersion, final Span span,
            final Handler<AsyncResult<Result<Void>>> resultHandler) {
        registrationService.deleteDevice(tenantId, deviceId, resourceVersion, span, resultHandler);


    }

    @Override public void assertRegistration(final String tenantId, final String deviceId,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {

    }

    @Override public void assertRegistration(final String tenantId, final String deviceId, final String gatewayId,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {

    }
}
