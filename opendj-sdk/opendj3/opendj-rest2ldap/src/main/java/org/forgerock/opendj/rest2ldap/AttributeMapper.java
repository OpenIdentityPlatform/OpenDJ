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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2012-2013 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import java.util.List;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Modification;

/**
 * An attribute mapper is responsible for converting JSON values to and from
 * LDAP attributes.
 */
public abstract class AttributeMapper {
    /*
     * This interface is an abstract class so that methods can be made package
     * private until API is finalized.
     */

    AttributeMapper() {
        // Nothing to do.
    }

    /**
     * Adds the names of the LDAP attributes required by this attribute mapper
     * to the provided set.
     * <p>
     * Implementations should only add the names of attributes found in the LDAP
     * entry directly associated with the resource.
     *
     * @param c
     *            The context.
     * @param jsonAttribute
     *            The name of the requested sub-attribute within this mapper or
     *            root if all attributes associated with this mapper have been
     *            requested.
     * @param ldapAttributes
     *            The set into which the required LDAP attribute names should be
     *            put.
     */
    abstract void getLDAPAttributes(Context c, JsonPointer path, JsonPointer subPath,
            Set<String> ldapAttributes);

    /**
     * Transforms the provided REST comparison filter parameters to an LDAP
     * filter representation, invoking a completion handler once the
     * transformation has completed.
     * <p>
     * If an error occurred while constructing the LDAP filter, then the result
     * handler's {@link ResultHandler#handleError handleError} method must be
     * invoked with an appropriate exception indicating the problem which
     * occurred.
     *
     * @param c
     *            The context.
     * @param type
     *            The type of REST comparison filter.
     * @param jsonAttribute
     *            The name of the targeted sub-attribute within this mapper or
     *            root if all attributes associated with this mapper have been
     *            targeted by the filter.
     * @param operator
     *            The name of the extended operator to use for the comparison,
     *            or {@code null} if {@code type} is not
     *            {@link FilterType#EXTENDED}.
     * @param valueAssertion
     *            The value assertion, or {@code null} if {@code type} is
     *            {@link FilterType#PRESENT}.
     * @param h
     *            The result handler.
     */
    abstract void getLDAPFilter(Context c, JsonPointer path, JsonPointer subPath, FilterType type,
            String operator, Object valueAssertion, ResultHandler<Filter> h);

    /**
     * Maps one or more LDAP attributes to their JSON representation, invoking a
     * completion handler once the transformation has completed.
     * <p>
     * This method is invoked whenever an LDAP entry is converted to a REST
     * resource, i.e. when responding to read, query, create, put, or patch
     * requests.
     * <p>
     * If the LDAP attributes are not present in the entry, perhaps because they
     * are optional, then implementations should invoke the result handler's
     * {@link ResultHandler#handleResult handleResult} method with a result of
     * {@code null}. If the LDAP attributes cannot be mapped for any other
     * reason, perhaps because they are required but missing, or they contain
     * unexpected content, then the result handler's
     * {@link ResultHandler#handleError handleError} method must be invoked with
     * an appropriate exception indicating the problem which occurred.
     *
     * @param c
     *            The context.
     * @param e
     *            The LDAP entry to be converted to JSON.
     * @param h
     *            The result handler.
     */
    abstract void toJSON(Context c, JsonPointer path, Entry e, ResultHandler<JsonValue> h);

    /**
     * Maps a JSON value to one or more LDAP attributes, invoking a completion
     * handler once the transformation has completed.
     * <p>
     * This method is invoked whenever a REST resource is converted to an LDAP
     * entry or LDAP modification, i.e. when performing create, put, or patch
     * requests.
     * <p>
     * If the JSON value corresponding to this mapper is not present in the
     * resource then this method will be invoked with a value of {@code null}.
     * It is the responsibility of the mapper implementation to take appropriate
     * action in this case, perhaps by substituting default LDAP values, or by
     * rejecting the update by invoking the result handler's
     * {@link ResultHandler#handleError handleError} method.
     *
     * @param c
     *            The context.
     * @param v
     *            The JSON value to be converted to LDAP attributes, which may
     *            be {@code null} indicating that the JSON value was not present
     *            in the resource.
     * @param h
     *            The result handler.
     */
    abstract void toLDAP(Context c, JsonPointer path, Entry e, JsonValue v,
            ResultHandler<List<Modification>> h);

    // TODO: methods for obtaining schema information (e.g. name, description,
    // type information).
    // TODO: methods for creating sort controls.
}
