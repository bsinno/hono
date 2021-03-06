/*****************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedCredentialsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedCredentialsDao;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedDeviceDao;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantKey;
import org.eclipse.hono.service.credentials.AbstractCredentialsServiceTest;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.credentials.Credentials;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.tenant.RegistrationLimits;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests for {@link MongoDbBasedCredentialsService}.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public class MongoDbBasedCredentialServiceTest implements AbstractCredentialsServiceTest {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasedCredentialServiceTest.class);

    private final MongoDbBasedCredentialsConfigProperties credentialsServiceConfig = new MongoDbBasedCredentialsConfigProperties();
    private final MongoDbBasedRegistrationConfigProperties registrationServiceConfig = new MongoDbBasedRegistrationConfigProperties();

    private MongoDbBasedCredentialsDao credentialsDao;
    private MongoDbBasedDeviceDao deviceDao;
    private MongoDbBasedCredentialsService credentialsService;
    private MongoDbBasedCredentialsManagementService credentialsManagementService;
    private MongoDbBasedDeviceManagementService deviceManagementService;
    private TenantInformationService tenantInformationService;
    private Vertx vertx;

    /**
     * Creates the services under test.
     *
     * @param ctx The test context to use for running asynchronous tests.
     */
    @BeforeAll
    public void createServices(final VertxTestContext ctx) {

        vertx = Vertx.vertx();

        credentialsDao = MongoDbTestUtils.getCredentialsDao(vertx, "hono-credentials-test");
        credentialsService = new MongoDbBasedCredentialsService(credentialsDao, credentialsServiceConfig);
        credentialsManagementService = new MongoDbBasedCredentialsManagementService(
                vertx,
                credentialsDao,
                credentialsServiceConfig,
                new SpringBasedHonoPasswordEncoder());

        deviceDao = MongoDbTestUtils.getDeviceDao(vertx, "hono-credentials-test");
        deviceManagementService = new MongoDbBasedDeviceManagementService(
                deviceDao,
                credentialsDao,
                registrationServiceConfig);

        CompositeFuture.all(deviceDao.createIndices(), credentialsDao.createIndices())
            .onComplete(ctx.completing());
    }

    /**
     * Starts up the service.
     *
     * @param testInfo Test case meta information.
     */
    @BeforeEach
    public void setup(final TestInfo testInfo) {
        LOG.info("running {}", testInfo.getDisplayName());

        tenantInformationService = mock(TenantInformationService.class);
        when(tenantInformationService.getTenant(anyString(), any())).thenReturn(Future.succeededFuture(new Tenant()));
        when(tenantInformationService.tenantExists(anyString(), any())).thenAnswer(invocation -> {
            return Future.succeededFuture(OperationResult.ok(
                    HttpURLConnection.HTTP_OK,
                    TenantKey.from(invocation.getArgument(0)),
                    Optional.empty(),
                    Optional.empty()));
        });

        credentialsService.setTenantInformationService(tenantInformationService);
        credentialsManagementService.setTenantInformationService(tenantInformationService);
        deviceManagementService.setTenantInformationService(tenantInformationService);
    }

    /**
     * Cleans up the collection after tests.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @AfterEach
    public void cleanCollection(final VertxTestContext testContext) {
        final Checkpoint clean = testContext.checkpoint(2);
        credentialsDao.deleteAllFromCollection()
            .onComplete(testContext.succeeding(ok -> clean.flag()));
        deviceDao.deleteAllFromCollection()
            .onComplete(testContext.succeeding(ok -> clean.flag()));
    }

    /**
     * Shut down services.
     *
     * @param testContext The test context to use for running asynchronous tests.
     */
    @AfterAll
    public void finishTest(final VertxTestContext testContext) {

        credentialsDao.close();
        deviceDao.close();
        vertx.close(s -> testContext.completeNow());
    }

    @Override
    public CredentialsService getCredentialsService() {
        return this.credentialsService;
    }

    @Override
    public CredentialsManagementService getCredentialsManagementService() {
        return this.credentialsManagementService;
    }

    @Override
    public DeviceManagementService getDeviceManagementService() {
        return this.deviceManagementService;
    }

    /**
     * Verifies that a request to update credentials of a device fails with a 403 status code
     * if the number of credentials exceeds the tenant's configured limit.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsFailsForExceededCredentialsPerDeviceLimit(final VertxTestContext ctx) {
        final var tenantId = UUID.randomUUID().toString();
        final var deviceId = UUID.randomUUID().toString();

        when(tenantInformationService.getTenant(anyString(), any()))
            .thenReturn(Future.succeededFuture(new Tenant().setRegistrationLimits(
                    new RegistrationLimits().setMaxCredentialsPerDevice(1))));

        credentialsManagementService.updateCredentials(
                tenantId,
                deviceId,
                List.of(
                        Credentials.createPasswordCredential("device1", "secret"),
                        Credentials.createPasswordCredential("device2", "secret")),
                Optional.empty(),
                NoopSpan.INSTANCE)
            .onComplete(ctx.succeeding(r -> {
                ctx.verify(() -> {
                    verify(tenantInformationService).getTenant(eq(tenantId), any(Span.class));
                    assertThat(r.getStatus()).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN);
                });
                ctx.completeNow();
            }));
    }
}
