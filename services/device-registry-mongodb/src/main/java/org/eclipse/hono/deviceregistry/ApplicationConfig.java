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

package org.eclipse.hono.deviceregistry;

import java.util.Optional;

import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ServerConfig;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.VertxProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedRegistrationService;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbCallExecutor;
import org.eclipse.hono.deviceregistry.service.credentials.AutowiredCredentialsAmqpEndpoint;
import org.eclipse.hono.deviceregistry.service.credentials.AutowiredCredentialsManagementHttpEndpoint;
import org.eclipse.hono.deviceregistry.service.device.AutowiredDeviceManagementHttpEndpoint;
import org.eclipse.hono.deviceregistry.service.device.AutowiredRegistrationAmqpEndpoint;
import org.eclipse.hono.deviceregistry.service.deviceconnection.MapBasedDeviceConnectionsConfigProperties;
import org.eclipse.hono.deviceregistry.service.tenant.AutowiredTenantAmqpEndpoint;
import org.eclipse.hono.deviceregistry.service.tenant.AutowiredTenantManagementHttpEndpoint;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.VertxBasedHealthCheckServer;
import org.eclipse.hono.service.amqp.AmqpEndpoint;
import org.eclipse.hono.service.deviceconnection.DeviceConnectionAmqpEndpoint;
import org.eclipse.hono.service.http.HttpEndpoint;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.Constants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

/**
 * Spring Boot configuration for the mongodb based device registry application.
 */
@SuppressWarnings("unused")
@Configuration
public class ApplicationConfig {

    /**
     * Exposes a Vert.x instance as a Spring bean.
     * <p>
     * This method creates new Vert.x default options and invokes
     * {@link VertxProperties#configureVertx(VertxOptions)} on the object returned
     * by {@link #vertxProperties()}.
     *
     * @return The Vert.x instance.
     */
    @Bean
    public Vertx vertx() {
        return Vertx.vertx(vertxProperties().configureVertx(new VertxOptions()));
    }

    /**
     * Exposes configuration properties for Vert.x.
     *
     * @return The properties.
     */
    @ConfigurationProperties("hono.vertx")
    @Bean
    public VertxProperties vertxProperties() {
        return new VertxProperties();
    }

    /**
     * Gets the mongodb config properties.
     *
     * @return The mongodb config properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.mongodb")
    public MongoDbConfigProperties mongoDbConfigProperties() {
        return new MongoDbConfigProperties();
    }

    /**
     * Gets a {@link MongoDbCallExecutor} instance containing helper methods for mongodb interaction.
     *
     * @return An instance of the helper class {@link MongoDbCallExecutor}.
     */
    @Bean
    @ConditionalOnProperty(name = "hono.app.type", havingValue = "mongodb")
    public MongoDbCallExecutor mongoDBCallExecutor() {
        return new MongoDbCallExecutor(vertx(), mongoDbConfigProperties());
    }

    /**
     * Exposes an OpenTracing {@code Tracer} as a Spring Bean.
     * <p>
     * The Tracer will be resolved by means of a Java service lookup.
     * If no tracer can be resolved this way, the {@code NoopTracer} is
     * returned.
     *
     * @return The tracer.
     */
    @Bean
    public Tracer getTracer() {

        return Optional.ofNullable(TracerResolver.resolveTracer())
                .orElse(NoopTracerFactory.create());
    }

    /**
     * Gets general properties for configuring the Device Registry Spring Boot application.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.app")
    public ApplicationConfigProperties applicationConfigProperties() {
        return new ApplicationConfigProperties();
    }

    /**
     * Exposes properties for configuring the health check as a Spring bean.
     *
     * @return The health check configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.health-check")
    public ServerConfig healthCheckConfigProperties() {
        return new ServerConfig();
    }

    /**
     * Gets properties for configuring the Device Registry's AMQP 1.0 endpoint.
     *
     * @return The properties.
     */
    @Qualifier(Constants.QUALIFIER_AMQP)
    @Bean
    @ConfigurationProperties(prefix = "hono.registry.amqp")
    public ServiceConfigProperties amqpServerProperties() {
        return new ServiceConfigProperties();
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Device Registration</em> API.
     *
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public AmqpEndpoint registrationAmqpEndpoint() {
        return new AutowiredRegistrationAmqpEndpoint(vertx());
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Credentials</em> API.
     *
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public AmqpEndpoint credentialsAmqpEndpoint() {
        return new AutowiredCredentialsAmqpEndpoint(vertx());
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Tenant</em> API.
     *
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public AmqpEndpoint tenantAmqpEndpoint() {
        return new AutowiredTenantAmqpEndpoint(vertx());
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Device Connection</em> API.
     *
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public AmqpEndpoint deviceConnectionAmqpEndpoint() {
        return new DeviceConnectionAmqpEndpoint(vertx());
    }

    /**
     * Gets properties for configuring the HTTP based Device Registry Management endpoint.
     *
     * @return The properties.
     */
    @Qualifier(Constants.QUALIFIER_REST)
    @Bean
    @ConfigurationProperties(prefix = "hono.registry.rest")
    public ServiceConfigProperties httpServerProperties() {
        return new ServiceConfigProperties();
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>devices</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public HttpEndpoint deviceHttpEndpoint() {
        return new AutowiredDeviceManagementHttpEndpoint(vertx());
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>credentials</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public HttpEndpoint credentialsHttpEndpoint() {
        return new AutowiredCredentialsManagementHttpEndpoint(vertx());
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>tenants</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public HttpEndpoint tenantHttpEndpoint() {
        return new AutowiredTenantManagementHttpEndpoint(vertx());
    }

    /**
     * Gets properties for configuring {@link MongoDbBasedRegistrationService} which implements
     * the <em>Device Registration</em> and <em>Device Management Service</em> APIs.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.device.mongodb")
    public MongoDbBasedRegistrationConfigProperties deviceRegistrationProperties() {
        return new MongoDbBasedRegistrationConfigProperties();
    }

    /**
     * Gets properties for the hono tenant mongodb service.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.tenant.mongodb")
    public MongoDbBasedTenantsConfigProperties mongoDbTenantsProperties() {
        return new MongoDbBasedTenantsConfigProperties();
    }

    /**
     * Gets properties for configuring {@code InMemoryDeviceConnectionService} which implements
     * the <em>Device Connection</em> API.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.device-connection.svc")
    public MapBasedDeviceConnectionsConfigProperties deviceConnectionsProperties() {
        return new MapBasedDeviceConnectionsConfigProperties();
    }

    /**
     * Customizer for meter registry.
     *
     * @return The new meter registry customizer.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags() {

        return r -> r.config().commonTags(MetricsTags.forService(Constants.SERVICE_NAME_DEVICE_REGISTRY));

    }

    /**
     * Exposes the health check server as a Spring bean.
     *
     * @return The health check server.
     */
    @Bean
    public HealthCheckServer healthCheckServer() {
        return new VertxBasedHealthCheckServer(vertx(), healthCheckConfigProperties());
    }

}
