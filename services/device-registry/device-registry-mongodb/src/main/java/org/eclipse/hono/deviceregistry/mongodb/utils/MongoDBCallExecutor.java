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

import com.mongodb.MongoSocketException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;
import org.eclipse.hono.deviceregistry.mongodb.MongoDbConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for vertx mongodb client access.
 */
public final class MongoDBCallExecutor {

    private static final Logger log = LoggerFactory.getLogger(MongoDBCallExecutor.class);

    private Vertx vertx;
    private MongoClient mongoClient;
    private MongoDbConfigProperties mongoDbConfig;

    /**
     * construct executor to init mongodb properties.
     *
     * @param vertx         acting vertx context
     * @param mongoDbConfig mongodb config properties
     */
    public MongoDBCallExecutor(final Vertx vertx, final MongoDbConfigProperties mongoDbConfig) {
        this.vertx = vertx;
        this.mongoDbConfig = mongoDbConfig;
        final JsonObject mongoConfigJson = this.mongoDbConfig.asMongoClientConfigJson();
        this.mongoClient = MongoClient.createShared(vertx, mongoConfigJson);
    }

    /**
     * MongoClient getter.
     *
     * @return mongodb client
     */
    public MongoClient getMongoClient() {
        return mongoClient;
    }

    /**
     * Creates mongodb collection index. Wrapper of {@link #createIndex(String, JsonObject, IndexOptions, Handler)}
     *
     * @param collectionName name of collection
     * @param keys           key to be indexed
     * @param options        optional options
     * @param startFuture    callback
     */
    public void createCollectionIndex(final String collectionName, final JsonObject keys, final IndexOptions options,
                                      final Future<Void> startFuture) {
        createIndex(collectionName, keys, options, res -> {
            if (res.succeeded()) {
                startFuture.complete();
            } else if (res.cause() instanceof MongoSocketException) {
                log.info("Create indices failed, wait for retry, cause:", res.cause());
                vertx.setTimer(this.mongoDbConfig.getCreateIndicesTimeout(), timer -> {
                    createIndex(collectionName, keys, options, res2 -> {
                        if (res2.succeeded()) {
                            startFuture.complete();
                        } else if (res2.cause() instanceof MongoSocketException) {
                            log.info("Create indices failed, wait for second retry, cause:", res2.cause());
                            vertx.setTimer(this.mongoDbConfig.getCreateIndicesTimeout(), timer2 -> {
                                createIndex(collectionName, keys, options, res3 -> {
                                    if (res3.succeeded()) {
                                        startFuture.complete();
                                    } else {
                                        log.error("Error creating index", res3.cause());
                                        startFuture.fail(res3.cause());
                                    }
                                });
                            });
                        } else {
                            log.error("Error creating index", res2.cause());
                            startFuture.fail(res2.cause());
                        }
                    });
                });
            } else {
                log.error("Error creating index", res.cause());
                startFuture.fail(res.cause());
            }
        });
    }

    /**
     * Creates mongodb index.
     *
     * @param collectionName       name of collection
     * @param keys                 key to be indexed
     * @param options              optional options
     * @param indexCreationTracker callback
     */
    private void createIndex(final String collectionName, final JsonObject keys, final IndexOptions options,
                             final Handler<AsyncResult<Void>> indexCreationTracker) {
        log.info("Create indices");
        mongoClient.createIndexWithOptions(collectionName, keys, options, indexCreationTracker);
    }
}
