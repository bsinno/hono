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

import java.util.List;

import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A versioned and dated wrapper class for {@link CommonCredential}.
 */
public class CredentialsDto extends BaseDto {

    @JsonProperty(RegistryManagementConstants.CREDENTIALS_ENDPOINT)
    private List<CommonCredential> credentials;

    /**
     * Creates a new empty credential dto.
     */
    public CredentialsDto() {
        //Explicit default constructor.
    }

    /**
     * Gets a list of {@link CommonCredential}.
     *
     * @return the credentials
     */
    public List<CommonCredential> getCredentials() {
        return credentials;
    }

    /**
     * Sets a list of {@link CommonCredential}.
     *
     * @param credentials the credentials
     */
    public void setCredentials(final List<CommonCredential> credentials) {
        this.credentials = credentials;
    }
}
