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
 * Copyright 2013 ForgeRock Inc.
 */
package org.forgerock.opendj.rest2ldap;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.Resource;
import org.forgerock.json.resource.RootContext;

/**
 * Unit test utility methods, including fluent methods for creating JSON
 * objects.
 */
public final class TestUtils {

    /**
     * Creates a JSON array object.
     *
     * @param objects
     *            The array elements.
     * @return A JSON array.
     */
    public static Object array(final Object... objects) {
        return Arrays.asList(objects);
    }

    /**
     * Returns a {@code Resource} containing the provided JSON content. The ID
     * and revision will be taken from the "_id" and "_rev" fields respectively.
     *
     * @param content
     *            The JSON content.
     * @return A {@code Resource} containing the provided JSON content.
     */
    public static Resource asResource(final JsonValue content) {
        return new Resource(content.get("_id").asString(), content.get("_rev").asString(), content);
    }

    /**
     * Creates a JSON value for the provided object.
     *
     * @param object
     *            The object.
     * @return The JSON value.
     */
    public static JsonValue content(final Object object) {
        return new JsonValue(object);
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
        return content(object);
    }

    /**
     * Creates a JSON field for inclusion in a JSON object using
     * {@link #object(java.util.Map.Entry...)}.
     *
     * @param key
     *            The JSON field name.
     * @param value
     *            The JSON field value.
     * @return The JSON field for inclusion in a JSON object.
     */
    public static Map.Entry<String, Object> field(final String key, final Object value) {
        return new AbstractMap.SimpleImmutableEntry<String, Object>(key, value);
    }

    /**
     * Creates a list of JSON pointers from the provided string representations.
     *
     * @param fields
     *            The list of JSON pointer strings.
     * @return The list of parsed JSON pointers.
     */
    public static List<JsonPointer> filter(final String... fields) {
        final List<JsonPointer> result = new ArrayList<JsonPointer>(fields.length);
        for (final String field : fields) {
            result.add(new JsonPointer(field));
        }
        return result;
    }

    /**
     * Creates a JSON object comprised of the provided JSON
     * {@link #field(String, Object) fields}.
     *
     * @param fields
     *            The list of {@link #field(String, Object) fields} to include
     *            in the JSON object.
     * @return The JSON object.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static Object object(final Map.Entry... fields) {
        final Map<String, Object> object = new LinkedHashMap<String, Object>(fields.length);
        for (final Map.Entry<String, Object> field : fields) {
            object.put(field.getKey(), field.getValue());
        }
        return object;
    }

    private TestUtils() {
        // Prevent instantiation.
    }

}
