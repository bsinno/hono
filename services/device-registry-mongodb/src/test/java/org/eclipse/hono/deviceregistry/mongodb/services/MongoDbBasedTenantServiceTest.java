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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.TenantDto;
import org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedTenantService;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbCallExecutor;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.service.management.tenant.TrustedCertificateAuthority;
import org.eclipse.hono.service.tenant.AbstractTenantServiceTest;
import org.eclipse.hono.service.tenant.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.stubbing.Answer;

import com.mongodb.MongoException;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.MongoClientDeleteResult;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@SuppressWarnings("unchecked")
@ExtendWith(VertxExtension.class)
@Timeout(timeUnit = TimeUnit.SECONDS, value = 3)
class MongoDbBasedTenantServiceTest extends AbstractTenantServiceTest {

    private static final List<Integer> DUPLICATE_KEY_ERROR_CODES = Arrays.asList(11000, 11001, 12582);
    private final String DUMMY_TENANT_ID = "dummyTenantId";
    private MongoDbBasedTenantsConfigProperties config;
    private TenantDto DUMMY_TENANT_DTO;
    private MongoDbCallExecutor mongoDbCallExecutor;
    private MongoClient mongoClient;
    private MongoDbBasedTenantService svc;

    @BeforeEach
    void setUp() {
        final Context ctx = mock(Context.class);
        final Vertx vertx = mock(Vertx.class);
        config = new MongoDbBasedTenantsConfigProperties();
        DUMMY_TENANT_DTO = new TenantDto(DUMMY_TENANT_ID, "dummyVersion", new Tenant(), Instant.now());
        svc = spy(new MongoDbBasedTenantService());
        svc.setConfig(config);
        svc.init(vertx, ctx);

        mongoDbCallExecutor = mock(MongoDbCallExecutor.class);
        mongoClient = mock(MongoClient.class);
        when(mongoDbCallExecutor.getMongoClient()).thenReturn(mongoClient);
        svc.setExecutor(mongoDbCallExecutor);

        when(mongoClient.findOne(anyString(), any(JsonObject.class), any(JsonObject.class), any(Handler.class)))
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<JsonObject>>) invocation.getArguments()[3];
                    result.handle(Future.succeededFuture(JsonObject.mapFrom(DUMMY_TENANT_DTO)));
                    return null;
                });
        when(mongoClient.insert(anyString(), any(JsonObject.class), any(Handler.class)))
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<String>>) invocation.getArguments()[2];
                    result.handle(Future.succeededFuture(DUMMY_TENANT_ID));
                    return null;
                });
        when(mongoClient.removeDocument(anyString(), any(JsonObject.class), any(Handler.class)))
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<MongoClientDeleteResult>>) invocation.getArguments()[2];
                    result.handle(Future.succeededFuture(new MongoClientDeleteResult(1)));
                    return null;
                });
        when(mongoClient.findOneAndReplace(anyString(), any(JsonObject.class), any(JsonObject.class), any(Handler.class)))
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<JsonObject>>) invocation.getArguments()[3];
                    result.handle(Future.succeededFuture(JsonObject.mapFrom(DUMMY_TENANT_DTO)));
                    return null;
                });


    }

    @Override
    public TenantService getTenantService() {
        return svc;
    }

    @Override
    public TenantManagementService getTenantManagementService() {
        return svc;
    }

    @Test
    public void testDoStartHasTenantCollection(final VertxTestContext ctx) {

        // GIVEN a tenant service with existing tenant db collection
        when(mongoClient.getCollections(any(Handler.class))).thenAnswer(
                invocation -> {
                    final var result = (Handler<AsyncResult<List<String>>>) invocation.getArguments()[0];
                    result.handle(Future.succeededFuture(new ArrayList<>(Arrays.asList("dummyCollection1", config.getCollectionName()))));
                    return null;
                });
        // THEN no new collection should be created
        final Promise<Void> startupTracker = Promise.promise();
        startupTracker.future().setHandler(ctx.succeeding(started -> ctx.verify(() -> {
            verify(mongoDbCallExecutor, never()).createCollectionIndex(anyString(), any(JsonObject.class), any());
            ctx.completeNow();
        })));

        // WHEN starting the service
        svc.start(startupTracker);
    }

    @Test
    public void testDoStartNoExistingTenantCollection(final VertxTestContext ctx) {

        // GIVEN a tenant service without existing tenant collection
        when(mongoClient.getCollections(any(Handler.class))).thenAnswer(
                invocation -> {
                    final var result = (Handler<AsyncResult<List<String>>>) invocation.getArguments()[0];
                    result.handle(Future.succeededFuture(new ArrayList<>(Collections.singletonList("dummyCollection1"))));
                    return null;
                });
        // GIVEN successful mongodb index creation
        when(mongoDbCallExecutor.createCollectionIndex(anyString(), any(JsonObject.class), any()))
                .thenReturn(Future.succeededFuture());

        // THEN a new indexed collection should be created
        final Promise<Void> startupTracker = Promise.promise();
        startupTracker.future().setHandler(ctx.succeeding(started -> ctx.verify(() -> {
            verify(mongoDbCallExecutor, atMostOnce()).createCollectionIndex(anyString(), any(JsonObject.class), any());
            ctx.completeNow();
        })));

        // WHEN starting the service
        svc.start(startupTracker);
    }

    @Test
    public void testAddTenantSucceedsWithGivenTenantId(final VertxTestContext ctx) {

        // WHEN tenant creation method called
        svc.createTenant(Optional.of(DUMMY_TENANT_ID), new Tenant(), null)
                // THEN a new tenant should be inserted and response should be HTTP_CREATED
                .setHandler(ctx.succeeding(successCreatedTenant -> ctx.verify(() -> {
                    assertThat(successCreatedTenant.getStatus()).isEqualTo(HttpURLConnection.HTTP_CREATED);
                    ctx.completeNow();
                })));


    }

    @Test
    public void testGetTenantFailsForNonExistingTenant(final VertxTestContext ctx) {

        // GIVEN tenant collection without a tenant with the dummy id
        doAnswer((Answer<Void>) invocation -> {
            final var result = (Handler<Future<JsonObject>>) invocation.getArguments()[3];
            result.handle(Future.succeededFuture());
            return null;
        }).when(mongoClient)
                .findOne(anyString(), any(JsonObject.class), any(JsonObject.class), any(Handler.class));

        // WHEN tenant read method called
        svc.readTenant(DUMMY_TENANT_ID, null)
                // THEN no tenant should be found and HTTP_NOT_FOUND 404 should be returned
                .setHandler(ctx.succeeding(successDidReadTenant -> ctx.verify(() -> {
                    assertThat(successDidReadTenant.getStatus()).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
                    ctx.completeNow();
                })));
    }

    @Test
    public void testGetTenantSucceedsForExistingTenant(final VertxTestContext ctx) {

        // GIVEN a tenant db with an existing tenant
        when(mongoClient.findOne(anyString(), any(JsonObject.class), any(JsonObject.class), any(Handler.class)))
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<JsonObject>>) invocation.getArguments()[3];
                    result.handle(Future.succeededFuture(JsonObject.mapFrom(DUMMY_TENANT_DTO)));
                    return null;
                });
        // WHEN existing tenant is requested
        svc.readTenant(DUMMY_TENANT_ID, null)
                // THEN a tenant should be found and returned
                .setHandler(ctx.succeeding(successDidReadTenant -> ctx.verify(() -> {
                    assertThat(successDidReadTenant.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                    assertThat(successDidReadTenant.getPayload()).isInstanceOf(Tenant.class);
                    ctx.completeNow();
                })));

    }

    @Test
    @Override
    public void testAddTenantFailsForDuplicateCa(final VertxTestContext ctx) {

        // GIVEN a successful create tenant mongodb response and one with a duplicate key error
        when(mongoClient.findOne(anyString(), any(JsonObject.class), any(JsonObject.class), any(Handler.class)))
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<JsonObject>>) invocation.getArguments()[3];

                    result.handle(Future.succeededFuture(new JsonObject()));
                    return null;
                })
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<JsonObject>>) invocation.getArguments()[3];

                    final Tenant tenant = new Tenant()
                            .setTrustedCertificateAuthorities(List.of(new TrustedCertificateAuthority()
                                    .setSubjectDn("CN=taken")
                                    .setPublicKey("NOTAKEY".getBytes(StandardCharsets.UTF_8))
                                    .setNotBefore(Instant.now().minus(1, ChronoUnit.DAYS))
                                    .setNotAfter(Instant.now().plus(2, ChronoUnit.DAYS))));

                    result.handle(Future.succeededFuture(JsonObject.mapFrom(
                            new TenantDto("tenant", "dummyVersion", tenant, Instant.now())
                    )));
                    return null;
                });

        super.testAddTenantFailsForDuplicateCa(ctx);

    }

    @Override
    @Test
    public void testAddTenantFailsForDuplicateTenantId(final VertxTestContext ctx) {

        when(mongoClient.insert(anyString(), any(JsonObject.class), any(Handler.class)))
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<String>>) invocation.getArguments()[2];
                    result.handle(Future.succeededFuture(DUMMY_TENANT_ID));
                    return null;
                })
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<String>>) invocation.getArguments()[2];
                    result.handle(Future.failedFuture(new MongoException(DUPLICATE_KEY_ERROR_CODES.get(0), "Duplicate tenantId dummyException")));
                    return null;
                });

        super.testAddTenantFailsForDuplicateTenantId(ctx);
    }

    @Override
    @Test
    public void testAddTenantSucceedsWithGeneratedTenantId(final VertxTestContext ctx) {

        // GIVEN first generated id found, second one free
        when(mongoClient.findOne(anyString(), any(JsonObject.class), any(JsonObject.class), any(Handler.class)))
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<JsonObject>>) invocation.getArguments()[3];
                    result.handle(Future.succeededFuture(JsonObject.mapFrom(new TenantDto(DUMMY_TENANT_ID, "dummyVersion", new Tenant(), Instant.now()))));
                    return null;
                })
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<JsonObject>>) invocation.getArguments()[3];
                    result.handle(Future.succeededFuture(new JsonObject()));
                    return null;
                });
        super.testAddTenantSucceedsWithGeneratedTenantId(ctx);
    }

    @Override
    @Test
    public void testDeleteTenantWithMatchingResourceVersionSucceed(final VertxTestContext ctx) {

        doReturn(Future.succeededFuture(
                OperationResult.ok(HttpURLConnection.HTTP_CREATED, Optional.of(DUMMY_TENANT_DTO.getTenantId()), Optional.empty(), Optional.of(DUMMY_TENANT_DTO.getVersion()))
        )).when(svc).createTenant(any(Optional.class), any(Tenant.class), any(Span.class));

        super.testDeleteTenantWithMatchingResourceVersionSucceed(ctx);
    }

    @Override
    @Test
    public void testUpdateTenantSucceeds(final VertxTestContext ctx) {

        doReturn(Future.succeededFuture(
                OperationResult.ok(HttpURLConnection.HTTP_CREATED, Optional.of(DUMMY_TENANT_DTO.getTenantId()), Optional.empty(), Optional.of(DUMMY_TENANT_DTO.getVersion()))
        )).when(svc).createTenant(any(Optional.class), any(Tenant.class), any(Span.class));

        // GIVEN mongodb response to provide updateTenant a tenant version
        when(mongoClient.findOne(anyString(), any(JsonObject.class), any(JsonObject.class), any(Handler.class)))
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<JsonObject>>) invocation.getArguments()[3];
                    result.handle(Future.succeededFuture(JsonObject.mapFrom(DUMMY_TENANT_DTO)));
                    return null;
                })
                // AND GIVEN mongodb response to provide updateTenant a tenant with previous updated properties
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<JsonObject>>) invocation.getArguments()[3];
                    final TenantDto updatedTenantDto = new TenantDto();
                    final Tenant updatedTenant = DUMMY_TENANT_DTO.getTenant();
                    final var extensionMap = new HashMap<String, Object>();
                    extensionMap.put("custom-prop", "something");
                    updatedTenant.setExtensions(extensionMap);
                    updatedTenantDto.setTenant(updatedTenant);

                    result.handle(Future.succeededFuture(JsonObject.mapFrom(updatedTenantDto)));
                    return null;
                });

        super.testUpdateTenantSucceeds(ctx);
    }

    @Override
    @Test
    public void testRemoveTenantSucceeds(final VertxTestContext ctx) {

        when(mongoClient.findOne(anyString(), any(JsonObject.class), any(JsonObject.class), any(Handler.class)))
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<JsonObject>>) invocation.getArguments()[3];
                    result.handle(Future.succeededFuture(JsonObject.mapFrom(DUMMY_TENANT_DTO)));
                    return null;
                })
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<JsonObject>>) invocation.getArguments()[3];
                    result.handle(Future.succeededFuture(JsonObject.mapFrom(DUMMY_TENANT_DTO)));
                    return null;
                })
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<JsonObject>>) invocation.getArguments()[3];
                    result.handle(Future.succeededFuture());
                    return null;
                });

        super.testRemoveTenantSucceeds(ctx);
    }

    @Override
    @Test
    public void testUpdateTenantWithMatchingResourceVersionSucceeds(final VertxTestContext ctx) {

        // GIVEN a tenant creation with a fixed version returned
        doReturn(Future.succeededFuture(
                OperationResult.ok(HttpURLConnection.HTTP_CREATED, Optional.of(DUMMY_TENANT_DTO.getTenantId()), Optional.empty(), Optional.of(DUMMY_TENANT_DTO.getVersion()))
        )).when(svc).createTenant(any(Optional.class), any(Tenant.class), any(Span.class));

        super.testDeleteTenantWithMatchingResourceVersionSucceed(ctx);
    }

    @Override
    @Test
    public void testGetForCertificateAuthoritySucceeds(final VertxTestContext ctx) {
        // GIVEN a tenant with id "tenant" and TrustedCertificateAuthorities
        when(mongoClient.findOne(anyString(), any(JsonObject.class), any(JsonObject.class), any(Handler.class)))
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<JsonObject>>) invocation.getArguments()[3];
                    result.handle(Future.succeededFuture(new JsonObject()));
                    return null;
                })
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<JsonObject>>) invocation.getArguments()[3];
                    final X500Principal subjectDn = new X500Principal("O=Eclipse, OU=Hono, CN=ca");

                    final Tenant tenant = new Tenant()
                            .setTrustedCertificateAuthorities(List.of(new TrustedCertificateAuthority()
                                    .setSubjectDn(subjectDn)
                                    .setPublicKey("NOTAPUBLICKEY".getBytes(StandardCharsets.UTF_8))
                                    .setNotBefore(Instant.now().minus(1, ChronoUnit.DAYS))
                                    .setNotAfter(Instant.now().plus(2, ChronoUnit.DAYS))));

                    result.handle(Future.succeededFuture(JsonObject.mapFrom(
                            new TenantDto("tenant", "dummyVersion", tenant, Instant.now())
                    )));
                    return null;
                });

        super.testGetForCertificateAuthoritySucceeds(ctx);
    }

    @Override
    @Test
    public void testGetForCertificateAuthorityFailsForUnknownSubjectDn(final VertxTestContext ctx) {

        // GIVEN a tenant with id "tenant" and TrustedCertificateAuthorities
        when(mongoClient.findOne(anyString(), any(JsonObject.class), any(JsonObject.class), any(Handler.class)))
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<JsonObject>>) invocation.getArguments()[3];

                    result.handle(Future.succeededFuture(new JsonObject()));
                    return null;
                });

        super.testGetForCertificateAuthorityFailsForUnknownSubjectDn(ctx);
    }

    @Override
    @Test
    public void testUpdateTenantFailsForDuplicateCa(final VertxTestContext ctx) {

        when(mongoClient.findOne(anyString(), any(JsonObject.class), any(JsonObject.class), any(Handler.class)))
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<JsonObject>>) invocation.getArguments()[3];
                    result.handle(Future.succeededFuture(new JsonObject()));
                    return null;
                })
                .thenAnswer((Answer<Void>) invocation -> {
                    final var result = (Handler<AsyncResult<JsonObject>>) invocation.getArguments()[3];

                    final Tenant tenant = new Tenant()
                            .setTrustedCertificateAuthorities(List.of(new TrustedCertificateAuthority()
                                    .setSubjectDn("CN=taken")
                                    .setPublicKey("NOTAKEY".getBytes(StandardCharsets.UTF_8))
                                    .setNotBefore(Instant.now().minus(1, ChronoUnit.DAYS))
                                    .setNotAfter(Instant.now().plus(2, ChronoUnit.DAYS))));

                    result.handle(Future.succeededFuture(JsonObject.mapFrom(
                            new TenantDto("tenant", "dummyVersion", tenant, Instant.now())
                    )));
                    return null;
                });

        super.testUpdateTenantFailsForDuplicateCa(ctx);
    }


}
