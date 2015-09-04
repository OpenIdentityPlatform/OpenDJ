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
 * Copyright 2012-2015 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import java.util.List;
import java.util.Set;

import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.PatchOperation;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.util.promise.Promise;

/** An attribute mapper is responsible for converting JSON values to and from LDAP attributes. */
public abstract class AttributeMapper {
    /*
     * This interface is an abstract class so that methods can be made package
     * private until API is finalized.
     */

    AttributeMapper() {
        // Nothing to do.
    }

    /**
     * Maps a JSON value to one or more LDAP attributes, returning a promise
     * once the transformation has completed. This method is invoked when a REST
     * resource is created using a create request.
     * <p>
     * If the JSON value corresponding to this mapper is not present in the
     * resource then this method will be invoked with a value of {@code null}.
     * It is the responsibility of the mapper implementation to take appropriate
     * action in this case, perhaps by substituting default LDAP values, or by
     * returning a failed promise with an appropriate {@link ResourceException}.
     *
     * @param requestState
     *            The request state.
     * @param path
     *            The pointer from the root of the JSON resource to this
     *            attribute mapper. This may be used when constructing error
     *            messages.
     * @param v
     *            The JSON value to be converted to LDAP attributes, which may
     *            be {@code null} indicating that the JSON value was not present
     *            in the resource.
     * @return A {@link Promise} containing the result of the operation.
     */
    abstract Promise<List<Attribute>, ResourceException> create(
            RequestState requestState, JsonPointer path, JsonValue v);

    /**
     * Adds the names of the LDAP attributes required by this attribute mapper
     * to the provided set.
     * <p>
     * Implementations should only add the names of attributes found in the LDAP
     * entry directly associated with the resource.
     *
     * @param requestState
     *            The request state.
     * @param path
     *            The pointer from the root of the JSON resource to this
     *            attribute mapper. This may be used when constructing error
     *            messages.
     * @param subPath
     *            The targeted JSON field relative to this attribute mapper, or
     *            root if all attributes associated with this mapper have been
     *            targeted.
     * @param ldapAttributes
     *            The set into which the required LDAP attribute names should be
     *            put.
     */
    abstract void getLDAPAttributes(
            RequestState requestState, JsonPointer path, JsonPointer subPath, Set<String> ldapAttributes);

    /**
     * Transforms the provided REST comparison filter parameters to an LDAP
     * filter representation, returning a promise once the transformation has
     * completed.
     * <p>
     * If an error occurred while constructing the LDAP filter, then a failed
     * promise must be returned with an appropriate {@link ResourceException}
     * indicating the problem which occurred.
     *
     * @param requestState
     *            The request state.
     * @param path
     *            The pointer from the root of the JSON resource to this
     *            attribute mapper. This may be used when constructing error
     *            messages.
     * @param subPath
     *            The targeted JSON field relative to this attribute mapper, or
     *            root if all attributes associated with this mapper have been
     *            targeted.
     * @param type
     *            The type of REST comparison filter.
     * @param operator
     *            The name of the extended operator to use for the comparison,
     *            or {@code null} if {@code type} is not
     *            {@link FilterType#EXTENDED}.
     * @param valueAssertion
     *            The value assertion, or {@code null} if {@code type} is
     *            {@link FilterType#PRESENT}.
     * @return A {@link Promise} containing the result of the operation.
     */
    abstract Promise<Filter, ResourceException> getLDAPFilter(RequestState requestState, JsonPointer path,
            JsonPointer subPath, FilterType type, String operator, Object valueAssertion);

    /**
     * Maps a JSON patch operation to one or more LDAP modifications, returning
     * a promise once the transformation has completed. This method is invoked
     * when a REST resource is modified using a patch request.
     *
     * @param requestState
     *            The request state.
     * @param path
     *            The pointer from the root of the JSON resource to this
     *            attribute mapper. This may be used when constructing error
     *            messages.
     * @param operation
     *            The JSON patch operation to be converted to LDAP
     *            modifications. The targeted JSON field will be relative to
     *            this attribute mapper, or root if all attributes associated
     *            with this mapper have been targeted.
     * @return A {@link Promise} containing the result of the operation.
     */
    abstract Promise<List<Modification>, ResourceException> patch(
            RequestState requestState, JsonPointer path, PatchOperation operation);

    /**
     * Maps one or more LDAP attributes to their JSON representation, returning
     * a promise once the transformation has completed.
     * <p>
     * This method is invoked whenever an LDAP entry is converted to a REST
     * resource, i.e. when responding to read, query, create, put, or patch
     * requests.
     * <p>
     * If the LDAP attributes are not present in the entry, perhaps because they
     * are optional, then implementations should return a successful promise
     * with a result of {@code null}. If the LDAP attributes cannot be mapped
     * for any other reason, perhaps because they are required but missing, or
     * they contain unexpected content, then a failed promise must be returned
     * with an appropriate exception indicating the problem which occurred.
     *
     * @param requestState
     *            The request state.
     * @param path
     *            The pointer from the root of the JSON resource to this
     *            attribute mapper. This may be used when constructing error
     *            messages.
     * @param e
     *            The LDAP entry to be converted to JSON.
     * @return A {@link Promise} containing the result of the operation.
     */
    abstract Promise<JsonValue, ResourceException> read(RequestState requestState, JsonPointer path, Entry e);

    /**
     * Maps a JSON value to one or more LDAP modifications, returning a promise
     * once the transformation has completed. This method is invoked when a REST
     * resource is modified using an update request.
     * <p>
     * If the JSON value corresponding to this mapper is not present in the
     * resource then this method will be invoked with a value of {@code null}.
     * It is the responsibility of the mapper implementation to take appropriate
     * action in this case, perhaps by substituting default LDAP values, or by
     * returning a failed promise with an appropriate {@link ResourceException}.
     *
     * @param requestState
     *            The request state.
     * @param v
     *            The JSON value to be converted to LDAP attributes, which may
     *            be {@code null} indicating that the JSON value was not present
     *            in the resource.
     * @return A {@link Promise} containing the result of the operation.
     */
    abstract Promise<List<Modification>, ResourceException> update(
            RequestState requestState, JsonPointer path, Entry e, JsonValue v);

    // TODO: methods for obtaining schema information (e.g. name, description, type information).
    // TODO: methods for creating sort controls.
}
