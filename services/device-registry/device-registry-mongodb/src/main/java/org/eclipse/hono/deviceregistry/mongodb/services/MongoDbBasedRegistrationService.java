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
package org.eclipse.hono.deviceregistry.mongodb.services;

import io.opentracing.Span;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.MongoClientDeleteResult;
import org.eclipse.hono.deviceregistry.config.DeviceRegistrationCommonConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.MongoDbConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.DeviceDto;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbCallExecutor;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDocumentBuilder;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbErrorHandler;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.deviceregistry.util.Versioned;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * TODO.
 */
@Component
@Qualifier("serviceImpl")
@ConditionalOnProperty(name = "hono.app.type", havingValue = "mongodb", matchIfMissing = true)
public class MongoDbBasedRegistrationService extends AbstractVerticle
        implements DeviceManagementService, RegistrationService {

    private static final String DEVICES_COLLECTION = "devices";
    private static final Logger log = LoggerFactory.getLogger(MongoDbBasedRegistrationService.class);
    private MongoDbConfigProperties mongoDbConfig;
    private MongoClient mongoClient;
    private DeviceRegistrationCommonConfigProperties config;

    @Override
    public void start(final Promise<Void> startPromise) {
        final MongoDbCallExecutor executor = new MongoDbCallExecutor(vertx, mongoDbConfig);
        mongoClient = executor.getMongoClient();

        executor.createCollectionIndex(DEVICES_COLLECTION,
                new JsonObject().put(RegistrationConstants.FIELD_PAYLOAD_TENANT_ID, 1)
                        .put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, 1),
                new IndexOptions().unique(true))
                .map(success -> {
                    startPromise.complete();
                    return null;
                }).onFailure(reason -> {
            log.error("Index creation failed", reason);
            startPromise.fail(reason);
        });
    }

    @Override
    public void stop(final Promise<Void> stopPromise) {
        mongoClient.close();
        stopPromise.complete();
    }

    @Autowired
    public void setMongoDBConfig(final MongoDbConfigProperties config) {
        this.mongoDbConfig = config;
    }

    public MongoDbConfigProperties getMongoDbConfig() {
        return mongoDbConfig;
    }

    public DeviceRegistrationCommonConfigProperties getConfig() {
        return config;
    }

    @Autowired
    public void setConfig(final DeviceRegistrationCommonConfigProperties config) {
        this.config = config;
    }

    @Override
    public void createDevice(final String tenantId, final Optional<String> deviceId,
                             final Device device,
                             final Span span,
                             final Handler<AsyncResult<OperationResult<Id>>> resultHandler) {

        //TODO. Is it enough to use randomuuid or add generate randomUUID
        final String deviceIdValue = deviceId
                .orElseGet(() -> UUID.randomUUID().toString());
//        TODO validate max devices per tenant.

        span.log(String.format("Registering  device [%s]", deviceIdValue));
//        final Map<String, Object> items = new HashMap<>();
        final Versioned<Device> newDevice = new Versioned<>(device);
        final DeviceDto newDeviceDto = new DeviceDto(tenantId, deviceIdValue, newDevice.getValue(),
                newDevice.getVersion(), Instant.now());

        final Promise<String> insertDevicePromise = Promise.promise();
        mongoClient.insert(DEVICES_COLLECTION, JsonObject.mapFrom(newDeviceDto), insertDevicePromise);
        insertDevicePromise
                .future()
                .compose(success -> {
                    log.info("Created device [{}]", deviceIdValue);
                    return Future.succeededFuture(
                            OperationResult.ok(
                                    HttpURLConnection.HTTP_CREATED,
                                    Id.of(deviceIdValue),
                                    Optional.empty(),
                                    Optional.of(newDevice.getVersion())));
                })
                .recover(error -> {
                    if (MongoDbErrorHandler.ifDuplicateKeyError(error)) {
                        log.error("Device [{}] already exist for tenant [{}]", deviceIdValue, tenantId);
                        TracingHelper.logError(span,
                                String.format("Device [%s] already exist for tenant [%s]", deviceIdValue, tenantId));
                        return Future.succeededFuture(
                                OperationResult.empty(HttpURLConnection.HTTP_CONFLICT));
                    } else {
                        log.error("Error adding device [{}] for tenant [{}]", deviceIdValue, tenantId);
                        TracingHelper.logError(span, String.format("Error adding device [%s]", deviceIdValue));
                        return Future.succeededFuture(
                                OperationResult.empty(HttpURLConnection.HTTP_INTERNAL_ERROR));
                    }
                })
                .setHandler(resultHandler);
    }

    @Override
    public void readDevice(final String tenantId, final String deviceId, final Span span,
                           final Handler<AsyncResult<OperationResult<Device>>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resultHandler);

        fetchDeviceFromMongoDb(tenantId, deviceId, span)
                .compose(device -> {
                    log.trace("Device [{}] found.", deviceId);
                    return Optional.ofNullable(device)
                            .map(ok -> Future.succeededFuture(OperationResult.ok(HttpURLConnection.HTTP_OK,
                                    device.getJsonObject("device").mapTo(Device.class),
                                    Optional.ofNullable(DeviceRegistryUtils.getCacheDirective(getConfig().getCacheMaxAge())),
                                    Optional.ofNullable(device.getString("version")))))
                            .orElseGet(() -> {
                                log.debug("Device [{}] not found.", deviceId);
                                TracingHelper.logError(span, String.format("Device [%s] not found.", deviceId));
                                return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_NOT_FOUND));
                            });
                })
                .recover(error -> {
                    log.error("Error retrieving device [{}]", deviceId, error);
                    return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_INTERNAL_ERROR));
                }).setHandler(resultHandler);
    }

    private Future<JsonObject> fetchDeviceFromMongoDb(final String tenantId, final String deviceId, final Span span) {
        final JsonObject findDeviceQuery = new MongoDbDocumentBuilder()
                .withTenantId(tenantId)
                .withDeviceId(deviceId)
                .create();
        final Promise<JsonObject> readDevicePromise = Promise.promise();
        mongoClient.findOne(DEVICES_COLLECTION, findDeviceQuery, null, readDevicePromise);
        return readDevicePromise.future();
    }

    @Override
    public void updateDevice(final String tenantId, final String deviceId, final Device device,
                             final Optional<String> resourceVersion, final Span span,
                             final Handler<AsyncResult<OperationResult<Id>>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resultHandler);

        if (!getConfig().isModificationEnabled()) {
            TracingHelper.logError(span, "Modification is disabled for Device Registration Service");
            resultHandler.handle(Future
                    .succeededFuture(Result.from(HttpURLConnection.HTTP_FORBIDDEN, OperationResult::empty)));
            return;
        }

        final Versioned<Device> updatedDevice = new Versioned<>(device);
        final DeviceDto UpdatedDeviceDto = new DeviceDto(tenantId, deviceId, updatedDevice.getValue(),
                updatedDevice.getVersion(), Instant.now());

        final JsonObject updateDeviceQuery = new MongoDbDocumentBuilder()
                .withTenantId(tenantId)
                .withDeviceId(deviceId)
                .create();
        final Promise<JsonObject> deviceUpdated = Promise.promise();
        mongoClient.findOneAndReplace(DEVICES_COLLECTION, updateDeviceQuery, JsonObject.mapFrom(UpdatedDeviceDto), deviceUpdated);
        deviceUpdated.future().compose(successDeviceUpdated -> {

            log.info("successDeviceUpdated: " + successDeviceUpdated);
            return Future.succeededFuture(OperationResult.ok(
                    HttpURLConnection.HTTP_CREATED,
                    Id.of(deviceId),
                    Optional.empty(),
                    Optional.of(updatedDevice.getVersion())));

        })
                .recover(errorDeleteDevice -> {
                    final String errorMsg = String.format("device with id [%s] on tenant [%s] could no be updated.", deviceId, tenantId);
                    log.error(errorMsg);
                    TracingHelper.logError(span, errorMsg);
                    return Future.failedFuture(errorMsg);
                })
                .setHandler(resultHandler);
    }

    @Override
    public void deleteDevice(final String tenantId, final String deviceId,
                             final Optional<String> resourceVersion, final Span span,
                             final Handler<AsyncResult<Result<Void>>> resultHandler) {

//        // TODO: check api version
//        // -> TracingHelper.logError(span, "Resource Version mismatch.");
//        //                    return Result.from(HttpURLConnection.HTTP_PRECON_FAILED);
//
        if (!config.isModificationEnabled()) {
            final String errorMsg = "Modification is disabled for Device Registration Service";
            TracingHelper.logError(span, errorMsg);
            log.info(errorMsg);
            resultHandler.handle(Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_FORBIDDEN)));
            return;
        }

        final JsonObject removeDeviceQuery = new MongoDbDocumentBuilder()
                .withTenantId(tenantId)
                .withDeviceId(deviceId)
                .create();
        final Promise<MongoClientDeleteResult> deleteDevice = Promise.promise();
        mongoClient.removeDocument(DEVICES_COLLECTION, removeDeviceQuery, deleteDevice);
        deleteDevice.future().compose(successDeleteDevice -> {
            if (successDeleteDevice.getRemovedCount() == 1) {
                log.info(String.format("Device deleted. Device id [%s] on tenant id [%s]", deviceId, tenantId));
                return Future.succeededFuture(Result.<Void>from(HttpURLConnection.HTTP_NO_CONTENT));
            } else {
                return Future.succeededFuture(Result.<Void>from(HttpURLConnection.HTTP_NOT_FOUND));
            }
        })
                .recover(errorDeleteDevice -> {
                    final String errorMsg = String.format("device with id [%s] on tenant [%s] could no be deleted.", deviceId, tenantId);
                    log.error(errorMsg);
                    TracingHelper.logError(span, errorMsg);
                    return Future.failedFuture(errorMsg);
                })
                .setHandler(resultHandler);

    }

    @Override
    public void assertRegistration(final String tenantId, final String deviceId,
                                   final Handler<AsyncResult<RegistrationResult>> resultHandler) {

    }

    @Override
    public void assertRegistration(final String tenantId, final String deviceId, final String gatewayId,
                                   final Handler<AsyncResult<RegistrationResult>> resultHandler) {

    }
}
