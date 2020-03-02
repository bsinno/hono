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

import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;

/**
 * TODO.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class MongoDbErrorHandler {

    /**
     * TODO.
     *
     * @param throwable Mongodb exception
     * @return if exception is an DUPLICATE_KEY exception
     */
    public static boolean ifDuplicateKeyError(final Throwable throwable) {
        if (throwable instanceof MongoException) {
            final MongoException mongoException = (MongoException) throwable;
            return ErrorCategory.fromErrorCode(mongoException.getCode()) == ErrorCategory.DUPLICATE_KEY;
        }
        return false;
    }

}
