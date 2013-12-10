/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.admin.client.ldap;

import java.util.Collection;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;

/**
 * An LDAP connection adaptor interface which is used to perform LDAP client
 * operations.
 * <p>
 * This interface is provided in order to make it easier to keep track of which
 * JNDI DirContext methods we require and also to facilitate implementation of
 * mock JNDI contexts for unit-testing.
 */
public abstract class LDAPConnection {

    /**
     * Create a new LDAP connection.
     */
    protected LDAPConnection() {
        // No implementation required.
    }

    /**
     * Creates a new entry.
     *
     * @param entry
     *            The entry to create.
     * @throws ErrorResultException
     *             If an error occurred whilst creating the entry.
     */
    public abstract void createEntry(Entry entry) throws ErrorResultException;

    /**
     * Deletes the named subtree.
     *
     * @param dn
     *            The name of the subtree to be deleted.
     * @throws ErrorResultException
     *             If an error occurred whilst deleting the subtree.
     */
    public abstract void deleteSubtree(DN dn) throws ErrorResultException;

    /**
     * Determines whether or not the named entry exists.
     *
     * @param dn
     *            The name of the entry.
     * @return Returns <code>true</code> if the entry exists.
     * @throws ErrorResultException
     *             If an error occurred whilst making the determination.
     */
    public abstract boolean entryExists(DN dn) throws ErrorResultException;

    /**
     * Lists the children of the named entry.
     *
     * @param dn
     *            The name of the entry to list.
     * @param filter
     *            An LDAP filter string, or <code>null</code> indicating the
     *            default filter of <code>(objectclass=*)</code>.
     * @return Returns the names of the children.
     * @throws ErrorResultException
     *             If an error occurred whilst listing the children.
     */
    public abstract Collection<DN> listEntries(DN dn, String filter) throws ErrorResultException;

    /**
     * Modifies an entry according to the provided modify request.
     *
     * @param request
     *            The modification request to perform.
     * @throws ErrorResultException
     *             If an error occurred whilst applying the modifications.
     */
    public abstract void modifyEntry(ModifyRequest request) throws ErrorResultException;

    /**
     * Reads the attributes of the named entry.
     *
     * @param dn
     *            The name of the entry to be read.
     * @param attrIds
     *            The list of attributes to be retrievd.
     * @return Returns the attributes of the requested entry.
     * @throws ErrorResultException
     *             If an error occurred whilst reading the entry.
     */
    public abstract SearchResultEntry readEntry(DN dn, Collection<String> attrIds) throws ErrorResultException;

    /**
     * Closes the LDAP connection.
     */
    public abstract void unbind();
}
