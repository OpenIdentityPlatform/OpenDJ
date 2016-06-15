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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.requests.SearchRequest;

/**
 * A naming strategy is responsible for naming JSON resources and LDAP entries.
 */
interface NamingStrategy {
    /**
     * Returns a search request which can be used to obtain the specified JSON resource.
     *
     * @param baseDn
     *         The search base DN.
     * @param resourceId
     *         The resource ID.
     * @return A search request which can be used to obtain the specified JSON resource.
     */
    SearchRequest createSearchRequest(DN baseDn, String resourceId);

    /**
     * Returns the name of the LDAP attribute from which this naming strategy computes the JSON resource ID.
     *
     * @return The name of the LDAP attribute from which this naming strategy computes the JSON resource ID.
     */
    String getResourceIdLdapAttribute();

    /**
     * Decodes the JSON resource ID from the provided LDAP entry. Implementations may use the entry DN as well as any
     * attributes in order to determine the resource ID.
     *
     * @param entry
     *         The LDAP entry from which the resource ID should be obtained.
     * @return The resource ID or {@code null} if the resource ID will be obtained from the resource's "_id" field.
     */
    String decodeResourceId(Entry entry);

    /**
     * Encodes the JSON resource ID in the provided LDAP entry. Implementations are responsible for setting the entry
     * DN as well as any attributes associated with the resource ID.
     *
     * @param baseDn
     *         The base DN to use when constructing the entry's DN.
     * @param resourceId
     *         The resource ID.
     * @param entry
     *         The LDAP entry whose DN and resource ID attributes are to be set.
     * @throws ResourceException
     *         If the resource ID cannot be determined.
     */
    void encodeResourceId(DN baseDn, String resourceId, Entry entry) throws ResourceException;
}
