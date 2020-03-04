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

package org.eclipse.hono.deviceregistry.mongodb.model;

import java.time.Instant;

import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TODO.
 */
public class TenantDto extends BaseDto {

    @JsonProperty(value = RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID, required = true)
    private String tenantId;
    @JsonProperty("tenant")
    private Tenant tenant;

    /**
     * TODO.
     */
    public TenantDto() {
        // Explicit default constructor.
    }

    /**
     * @param tenantId The tenant id.
     * @param version The version of tenant to be sent as request header.
     * @param tenant The tenant.
     * @param updatedOn TODO.
     */
    public TenantDto(final String tenantId, final String version, final Tenant tenant, final Instant updatedOn) {
        this.tenantId = tenantId;
        this.version = version;
        this.tenant = tenant;
        this.updatedOn = updatedOn;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(final String tenantId) {
        this.tenantId = tenantId;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(final Tenant tenant) {
        this.tenant = tenant;
    }

}
