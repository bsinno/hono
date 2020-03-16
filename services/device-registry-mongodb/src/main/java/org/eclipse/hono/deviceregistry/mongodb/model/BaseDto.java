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

import org.eclipse.hono.annotation.HonoTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Common abstract wrapper class to wrap device registry entities and add version and timestamp information.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class BaseDto {

    @JsonProperty(value = "version", required = true)
    protected String version;
    @JsonProperty("updatedOn")
    @HonoTimestamp
    protected Instant updatedOn;

    /**
     * Creates dto without values.
     */
    public BaseDto() {
        //Explicit default constructor.
    }

    /**
     * Gets the version of the dto.
     *
     * @return the version or {@code null} if none has been set.
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets the new version.
     *
     * @param version the new version.
     */
    public void setVersion(final String version) {
        this.version = version;
    }

    /**
     * Gets the timestamp of the last update.
     *
     * @return the timestamp or {@code null} if none has been set.
     */
    public Instant getUpdatedOn() {
        return updatedOn;
    }

    /**
     * Sets a new timestamp as the new last update time.
     *
     * @param updatedOn the timestamp.
     */
    public void setUpdatedOn(final Instant updatedOn) {
        this.updatedOn = updatedOn;
    }
}
