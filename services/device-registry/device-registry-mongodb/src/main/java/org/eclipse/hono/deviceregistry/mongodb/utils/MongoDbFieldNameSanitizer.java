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

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.function.Function;

/**
 * Utility to sanitize field names of mongodb documents. Replaces dot
 * (<code>"{@value #DOT_CHAR}"</code>) and dollar
 * (<code>"{@value #DOLLAR_CHAR}"</code>) characters in field names with their
 * unicode counterparts for input documents replaces vice versa for output
 * documents.
 * <p>
 * Needs to be used for user provided field names to allow dot and dollar
 * characters.
 */
public class MongoDbFieldNameSanitizer {
    private static final char DOLLAR_CHAR = '$';
    private static final char DOLLAR_UNICODE_CHAR = '\uFF04';
    private static final char DOT_CHAR = '.';
    private static final char DOT_UNICODE_CHAR = '\uFF0E';
    private static final Function<String, String> SANITIZE_INPUT_FUNCTION = fieldName -> fieldName
            .replace(DOLLAR_CHAR, DOLLAR_UNICODE_CHAR)
            .replace(DOT_CHAR, DOT_UNICODE_CHAR);
    private static final Function<String, String> SANITIZE_OUTPUT_FUNCTION = fieldName -> fieldName
            .replace(DOLLAR_UNICODE_CHAR, DOLLAR_CHAR)
            .replace(DOT_UNICODE_CHAR, DOT_CHAR);

    /**
     * To sanitize data before save into mongodb.
     *
     * @param input the object before saved in mongodb
     * @return the sanitized JSON object
     */
    public JsonObject sanitizeInput(final JsonObject input) {
        return sanitize(input, SANITIZE_INPUT_FUNCTION);
    }

    /**
     * To sanitize data before save into mongodb.
     *
     * @param input the array before saved in mongodb
     * @return the sanitized JSON array
     */
    public JsonArray sanitizeInput(final JsonArray input) {
        return sanitize(input, SANITIZE_INPUT_FUNCTION);
    }

    /**
     * To undo {@link #sanitizeInput(JsonObject)} after retrieved document from
     * mongodb.
     *
     * @param output the document retrieved from mongodb
     * @return the sanitized JSON object
     */
    public JsonObject sanitizeOutput(final JsonObject output) {
        return sanitize(output, SANITIZE_OUTPUT_FUNCTION);
    }

    /**
     * To undo {@link #sanitizeInput(JsonArray)} after retrieved document from
     * mongodb.
     *
     * @param output the document retrieved from mongodb
     * @return the sanitized JSON array
     */
    public JsonArray sanitizeOutput(final JsonArray output) {
        return sanitize(output, SANITIZE_OUTPUT_FUNCTION);
    }

    private JsonObject sanitize(final JsonObject nonSanitizedObject, final Function<String, String> sanitizeFunction) {
        if (nonSanitizedObject == null) {
            return null;
        }
        final JsonObject sanitizedObject = new JsonObject();
        nonSanitizedObject.forEach(entry -> {
            final String sanitizedFieldName = sanitizeFunction.apply(entry.getKey());
            if (entry.getValue() instanceof JsonObject) {
                sanitizedObject.put(sanitizedFieldName, sanitize((JsonObject) entry.getValue(), sanitizeFunction));
            } else if (entry.getValue() instanceof JsonArray) {
                sanitizedObject.put(sanitizedFieldName, sanitize((JsonArray) entry.getValue(), sanitizeFunction));
            } else {
                sanitizedObject.put(sanitizedFieldName, entry.getValue());
            }
        });
        return sanitizedObject;
    }

    private JsonArray sanitize(final JsonArray nonSanitizedArray, final Function<String, String> sanitizeFunction) {
        if (nonSanitizedArray == null) {
            return null;
        }
        final JsonArray sanitizedArray = new JsonArray();
        nonSanitizedArray.forEach(entry -> {
            if (entry instanceof JsonObject) {
                sanitizedArray.add(sanitize((JsonObject) entry, sanitizeFunction));
            } else if (entry instanceof JsonArray) {
                sanitizedArray.add(sanitize((JsonArray) entry, sanitizeFunction));
            } else {
                sanitizedArray.add(entry);
            }
        });
        return sanitizedArray;
    }

}
