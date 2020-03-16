/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.TenantDto;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbCallExecutor;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDocumentBuilder;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbErrorHandler;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbServiceUtils;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.deviceregistry.util.Versioned;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.service.tenant.TenantService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.opentracing.Span;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.MongoClientDeleteResult;

/**
 * A tenant service that uses a mongodb client to manage tenants.
 * <p>
 * On startup this adapter tries to find the tenant collection, if not found, it gets created.
 */
@SuppressWarnings("RedundantTypeArguments")
@Component
@Qualifier("serviceImpl")
@ConditionalOnProperty(name = "hono.app.type", havingValue = "mongodb")
public final class MongoDbBasedTenantService extends AbstractVerticle implements TenantService, TenantManagementService {

    private static final Logger log = LoggerFactory.getLogger(MongoDbBasedTenantService.class);

    private MongoDbCallExecutor mongoDbCallExecutor;
    private MongoClient mongoClient;
    private MongoDbBasedTenantsConfigProperties config;

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

    public MongoDbBasedTenantsConfigProperties getConfig() {
        return config;
    }

    /**
     * Autowires the tenant config.
     *
     * @param configuration The tenant configuration
     */
    @Autowired
    public void setConfig(final MongoDbBasedTenantsConfigProperties configuration) {
        this.config = configuration;
    }

    /**
     * Starts the service.
     * <p>
     * Finishes if a tenant collection is found and creates an indexed collection else.
     * </P>
     *
     * @param startPromise the startup promise
     */
    @Override
    public void start(final Promise<Void> startPromise) {
        final Promise<List<String>> existingCollections = Promise.promise();
        mongoClient.getCollections(existingCollections);
        existingCollections.future()
                .compose(successExistingCollections -> {
                    if (successExistingCollections.contains(getConfig().getCollectionName())) {
                        return Future.succeededFuture();
                    } else {
                        // create index & implicit collection
                        return mongoDbCallExecutor.createCollectionIndex(getConfig().getCollectionName(),
                                new JsonObject().put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, 1).put(TenantConstants.FIELD_PAYLOAD_DEVICE_ID, 1),
                                new IndexOptions().unique(true));
                    }
                })
                .compose(success -> {
                    if (getConfig().isModificationEnabled()) {
                        log.info("persistence is disabled, will not save tenant identities to mongoDB.");
                    }
                    log.debug("startup complete");
                    startPromise.complete();
                    return Future.succeededFuture();
                }).onFailure(reason -> {
            log.error("Index creation failed", reason);
            startPromise.fail(reason.toString());
        });
    }

    /**
     * Stops the service.
     *
     * @param stopPromise the shutdown promise
     */
    @Override
    public void stop(final Promise<Void> stopPromise) {
        this.mongoClient.close();
        stopPromise.complete();
    }

    /**
     * Updates data of a present tenant.
     * <p>
     * Tenant modification have to be enabled in {@link MongoDbBasedTenantsConfigProperties}.
     * </p>
     * <p>
     * Provided version {@code resourceVersion} have to be empty or equal to the version of present tenant.
     * A provided <em>certificate authority</em> must not be set in other tenants.
     * </p>
     *
     * @param tenantId        The identifier of the tenant.
     * @param tenantObj       The updated configuration information for the tenant (may be {@code null}).
     * @param resourceVersion The identifier of the resource version to update.
     * @param span            The active OpenTracing span for this operation. It is not to be closed in this method!
     *                        An implementation should log (error) events on this span and it may set tags and use this span as the
     *                        parent for any spans created in this method.
     * @return Result of update operation: On success {@code HttpURLConnection.HTTP_NO_CONTENT}, http error codes else.
     */
    @SuppressWarnings("unchecked")
    @Override
    public Future<OperationResult<Void>> updateTenant(final String tenantId, final Tenant tenantObj, final Optional<String> resourceVersion, final Span span) {
        if (!getConfig().isModificationEnabled()) {
            final String errorMsg = "Modification disabled for tenant service.";
            TracingHelper.logError(span, errorMsg);
            log.info(errorMsg);
            return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_FORBIDDEN));
        }

        final Promise<JsonObject> tenantRead = Promise.promise();
        final JsonObject findTenantQuery = new MongoDbDocumentBuilder()
                .withTenantId(tenantId)
                .create();
        mongoClient.findOne(getConfig().getCollectionName(), findTenantQuery, new JsonObject(), tenantRead);
        return tenantRead.future()
                .compose(successTenantRead -> {
                    if (successTenantRead == null || successTenantRead.size() == 0) {
                        TracingHelper.logError(span, "Tenant not found.");
                        return Future.<OperationResult<Void>>succeededFuture(OperationResult.<Void>empty(HttpURLConnection.HTTP_NOT_FOUND));
                    }
                    final TenantDto currentTenantDto = successTenantRead.mapTo(TenantDto.class);

                    if (resourceVersion != null && resourceVersion.isPresent() && MongoDbServiceUtils.isVersionDifferent(resourceVersion, currentTenantDto.getVersion())) {
                        TracingHelper.logError(span, "Resource Version mismatch.");
                        return Future.<OperationResult<Void>>succeededFuture(OperationResult.<Void>empty(HttpURLConnection.HTTP_PRECON_FAILED));
                    }

                    final Versioned<Tenant> newTenant = new Versioned<>(tenantObj);
                    final TenantDto newTenantDto = new TenantDto(tenantId, newTenant.getVersion(), newTenant.getValue(), Instant.now());

                    final Future<Map.Entry<String, Versioned<Tenant>>> conflictingTenant = newTenantDto.getTenant()
                            .getTrustedCertificateAuthoritySubjectDNs()
                            .stream()
                            .map(this::getByCa)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElseGet(() -> Future.succeededFuture(null));

                    return conflictingTenant.compose(conflictingTenantFound -> {
                        if (conflictingTenantFound != null && !tenantId.equals(conflictingTenantFound.getKey())) {
                            // we are trying to use the same CA as another tenant
                            TracingHelper.logError(span, "Conflict : CA already used by an existing tenant.");
                            return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_CONFLICT));
                        }

                        final JsonObject newTenantDtoJson = JsonObject.mapFrom(newTenantDto);

                        final Promise<JsonObject> tenantUpdated = Promise.promise();
                        mongoClient.findOneAndReplace(getConfig().getCollectionName(), findTenantQuery, newTenantDtoJson, tenantUpdated);
                        return tenantUpdated.future().compose(successTenantUpdated -> Future.succeededFuture(OperationResult.ok(
                                HttpURLConnection.HTTP_NO_CONTENT,
                                null,
                                Optional.empty(),
                                Optional.of(newTenantDto.getVersion())
                        )));
                    });

                })
                .recover(errorTenantUpdating -> MongoDbErrorHandler.operationFailed(log, span, String.format("tenant [%s] could no be updated.", tenantObj)));
    }

    /**
     * Deletes a present tenant.
     * <p>
     * Tenant modification have to be enabled in {@link MongoDbBasedTenantsConfigProperties}.
     * </p>
     * <p>
     * Provided version {@code resourceVersion} have to be empty or equal to the version of present tenant.
     * </p>
     *
     * @param tenantId        The identifier of the tenant.
     * @param resourceVersion The identifier of the resource version to delete.
     * @param span            The active OpenTracing span for this operation. It is not to be closed in this method!
     *                        An implementation should log (error) events on this span and it may set tags and use this span as the
     *                        parent for any spans created in this method.
     * @return Result of deletion operation: On success {@code HttpURLConnection.HTTP_NO_CONTENT}, http error codes else.
     */
    @SuppressWarnings("unchecked")
    @Override
    public Future<Result<Void>> deleteTenant(final String tenantId, final Optional<String> resourceVersion, final Span span) {

        if (!getConfig().isModificationEnabled()) {
            final String errorMsg = "Modification disabled for tenant service.";
            TracingHelper.logError(span, errorMsg);
            log.info(errorMsg);
            return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_FORBIDDEN));
        }

        final Promise<JsonObject> tenantFound = Promise.promise();
        final JsonObject findTenantQuery = new MongoDbDocumentBuilder()
                .withTenantId(tenantId)
                .create();
        mongoClient.findOne(getConfig().getCollectionName(), findTenantQuery, new JsonObject(), tenantFound);
        return tenantFound.future()
                .compose(successTenantRead -> {

                    if (successTenantRead == null) {
                        TracingHelper.logError(span, "Tenant not found.");
                        return Future.succeededFuture(Result.<Void>from(HttpURLConnection.HTTP_NOT_FOUND));
                    }
                    final String currentVersion = successTenantRead.getString("version");
                    if (currentVersion == null) {
                        TracingHelper.logError(span, "Tenant not found.");
                        return Future.succeededFuture(Result.<Void>from(HttpURLConnection.HTTP_NOT_FOUND));
                    }
                    if (MongoDbServiceUtils.isVersionDifferent(resourceVersion, currentVersion)) {
                        TracingHelper.logError(span, "Resource Version mismatch.");
                        return Future.succeededFuture(Result.<Void>from(HttpURLConnection.HTTP_PRECON_FAILED));
                    }

                    final Promise<MongoClientDeleteResult> deleteTenant = Promise.promise();
                    final JsonObject removeTenantQuery = new MongoDbDocumentBuilder()
                            .withTenantId(tenantId)
                            .create();
                    mongoClient.removeDocument(getConfig().getCollectionName(), removeTenantQuery, deleteTenant);
                    return deleteTenant.future().compose(successDeleteTenant -> {
                        if (successDeleteTenant.getRemovedCount() == 1) {
                            return Future.succeededFuture(Result.<Void>from(HttpURLConnection.HTTP_NO_CONTENT));
                        } else {
                            return Future.succeededFuture(Result.<Void>from(HttpURLConnection.HTTP_NOT_FOUND));
                        }
                    });
                })
                .recover(errorDeleteTenant -> MongoDbErrorHandler.operationFailed(log, span, String.format("tenant with id [%s] could no be deleted.", tenantId)));

    }

    /**
     * Fetches tenant by tenant id.
     *
     * @param tenantId The identifier of the tenant.
     * @return Future TenantResult with {@code HttpURLConnection.HTTP_OK} if tenant found, {@code HttpURLConnection.HTTP_NOT_FOUND} else.
     */
    @Override
    public Future<TenantResult<JsonObject>> get(final String tenantId) {
        Objects.requireNonNull(tenantId);
        return get(tenantId, null);
    }

    /**
     * Fetches tenant by tenant id.
     *
     * @param tenantId The identifier of the tenant.
     * @param span     The active OpenTracing span for this operation. It is not to be closed in this method!
     *                 An implementation should log (error) events on this span and it may set tags and use this span as the
     *                 parent for any spans created in this method.
     * @return Future TenantResult with {@code HttpURLConnection.HTTP_OK} if tenant found, {@code HttpURLConnection.HTTP_NOT_FOUND} else.
     */
    @Override
    public Future<TenantResult<JsonObject>> get(final String tenantId, final Span span) {
        return readTenant(tenantId, span)
                .compose(successTenantRead -> {
                    if (successTenantRead.getStatus() != HttpURLConnection.HTTP_OK) {
                        TracingHelper.logError(span, "tenant not found");
                        return Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_NOT_FOUND));
                    }
                    return Future.succeededFuture(TenantResult.from(
                            HttpURLConnection.HTTP_OK,
                            DeviceRegistryUtils.convertTenant(tenantId, successTenantRead.getPayload(), true),
                            DeviceRegistryUtils.getCacheDirective(config.getCacheMaxAge())
                    ));
                });
    }

    /**
     * Fetches tenant by tenant {@link X500Principal}.
     *
     * @param subjectDn The <em>subject DN</em> of the trusted CA certificate
     *                  that has been configured for the tenant.
     * @return Future TenantResult with {@code HttpURLConnection.HTTP_OK} if tenant found, {@code HttpURLConnection.HTTP_NOT_FOUND} else.
     */
    @Override
    public Future<TenantResult<JsonObject>> get(final X500Principal subjectDn) {
        Objects.requireNonNull(subjectDn);
        return get(subjectDn, null);
    }

    /**
     * Fetches tenant by tenant {@link X500Principal}.
     *
     * @param subjectDn The <em>subject DN</em> of the trusted CA certificate
     *                  that has been configured for the tenant.
     * @param span      The active OpenTracing span for this operation. It is not to be closed in this method!
     *                  An implementation should log (error) events on this span and it may set tags and use this span as the
     *                  parent for any spans created in this method.
     * @return Future TenantResult with {@code HttpURLConnection.HTTP_OK} if tenant found, {@code HttpURLConnection.HTTP_NOT_FOUND} else.
     */
    @Override
    public Future<TenantResult<JsonObject>> get(final X500Principal subjectDn, final Span span) {

        return getForCertificateAuthority(subjectDn, span);

    }

    /**
     * Finds an existing tenant by the subject DN of its configured certificate authority.
     *
     * @param subjectDn the subject DN to find the tenant with.
     * @param span      The active OpenTracing span for this operation. It is not to be closed in this method!
     *                  An implementation should log (error) events on this span and it may set tags and use this span as the
     *                  parent for any spans created in this method.
     * @return Future TenantResult with {@code HttpURLConnection.HTTP_OK} if tenant found, {@code HttpURLConnection.HTTP_NOT_FOUND} else.
     */
    private Future<TenantResult<JsonObject>> getForCertificateAuthority(final X500Principal subjectDn, final Span span) {

        if (subjectDn == null) {
            TracingHelper.logError(span, "missing subject DN");
            return Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            final Future<Map.Entry<String, Versioned<Tenant>>> tenantsFound = getByCa(subjectDn);
            return tenantsFound.compose(successTenantRead -> {
                if (successTenantRead == null) {
                    TracingHelper.logError(span, "no tenant found for subject DN");
                    return Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_NOT_FOUND));
                } else {
                    return Future.succeededFuture(TenantResult.from(
                            HttpURLConnection.HTTP_OK,
                            DeviceRegistryUtils.convertTenant(successTenantRead.getKey(), successTenantRead.getValue().getValue(), true),
                            DeviceRegistryUtils.getCacheDirective(config.getCacheMaxAge())));
                }
            });


        }
    }

    /**
     * Fetches tenant by subject DN.
     *
     * @param subjectDn the subject DN.
     * @return A Future Map consisting of pair of a tenant id and a versioned tenant.
     */
    private Future<Map.Entry<String, Versioned<Tenant>>> getByCa(final X500Principal subjectDn) {

        if (subjectDn == null) {
            return Future.failedFuture("missing subject DN");
        } else {
            final JsonObject findTenantQuery = new MongoDbDocumentBuilder()
                    .withCa("tenant", subjectDn.getName())
                    .create();

            final Promise<JsonObject> tenantsFound = Promise.promise();
            mongoClient.findOne(getConfig().getCollectionName(), findTenantQuery, new JsonObject(), tenantsFound);
            return tenantsFound.future().compose(successTenantsFound -> {
                if (successTenantsFound == null || successTenantsFound.size() == 0) {
                    return Future.succeededFuture(null);
                }
                final TenantDto tenantDtoFound = successTenantsFound.mapTo(TenantDto.class);
                return Future.succeededFuture(
                        new AbstractMap.SimpleEntry<>(tenantDtoFound.getTenantId(), new Versioned<>(tenantDtoFound.getVersion(), tenantDtoFound.getTenant()))
                );
            });
        }
    }

    /**
     * Retrieves a tenant by tenant id.
     *
     * @param tenantId The identifier of the tenant.
     * @param span     The active OpenTracing span for this operation. It is not to be closed in this method!
     *                 An implementation should log (error) events on this span and it may set tags and use this span as the
     *                 parent for any spans created in this method.
     * @return Result of find operation: On success {@code HttpURLConnection.HTTP_OK}, if tenant found, {@code HttpURLConnection.HTTP_NOT_FOUND} else.
     */
    @Override
    public Future<OperationResult<Tenant>> readTenant(final String tenantId, final Span span) {
        final JsonObject findTenantQuery = new MongoDbDocumentBuilder()
                .withTenantId(tenantId)
                .create();
        final Promise<JsonObject> didReadTenant = Promise.promise();
        mongoClient.findOne(getConfig().getCollectionName(), findTenantQuery, new JsonObject(), didReadTenant);
        //noinspection unchecked
        return didReadTenant.future().compose(successDidReadTenant -> Optional.ofNullable(successDidReadTenant)
                .map(tenantFound -> Future.succeededFuture(OperationResult.ok(
                        HttpURLConnection.HTTP_OK,
                        tenantFound.getJsonObject("tenant").mapTo(Tenant.class),
                        Optional.ofNullable(DeviceRegistryUtils.getCacheDirective(config.getCacheMaxAge())),
                        Optional.ofNullable(tenantFound.getString("version")))
                )).orElseGet(() -> Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_NOT_FOUND))))
                .recover(errorDidReadTenant -> MongoDbErrorHandler.operationFailed(log, span, "tenants could not be read."));
    }

    /**
     * Checks if a given tenant id exists in db.
     *
     * @param tenantId the tenant id to check for.
     * @return Future with {@code true} if the id already exist.
     */
    private Future<Boolean> tenantIdExist(final String tenantId) {
        final JsonObject findTenantQuery = new MongoDbDocumentBuilder()
                .withTenantId(tenantId)
                .create();
        final Promise<JsonObject> checkedIdExist = Promise.promise();
        mongoClient.findOne(getConfig().getCollectionName(), findTenantQuery, new JsonObject(), checkedIdExist);
        //noinspection unchecked
        return checkedIdExist.future().compose(successCheckedIdExist -> Future.succeededFuture(successCheckedIdExist != null && !successCheckedIdExist.isEmpty()))
                .recover(errorDidReadTenant -> MongoDbErrorHandler.operationFailed(log, null, "tenants could not be read."));
    }

    /**
     * Generate a random tenant ID that is conflict free from existing tenant ids.
     *
     * @return a conflict free tenant id.
     */
    private Future<String> createConflictFreeUUID() {
        final String id = UUID.randomUUID().toString();
        return tenantIdExist(id).compose(successIdExist -> {
            if (successIdExist) {
                return createConflictFreeUUID();
            } else {
                return Future.succeededFuture(id);
            }
        });
    }

    /**
     * Inserts a tenant into the device registry.
     * <p>
     * Tenant modification have to be enabled in {@link MongoDbBasedTenantsConfigProperties}.
     * </p>
     * <p>
     * Optional: A tenant id can be provided. If none is set, a UUID will be created.
     * A provided <em>certificate authority</em> must not be set in other tenants.
     * </p>
     *
     * @param tenantId  The identifier of the tenant to create.
     * @param tenantObj The configuration information to add for the tenant (may be {@code null}).
     * @param span      The active OpenTracing span for this operation. It is not to be closed in this method!
     *                  An implementation should log (error) events on this span and it may set tags and use this span as the
     *                  parent for any spans created in this method.
     * @return Result of insertion operation: On success {@code HttpURLConnection.HTTP_CREATED}, http error codes else.
     */
    @Override
    public Future<OperationResult<Id>> createTenant(final Optional<String> tenantId, final Tenant tenantObj, final Span span) {

        if (!getConfig().isModificationEnabled()) {
            final String errorMsg = "Modification disabled for tenant service.";
            TracingHelper.logError(span, errorMsg);
            log.info(errorMsg);
            return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_FORBIDDEN));
        }

        final Future<String> getTenantId;

        if (tenantId.isPresent()) {
            getTenantId = Future.succeededFuture(tenantId.get());
        } else {
            getTenantId = createConflictFreeUUID();
        }

        return getTenantId
                .compose(successTenantId -> {
                    final Versioned<Tenant> newTenant = new Versioned<>(tenantObj);
                    final TenantDto newTenantDto = new TenantDto(successTenantId, newTenant.getVersion(), newTenant.getValue(), Instant.now());
                    if (newTenantDto.getVersion() == null) {
                        TracingHelper.logError(span, "Tenant format invalid.");
                        return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_BAD_REQUEST));
                    }

                    final Future<Map.Entry<String, Versioned<Tenant>>> conflictingTenant = newTenantDto.getTenant()
                            .getTrustedCertificateAuthoritySubjectDNs()
                            .stream()
                            .map(this::getByCa)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElseGet(() -> Future.succeededFuture(null));

                    return conflictingTenant.compose(conflictingTenantFound -> {
                        if (conflictingTenantFound != null && !successTenantId.equals(conflictingTenantFound.getKey())) {
                            // CA is already present in another tenant
                            TracingHelper.logError(span, "Conflict : CA already used by an existing tenant.");
                            return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_CONFLICT));
                        }

                        final JsonObject newTenantDtoJson = JsonObject.mapFrom(newTenantDto);
                        final Promise<String> getTenantInsertion = Promise.promise();
                        mongoClient.insert(getConfig().getCollectionName(), newTenantDtoJson, getTenantInsertion);
                        return getTenantInsertion.future()
                                .compose(successTenantInsertion -> Future.succeededFuture(OperationResult.ok(HttpURLConnection.HTTP_CREATED, Id.of(successTenantId), Optional.empty(), Optional.of(newTenant.getVersion()))))
                                .recover(errorTenantInsertion -> {
                                    if (MongoDbErrorHandler.ifDuplicateKeyError(errorTenantInsertion)) {
                                        final var errorMsg = String.format("Tenant [%s] with id [%s] already exist.", newTenant.getValue(), successTenantId);
                                        log.error(errorMsg);
                                        TracingHelper.logError(span, errorMsg);
                                        return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_CONFLICT));
                                    } else {
                                        final var errorMsg = String.format("Tenant [%s] could no be created.", newTenant.getValue());
                                        log.error(errorMsg);
                                        TracingHelper.logError(span, errorMsg);
                                        return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_INTERNAL_ERROR));
                                    }
                                });
                    });
                })
                .recover(errorTenantCreation -> {
                    final var errorMsg = String.format("Tenant [%s] could no be created.", getTenantId.result());
                    log.error(errorMsg);
                    TracingHelper.logError(span, errorMsg);
                    return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_INTERNAL_ERROR));
                });
    }

}
