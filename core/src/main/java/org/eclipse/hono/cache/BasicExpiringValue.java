/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.cache;

import java.time.Instant;
import java.util.Objects;

/**
 * A base class for implementing an expiring value.
 *
 * @param <T> The value type.
 */
public class BasicExpiringValue<T> implements ExpiringValue<T> {

    private final T value;
    private final Instant expirationTime;

    /**
     * Creates a new instance for a value and an expiration time.
     *
     * @param value The value.
     * @param expirationTime The instant after which the value will be considered expired.
     */
    public BasicExpiringValue(final T value, final Instant expirationTime) {
        this.value = Objects.requireNonNull(value);
        this.expirationTime = Objects.requireNonNull(expirationTime);
    }

    @Override
    public final T getValue() {
        return value;
    }

    @Override
    public boolean isExpired() {
        return isExpired(Instant.now());
    }

    @Override
    public boolean isExpired(final Instant now) {
        Objects.requireNonNull(now);
        return now.isAfter(expirationTime);
    }
}
