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
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.util.CredentialsResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Optional;

/**
 * TODO.
 */
@Component
@Qualifier("serviceImpl")
@ConditionalOnProperty(name = "hono.app.type", havingValue = "mongodb", matchIfMissing = true)
public class MongoDbBasedCredentialsService extends AbstractVerticle
        implements CredentialsManagementService, CredentialsService {

    @Override
    public void get(final String tenantId, final String type, final String authId, final Span span,
                    final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

    }

    @Override
    public void get(final String tenantId, final String type, final String authId,
                    final JsonObject clientContext, final Span span,
                    final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

    }

    @Override
    public void updateCredentials(final String tenantId, final String deviceId,
                                  final List<CommonCredential> credentials,
                                  final Optional<String> resourceVersion, final Span span,
                                  final Handler<AsyncResult<OperationResult<Void>>> resultHandler) {

        resultHandler.handle(Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_OK)));
    }

    @Override
    public void readCredentials(final String tenantId, final String deviceId, final Span span,
                                final Handler<AsyncResult<OperationResult<List<CommonCredential>>>> resultHandler) {

    }
}
