/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2013-2015 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static org.forgerock.json.JsonValue.json;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.Responses;
import org.forgerock.services.context.RootContext;

/** Unit test utility methods, including fluent methods for creating JSON objects. */
public final class TestUtils {

    /**
     * Returns a {@code Resource} containing the provided JSON content. The ID
     * and revision will be taken from the "_id" and "_rev" fields respectively.
     *
     * @param content
     *            The JSON content.
     * @return A {@code Resource} containing the provided JSON content.
     */
    public static ResourceResponse asResource(final JsonValue content) {
        return Responses.newResourceResponse(content.get("_id").asString(), content.get("_rev").asString(), content);
    }

    /**
     * Creates a JSON value for the provided object.
     *
     * @param object
     *            The object.
     * @return The JSON value.
     */
    public static JsonValue content(final Object object) {
        return json(object);
    }

    /**
     * Creates a root context to be passed in with client requests.
     *
     * @return The root context.
     */
    public static RootContext ctx() {
        return new RootContext();
    }

    /**
     * Creates a JSON value for the provided object. This is the same as
     * {@link #content(Object)} but can yield more readable test data in data
     * providers.
     *
     * @param object
     *            The object.
     * @return The JSON value.
     */
    public static JsonValue expected(final Object object) {
        return json(object);
    }

    /**
     * Creates a list of JSON pointers from the provided string representations.
     *
     * @param fields
     *            The list of JSON pointer strings.
     * @return The list of parsed JSON pointers.
     */
    public static List<JsonPointer> filter(final String... fields) {
        final List<JsonPointer> result = new ArrayList<>(fields.length);
        for (final String field : fields) {
            result.add(new JsonPointer(field));
        }
        return result;
    }

    private TestUtils() {
        // Prevent instantiation.
    }

}
