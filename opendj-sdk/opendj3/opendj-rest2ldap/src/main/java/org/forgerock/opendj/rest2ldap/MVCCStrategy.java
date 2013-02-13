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
 * Copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.rest2ldap;

import java.util.Set;

import org.forgerock.opendj.ldap.Entry;

/**
 * A multi-version concurrency control strategy is responsible for ensuring that
 * clients can perform atomic updates to LDAP resources.
 */
abstract class MVCCStrategy {
    /*
     * This interface is an abstract class so that methods can be made package
     * private until API is finalized.
     */

    MVCCStrategy() {
        // Nothing to do.
    }

    /**
     * Adds the name of any LDAP attribute required by this MVCC strategy to the
     * provided set.
     *
     * @param c
     *            The context.
     * @param ldapAttributes
     *            The set into which any required LDAP attribute name should be
     *            put.
     */
    abstract void getLDAPAttributes(Context c, Set<String> ldapAttributes);

    /**
     * Retrieves the revision value (etag) from the provided LDAP entry.
     *
     * @param c
     *            The context.
     * @param entry
     *            The LDAP entry.
     * @return The revision value.
     */
    abstract String getRevisionFromEntry(Context c, Entry entry);

}
