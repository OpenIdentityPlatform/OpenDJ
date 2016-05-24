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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.opendj.server.core;

import java.util.Set;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.RequestHandler;

/**
 * A connection to a data provider. When a connection is no longer needed it
 * must be closed.
 */
public interface DataProviderConnection extends RequestHandler<Operation> {

    /**
     * Closes this data provider connection. When this method returns the
     * connection can no longer be used.
     */
    void close();

    /**
     * Indicates whether the underlying data provider contains the specified
     * entry.
     *
     * @param dn
     *            The DN of the entry.
     * @return {@code true} if the underlying data provider contains the
     *         specified entry, or {@code false} if it does not.
     * @throws LdapException
     *             If a problem occurs while trying to make the determination,
     *             or if {@code dn} is not a DN equal to or subordinate to one
     *             of the base DNs managed by the underlying data provider.
     */
    boolean containsEntry(DN dn) throws LdapException;

    /**
     * Deregisters an event listener from the underlying data provider.
     *
     * @param listener
     *            The event listener.
     */
    void deregisterEventListener(DataProviderEventListener listener);

    /**
     * Returns an unmodifiable set containing the base DNs of the sub-trees
     * which the underlying data provider contains.
     *
     * @return An unmodifiable set containing the base DNs of the sub-trees
     *         which the underlying data provider contains.
     */
    Set<DN> getBaseDNs();

    /**
     * Retrieves the specified entry from the underlying data provider.
     *
     * @param dn
     *            The DN of the entry.
     * @return The requested entry, or {@code null} if the underlying data
     *         provider does not contain the specified entry.
     * @throws LdapException
     *             If a problem occurs while trying to retrieve the entry, or if
     *             {@code dn} is not a DN equal to or subordinate to one of the
     *             base DNs managed by the underlying data provider.
     */
    Entry getEntry(DN dn) throws LdapException;

    /**
     * Returns the current status of the provided base DN in the underlying data
     * provider.
     *
     * @param baseDN
     *            The base DN in the underlying data provider.
     * @return The current status of the provided base DN in the underlying data
     *         provider.
     * @throws LdapException
     *             If {@code baseDN} is not one of the base DNs managed by the
     *             underlying data provider.
     */
    DataProviderStatus getStatus(DN baseDN) throws LdapException;

    /**
     * Returns an unmodifiable set containing the OIDs of the controls that may
     * be supported by the provided base DN in the underlying data provider.
     *
     * @param baseDN
     *            The base DN in the underlying data provider.
     * @return An unmodifiable set containing the OIDs of the controls that may
     *         be supported by the provided base DN in the underlying data
     *         provider.
     * @throws LdapException
     *             If {@code baseDN} is not one of the base DNs managed by the
     *             underlying data provider.
     */
    Set<String> getSupportedControls(DN baseDN) throws LdapException;

    /**
     * Returns an unmodifiable set containing the OIDs of the features that may
     * be supported by the provided base DN in the underlying data provider.
     *
     * @param baseDN
     *            The base DN in the underlying data provider.
     * @return An unmodifiable set containing the OIDs of the features that may
     *         be supported by the provided base DN in the underlying data
     *         provider.
     * @throws LdapException
     *             If {@code baseDN} is not one of the base DNs managed by the
     *             underlying data provider.
     */
    Set<String> getSupportedFeatures(DN baseDN) throws LdapException;

    /**
     * Registers an event listener with the underlying data provider.
     *
     * @param listener
     *            The event listener.
     */
    void registerEventListener(DataProviderEventListener listener);

    /**
     * Indicates whether the provided base DN in the underlying data
     * provider supports change notification.
     *
     * @param baseDN
     *            The base DN in the underlying data provider.
     * @return {@code true} if the provided base DN in the underlying data
     *         provider supports change notification.
     * @throws LdapException
     *             If {@code baseDN} is not one of the base DNs managed by the
     *             underlying data provider.
     */
    boolean supportsChangeNotification(DN baseDN) throws LdapException;
}
