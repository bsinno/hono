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
 * A versioned and dated wrapper class for {@link Device}.
 */
public class DeviceDto extends BaseDto {

    @JsonProperty(value = RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID, required = true)
    private String tenantId;

    @JsonProperty(value = RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID, required = true)
    private String deviceId;

    @JsonProperty("device")
    private Device device;

    /**
     * Creates a new empty device dto.
     */
    public DeviceDto() {
        // Explicit default constructor.
    }

    /**
     * @param tenantId  The tenant identifier.
     * @param deviceId  The device identifier.
     * @param device    The device.
     * @param version   The version of tenant to be sent as request header.
     * @param updatedOn The timestamp of creation.
     */
    public DeviceDto(final String tenantId, final String deviceId, final Device device, final String version,
                     final Instant updatedOn) {
        this.tenantId = tenantId;
        this.deviceId = deviceId;
        this.device = device;
        this.version = version;
        this.updatedOn = updatedOn;
    }

    /**
     * Gets the tenant id for the device.
     *
     * @return the tenant id or {@code null} if none has been set..
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Sets the tenant id for this device.
     *
     * @param tenantId the tenant id.
     */
    public void setTenantId(final String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * Gets the device id.
     *
     * @return the device id or {@code null} if none has been set.
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Sets the device id.
     * <p>
     * Have to be conflict free with present devices.
     *
     * @param deviceId the device id.
     */
    public void setDeviceId(final String deviceId) {
        this.deviceId = deviceId;
    }

    /**
     * Gets the {@link Device}.
     *
     * @return the device  or {@code null} if none has been set.
     */
    public Device getDevice() {
        return device;
    }

    /**
     * Sets the {@link Device}.
     *
     * @param device the device.
     */
    public void setDevice(final Device device) {
        this.device = device;
    }
}
