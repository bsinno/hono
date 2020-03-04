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

import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TODO.
 */
public class DeviceDto extends BaseDto {

    @JsonProperty(value = RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID, required = true)
    private String tenantId;

    @JsonProperty(value = RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID, required = true)
    private String deviceId;

    @JsonProperty("device")
    private Device device;

    /**
     * TODO.
     */
    public DeviceDto() {
        // Explicit default constructor.
    }

    /**
     * @param tenantId The tenant identifier.
     * @param deviceId The device identifier.
     * @param device The device.
     * @param version The version of tenant to be sent as request header.
     * @param updatedOn TODO.
     */
    public DeviceDto(final String tenantId, final String deviceId, final Device device, final String version,
            final Instant updatedOn) {
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.device = device;
        this.version = version;
        this.updatedOn = updatedOn;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(final String tenantId) {
        this.tenantId = tenantId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(final String deviceId) {
        this.deviceId = deviceId;
    }

    public Device getDevice() {
        return device;
    }

    public void setDevice(final Device device) {
        this.device = device;
    }
}
