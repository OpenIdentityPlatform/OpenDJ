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

import java.util.Collection;

import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.resource.provider.Context;

/**
 *
 */
public final class EntryContainer {

    /**
     * @param entry
     * @return
     */
    public String getIDFromEntry(final SearchResultEntry entry) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * @param context
     * @param handler
     */
    public void listEntries(final Context context, final SearchResultHandler handler) {
        // TODO Auto-generated method stub

    }

    /**
     * Reads the entry having the specified resource ID.
     *
     * @param c
     *            The request context.
     * @param id
     *            The resource ID.
     * @param attributes
     *            The set of LDAP attributes to be read.
     * @param h
     *            The result handler.
     */
    public void readEntry(final Context c, final String id, final Collection<String> attributes,
            final ResultHandler<SearchResultEntry> h) {
        // TODO Auto-generated method stub

    }

}
