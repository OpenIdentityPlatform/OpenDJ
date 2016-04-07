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

import java.util.Set;

import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.requests.SearchRequest;

/**
 * A name strategy is responsible for naming REST resources and LDAP entries.
 */
abstract class NameStrategy {
    /*
     * This interface is an abstract class so that methods can be made package
     * private until API is finalized.
     */

    NameStrategy() {
        // Nothing to do.
    }

    /**
     * Returns a search request which can be used to obtain the specified REST
     * resource.
     *
     * @param requestState
     *            The request state.
     * @param baseDN
     *            The search base DN.
     * @param resourceId
     *            The resource ID.
     * @return A search request which can be used to obtain the specified REST
     *         resource.
     */
    abstract SearchRequest createSearchRequest(RequestState requestState, DN baseDN, String resourceId);

    /**
     * Adds the name of any LDAP attribute required by this name strategy to the
     * provided set.
     *
     * @param requestState
     *            The request state.
     * @param ldapAttributes
     *            The set into which any required LDAP attribute name should be
     *            put.
     */
    abstract void getLDAPAttributes(RequestState requestState, Set<String> ldapAttributes);

    /**
     * Retrieves the resource ID from the provided LDAP entry. Implementations
     * may use the entry DN as well as any attributes in order to determine the
     * resource ID.
     *
     * @param requestState
     *            The request state.
     * @param entry
     *            The LDAP entry from which the resource ID should be obtained.
     * @return The resource ID.
     */
    abstract String getResourceId(RequestState requestState, Entry entry);

    /**
     * Sets the resource ID in the provided LDAP entry. Implementations are
     * responsible for setting the entry DN as well as any attributes associated
     * with the resource ID.
     *
     * @param requestState
     *            The request state.
     * @param baseDN
     *            The baseDN to use when constructing the entry's DN.
     * @param resourceId
     *            The resource ID.
     * @param entry
     *            The LDAP entry whose DN and resource ID attributes are to be
     *            set.
     * @throws ResourceException
     *             If the resource ID cannot be determined.
     */
    abstract void setResourceId(RequestState requestState, DN baseDN, String resourceId, Entry entry)
            throws ResourceException;

}
