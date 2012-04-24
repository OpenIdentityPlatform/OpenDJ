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

import java.util.Set;

import org.forgerock.opendj.ldap.Entry;

/**
 *
 */
public final class EntryContainer {

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
    public void readEntry(Context c, String id, Set<String> attributes,
            CompletionHandler<Entry> h) {
        // TODO Auto-generated method stub

    }

}
