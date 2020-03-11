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
package org.eclipse.hono.deviceregistry.mongodb.service;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.DeviceDto;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbCallExecutor;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDocumentBuilder;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.deviceregistry.util.Versioned;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.registration.AbstractRegistrationService;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;

import io.opentracing.Span;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.MongoClientDeleteResult;
import io.vertx.ext.mongo.MongoClientUpdateResult;

/**
 * TODO.
 */
@Component
@Qualifier("serviceImpl")
@ConditionalOnProperty(name = "hono.app.type", havingValue = "mongodb", matchIfMissing = true)
public class MongoDbBasedRegistrationService extends AbstractVerticle
        implements DeviceManagementService, RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(MongoDbBasedRegistrationService.class);
    private MongoClient mongoClient;
    private MongoDbBasedRegistrationConfigProperties config;
    private MongoDbCallExecutor mongoDbCallExecutor;

    /**
     * Registration service, based on {@link AbstractRegistrationService}.
     * <p>
     * This helps work around Java's inability to inherit from multiple base classes. We create a new Registration
     * service, overriding the implementation of {@link AbstractRegistrationService} with the implementation of our
     * {@link MongoDbBasedRegistrationService#getDevice(String, String, Span)}.
     */
    private final AbstractRegistrationService registrationService = new AbstractRegistrationService() {

        @Override
        public Future<RegistrationResult> getDevice(final String tenantId, final String deviceId, final Span span) {
            return MongoDbBasedRegistrationService.this.getDevice(tenantId, deviceId, span);
        }

        @Override
        public Future<JsonArray> resolveGroupMembers(final String tenantId, final JsonArray viaGroups, final Span span) {
            return MongoDbBasedRegistrationService.this.resolveGroupMembers(tenantId, viaGroups);
        }
    };

    /**
     * Autowires the mongodb client.
     *
     * @param mongoDbCallExecutor the executor singleton
     */
    @Autowired
    public void setExecutor(final MongoDbCallExecutor mongoDbCallExecutor) {
        this.mongoDbCallExecutor = mongoDbCallExecutor;
        this.mongoClient = this.mongoDbCallExecutor.getMongoClient();
    }

    @Autowired
    public void setConfig(final MongoDbBasedRegistrationConfigProperties config) {
        this.config = config;
    }

    public MongoDbBasedRegistrationConfigProperties getConfig() {
        return config;
    }

    @Override
    public void start(final Promise<Void> startPromise) {

        mongoDbCallExecutor.createCollectionIndex(getConfig().getCollectionName(),
                new JsonObject().put(RegistrationConstants.FIELD_PAYLOAD_TENANT_ID, 1)
                        .put(RegistrationConstants.FIELD_PAYLOAD_DEVICE_ID, 1),
                new IndexOptions().unique(true))
                .map(success -> {
                    startPromise.complete();
                    return null;
                })
                .onFailure(error -> {
                    log.error("Index creation failed", error);
                    startPromise.fail(error);
                });
    }

    @Override
    public void stop(final Promise<Void> stopPromise) {
        mongoClient.close();
        stopPromise.complete();
    }

    @Override
    public Future<OperationResult<Id>> createDevice(final String tenantId, final Optional<String> deviceId,
            final Device device, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        final String deviceIdValue = deviceId.orElse(UUID.randomUUID().toString());
        final Versioned<Device> versionedDevice = new Versioned<>(device);
        final DeviceDto deviceDto = new DeviceDto(tenantId, deviceIdValue, versionedDevice.getValue(),
                versionedDevice.getVersion(), Instant.now());

        final Promise<Long> findExistingNoOfDevicesPromise = Promise.promise();
        mongoClient.count(getConfig().getCollectionName(), new JsonObject(), findExistingNoOfDevicesPromise);
        return findExistingNoOfDevicesPromise.future()
                .compose(existingNoOfDevices -> {
                    if (existingNoOfDevices >= getConfig().getMaxDevicesPerTenant()) {
                        log.debug("Maximum number of devices limit already reached for the tenant [{}]", tenantId);
                        TracingHelper.logError(span, String.format(
                                "Maximum number of devices limit already reached for the tenant [%s]", tenantId));
                        return Future
                                .succeededFuture(Result.from(HttpURLConnection.HTTP_FORBIDDEN, OperationResult::empty));
                    } else {
                        return processCreateDevice(deviceDto, span);
                    }
                });
    }

    @Override
    public Future<OperationResult<Device>> readDevice(final String tenantId, final String deviceId, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        return processReadDevice(tenantId, deviceId, span);
    }

    @Override
    public Future<OperationResult<Id>> updateDevice(final String tenantId, final String deviceId, final Device device,
            final Optional<String> resourceVersion, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        if (!getConfig().isModificationEnabled()) {
            TracingHelper.logError(span, "Modification is disabled for Device Registration Service");
            return Future.succeededFuture(Result.from(HttpURLConnection.HTTP_FORBIDDEN, OperationResult::empty));
        }
        // TODO: To check for the version mismatch.

        final Versioned<Device> updatedDevice = new Versioned<>(device);
        final DeviceDto UpdatedDeviceDto = new DeviceDto(tenantId, deviceId, updatedDevice.getValue(),
                updatedDevice.getVersion(), Instant.now());

        return ProcessUpdateDevice(tenantId, deviceId, UpdatedDeviceDto, span);
    }

    @Override
    public Future<Result<Void>> deleteDevice(final String tenantId, final String deviceId,
            final Optional<String> resourceVersion, final Span span) {

        if (!config.isModificationEnabled()) {
            final String errorMsg = "Modification is disabled for Device Registration Service";
            TracingHelper.logError(span, errorMsg);
            log.warn(errorMsg);
            return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_FORBIDDEN));
        }

        // TODO: To check for the version mismatch.

        return processDeleteDevice(tenantId, deviceId, span);
    }

    @Override
    public Future<RegistrationResult> assertRegistration(final String tenantId, final String deviceId) {
        return registrationService.assertRegistration(tenantId, deviceId);
    }

    @Override
    public Future<RegistrationResult> assertRegistration(final String tenantId, final String deviceId,
            final String gatewayId) {
        return registrationService.assertRegistration(tenantId, deviceId, gatewayId);
    }

    private JsonObject convertDevice(final String deviceId, final Device payload) {

        if (payload == null) {
            return null;
        }

        final JsonObject data = JsonObject.mapFrom(payload);

        return new JsonObject()
                .put(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID, deviceId)
                .put("data", data);
    }

    private Future<DeviceDto> findDevice(final String tenantId, final String deviceId, final Span span) {
        final JsonObject findDeviceQuery = new MongoDbDocumentBuilder()
                .withTenantId(tenantId)
                .withDeviceId(deviceId)
                .create();
        final Promise<JsonObject> readDevicePromise = Promise.promise();
        mongoClient.findOne(getConfig().getCollectionName(), findDeviceQuery, null, readDevicePromise);
        return readDevicePromise.future()
                .compose(result -> Optional.ofNullable(result)
                        .map(ok -> result.mapTo(DeviceDto.class))
                        .map(Future::succeededFuture)
                        .orElseGet(() -> {
                            log.debug("Device [{}] not found.", deviceId);
                            return Future.succeededFuture(null);
                        }));
    }

    private Future<RegistrationResult> getDevice(final String tenantId, final String deviceId, final Span span) {

        return processReadDevice(tenantId, deviceId, span)
                .compose(result -> Future.succeededFuture(RegistrationResult.from(result.getStatus(),
                        convertDevice(deviceId, result.getPayload()), result.getCacheDirective().orElse(null))));

    }

    private boolean ifDuplicateKeyError(final Throwable throwable) {
        if (throwable instanceof MongoException) {
            final MongoException mongoException = (MongoException) throwable;
            return ErrorCategory.fromErrorCode(mongoException.getCode()) == ErrorCategory.DUPLICATE_KEY;
        }
        return false;
    }

    private Future<OperationResult<Id>> processCreateDevice(final DeviceDto device, final Span span) {
        final Promise<String> addDevicePromise = Promise.promise();
        mongoClient.insert(getConfig().getCollectionName(), JsonObject.mapFrom(device), addDevicePromise);
        return addDevicePromise.future()
                .map(success -> OperationResult.ok(
                        HttpURLConnection.HTTP_CREATED,
                        Id.of(device.getDeviceId()),
                        Optional.empty(),
                        Optional.of(device.getVersion())))
                .recover(error -> {
                    if (ifDuplicateKeyError(error)) {
                        log.debug("Device [{}] already exists for the tenant [{}]", device.getDeviceId(),
                                device.getTenantId(), error);
                        TracingHelper.logError(span, String.format("Device [%s] already exists for the tenant [%s]",
                                device.getDeviceId(), device.getTenantId()));
                        return Future.succeededFuture(
                                OperationResult.empty(HttpURLConnection.HTTP_CONFLICT));
                    } else {
                        log.error("Error adding device [{}] for the tenant [{}]", device.getDeviceId(),
                                device.getTenantId(), error);
                        TracingHelper.logError(span, String.format("Error adding device [%s] for the tenant [%s]",
                                device.getDeviceId(), device.getTenantId()), error);
                        return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_INTERNAL_ERROR));
                    }
                });
    }

    private Future<Result<Void>> processDeleteDevice(final String tenantId, final String deviceId, final Span span) {
        final Promise<MongoClientDeleteResult> deleteDevicePromise = Promise.promise();
        final JsonObject removeDeviceQuery = new MongoDbDocumentBuilder()
                .withTenantId(tenantId)
                .withDeviceId(deviceId)
                .create();
        mongoClient.removeDocument(getConfig().getCollectionName(), removeDeviceQuery, deleteDevicePromise);
        return deleteDevicePromise.future()
                .compose(successDeleteDevice -> {
                    if (successDeleteDevice.getRemovedCount() == 1) {
                        return Future.succeededFuture(Result.<Void> from(HttpURLConnection.HTTP_NO_CONTENT));
                    } else {
                        return Future.succeededFuture(Result.<Void> from(HttpURLConnection.HTTP_NOT_FOUND));
                    }
                })
                .recover(errorDeleteDevice -> {
                    final String errorMsg = String.format("device with id [%s] on tenant [%s] could no be deleted.",
                            deviceId, tenantId);
                    log.error(errorMsg);
                    TracingHelper.logError(span, errorMsg);
                    return Future.failedFuture(errorMsg);
                });
    }

    private Future<OperationResult<Device>> processReadDevice(final String tenantId, final String deviceId,
            final Span span) {
        return findDevice(tenantId, deviceId, span)
                .compose(deviceDto -> Optional.ofNullable(deviceDto)
                        .map(ok -> Future.succeededFuture(
                                OperationResult.ok(
                                        HttpURLConnection.HTTP_OK,
                                        deviceDto.getDevice(),
                                        Optional.ofNullable(
                                                DeviceRegistryUtils.getCacheDirective(getConfig().getCacheMaxAge())),
                                        Optional.ofNullable(deviceDto.getVersion()))))
                        .orElseGet(() -> {
                            TracingHelper.logError(span, String.format("Device [%s] not found.", deviceId));
                            return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_NOT_FOUND));
                        }));
    }

    private Future<OperationResult<Id>> ProcessUpdateDevice(final String tenantId, final String deviceId,
            final DeviceDto device,
            final Span span) {
        final JsonObject updateDeviceQuery = new MongoDbDocumentBuilder()
                .withTenantId(tenantId)
                .withDeviceId(deviceId)
                .create();
        final Promise<MongoClientUpdateResult> updateDevicePromise = Promise.promise();
        mongoClient.updateCollection(getConfig().getCollectionName(), updateDeviceQuery,
                JsonObject.mapFrom(device), updateDevicePromise);
        return updateDevicePromise.future()
                .map(updateResult -> {
                    if (updateResult.getDocMatched() == 0) {
                        return OperationResult.empty(HttpURLConnection.HTTP_NOT_FOUND);
                    } else {
                        return OperationResult.ok(
                                HttpURLConnection.HTTP_CREATED,
                                Id.of(deviceId),
                                Optional.empty(),
                                Optional.of(device.getVersion()));
                    }
                });
    }

    private Future<JsonArray> resolveGroupMembers(final String tenantId, final JsonArray viaGroups) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(viaGroups);

        // TODO.
        return null;
    }
}
