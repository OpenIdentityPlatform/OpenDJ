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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright 2012 ForgeRock AS. All rights reserved.
 */

package org.forgerock.opendj.rest2ldap;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.json.fluent.JsonPointer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.opendj.ldap.Entry;

/**
 *
 */
public class LDAPResource {
    // FIXME: This will inherit from JsonResource.
    private EntryContainer container;
    private List<AttributeMapper> mappers;
    private Set<String> allLDAPAttributes;

    /**
     * Creates a new LDAP resource.
     *
     * @param container
     *            The entry container which will be used to interact with the
     *            LDAP server.
     * @param mappers
     *            The list of attribute mappers.
     */
    public LDAPResource(EntryContainer container, List<AttributeMapper> mappers) {
        this.container = container;
        this.mappers = mappers;
        cacheAllLDAPAttributes();
    }

    /**
     * Caches the set of LDAP attributes associated with all of this resource's
     * mappers.
     */
    private void cacheAllLDAPAttributes() {
        allLDAPAttributes = new LinkedHashSet<String>(mappers.size());
        for (AttributeMapper mapper : mappers) {
            allLDAPAttributes.addAll(mapper.getAllLDAPAttributes());
        }
    }

    /**
     * Reads a resource from the LDAP directory.
     *
     * @param c
     *            The request context.
     * @param r
     *            The read request.
     * @param h
     *            The read result handler.
     */
    public void read(final Context c, final ReadRequest r,
            final CompletionHandler<JsonValue> h) {
        // Determine the set of LDAP attributes that need to be read.
        final Set<JsonPointer> requestedAttributes = r.getRequestedAttributes();
        Set<String> requestedLDAPAttributes = getRequestedLDAPAttributes(requestedAttributes);

        // Create a completion handler which will convert the entry to a
        // JsonValue.
        CompletionHandler<Entry> eh = new CompletionHandler<Entry>() {
            public void failed(Throwable t) {
                // Unable to read the entry.
                handleReadFailure(c, h, t);
            }

            public void completed(Entry result) {
                // Convert the entry to a JsonValue.
                mapEntryToJson(c, requestedAttributes, result, h);
            }

        };

        // Read the entry.
        container.readEntry(c, r.getResourceID(), requestedLDAPAttributes, eh);
    }

    /**
     * Determines the set of LDAP attributes to request in an LDAP read (search,
     * post-read), based on the provided set of JSON pointers.
     *
     * @param requestedAttributes
     *            The set of resource attributes to be read.
     * @return The set of LDAP attributes associated with the resource
     *         attributes.
     */
    private Set<String> getRequestedLDAPAttributes(
            Set<JsonPointer> requestedAttributes) {
        if (requestedAttributes.isEmpty()) {
            // Full read.
            return allLDAPAttributes;
        } else {
            // Partial read.
            Set<String> requestedLDAPAttributes = new LinkedHashSet<String>(
                    requestedAttributes.size());
            for (JsonPointer requestedAttribute : requestedAttributes) {
                for (AttributeMapper mapper : mappers) {
                    requestedLDAPAttributes.addAll(mapper
                            .getLDAPAttributesFor(requestedAttribute));
                }
            }
            return requestedLDAPAttributes;
        }
    }

    private void handleReadFailure(Context c, CompletionHandler<JsonValue> h,
            Throwable t) {
        // TODO Auto-generated method stub

    }

    private void mapEntryToJson(Context c,
            Set<JsonPointer> requestedAttributes, Entry result,
            CompletionHandler<JsonValue> h) {
        // TODO Auto-generated method stub

    }
}
