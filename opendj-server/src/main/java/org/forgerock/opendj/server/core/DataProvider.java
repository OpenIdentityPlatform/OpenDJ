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
 * An entry container which provides the content of one or more sub-trees.
 * <p>
 * A data provider can be:
 * <ul>
 * <li>a simple data source such as a local back-end, a remote LDAP server or a
 * local LDIF file.
 * <li>used to route operations. This is the case for load balancing and
 * distribution.
 * <li>combine and transform data from underlying data providers. For example,
 * DN mapping, attribute renaming, attribute value transformations, etc.
 * </ul>
 * Data providers operate in two states:
 * <ul>
 * <li>initialized
 * <li>accepting requests
 * </ul>
 * Data providers are created in the <i>initialized</i> state. In this state a
 * data provider has validated its configuration and registered support for
 * off-line services such as export, import, backup, and restore if available.
 * <p>
 * A data provider transitions to the <i>accepting requests</i> state when the
 * {@link #startDataProvider()} method is invoked. In this state a data provider
 * has acquired any remaining resources that it needs in order to be fully
 * operational. This may include connections to underlying data providers. See
 * the documentation for {@link #startDataProvider()} for more information.
 * <p>
 * A data provider transitions back to the <i>initialized</i> state using the
 * {@link #stopDataProvider()} method. This occurs when the data provider is no
 * longer needed in order process client requests, but may still be needed in
 * order to perform off-line services such as import, export, backup, and
 * restore.
 * <p>
 * If data provider is disabled or deleted from the server configuration or if
 * the server is shutdown, then the {@link #finalizeDataProvider()} method is
 * invoked. This method should ensure that the data provider is stopped and no
 * longer available for off-line services such as import, export, backup, and
 * restore.
 */
public interface DataProvider extends RequestHandler<Operation> {

    /**
     * Indicates whether this data provider contains the specified entry.
     *
     * @param dn
     *            The DN of the entry.
     * @return {@code true} if this data provider contains the specified entry,
     *         or {@code false} if it does not.
     * @throws LdapException
     *             If a problem occurs while trying to make the determination,
     *             or if {@code dn} is not a DN equal to or subordinate to one
     *             of the base DNs managed by this data provider.
     */
    boolean containsEntry(DN dn) throws LdapException;

    /**
     * Deregisters an event listener from this data provider.
     *
     * @param listener
     *            The event listener.
     */
    void deregisterEventListener(DataProviderEventListener listener);

    /**
     * Performs any necessary work to finalize this data provider. This may
     * include closing any connections to underlying data providers, databases,
     * and deregistering any listeners, etc.
     * <p>
     * This method may be called during the Directory Server shutdown process or
     * if a data provider is disabled with the server online. It must not return
     * until this data provider is finalized.
     * <p>
     * Implementations should assume that this data provider has already been
     * stopped using {@link #stopDataProvider()}.
     * <p>
     * Implementations must deregister any listeners such as those required for
     * performing import, export, backup, and restore.
     * <p>
     * Implementations must not throw any exceptions. If any problems are
     * encountered, then they may be logged but the closure should progress as
     * completely as possible.
     */
    void finalizeDataProvider();

    /**
     * Returns an unmodifiable set containing the base DNs of the sub-trees
     * which this data provider contains.
     *
     * @return An unmodifiable set containing the base DNs of the sub-trees
     *         which this data provider contains.
     */
    Set<DN> getBaseDNs();

    /**
     * Retrieves the specified entry from this data provider.
     *
     * @param dn
     *            The DN of the entry.
     * @return The requested entry, or {@code null} if this data provider does
     *         not contain the specified entry.
     * @throws LdapException
     *             If a problem occurs while trying to retrieve the entry, or if
     *             {@code dn} is not a DN equal to or subordinate to one of the
     *             base DNs managed by this data provider.
     */
    Entry getEntry(DN dn) throws LdapException;

    /**
     * Returns the current status of the provided base DN in this data provider.
     *
     * @param baseDN
     *            The base DN in this data provider.
     * @return The current status of the provided base DN in this data provider.
     * @throws LdapException
     *             If {@code baseDN} is not one of the base DNs managed by this
     *             data provider.
     */
    DataProviderStatus getStatus(DN baseDN) throws LdapException;

    /**
     * Returns an unmodifiable set containing the OIDs of the controls that may
     * be supported by the provided base DN in this data provider.
     *
     * @param baseDN
     *            The base DN in this data provider.
     * @return An unmodifiable set containing the OIDs of the controls that may
     *         be supported by the provided base DN in this data provider.
     * @throws LdapException
     *             If {@code baseDN} is not one of the base DNs managed by this
     *             data provider.
     */
    Set<String> getSupportedControls(DN baseDN) throws LdapException;

    /**
     * Returns an unmodifiable set containing the OIDs of the features that may
     * be supported by the provided base DN in this data provider.
     *
     * @param baseDN
     *            The base DN in this data provider.
     * @return An unmodifiable set containing the OIDs of the features that may
     *         be supported by the provided base DN in this data provider.
     * @throws LdapException
     *             If {@code baseDN} is not one of the base DNs managed by this
     *             data provider.
     */
    Set<String> getSupportedFeatures(DN baseDN) throws LdapException;

    /**
     * Registers an event listener with this data provider.
     *
     * @param listener
     *            The event listener.
     */
    void registerEventListener(DataProviderEventListener listener);

    /**
     * Starts this data provider so that it is ready to process client requests.
     * This method is called immediately before the first data provider
     * connection is opened.
     * <p>
     * Implementations must acquire any remaining resources in order to make
     * this data provider fully operational. This may include any of the
     * following:
     * <ul>
     * <li>connections to other data providers
     * <li>connections to remote databases
     * <li>connections to remote servers
     * <li>opening local databases and files
     * <li>pre-loading databases.
     * </ul>
     * Implementations must perform all required work synchronously such that,
     * on return, this data provider is fully operational.
     */
    void startDataProvider();

    /**
     * Performs any necessary work to stop this data provider. This includes
     * closing any connections to underlying data providers, databases, etc.
     * <p>
     * This method is called immediately after the last data provider connection
     * is closed. It must not return until this data provider is stopped.
     * <p>
     * Implementations must release all resources acquired when this data
     * provider was started. This includes:
     * <ul>
     * <li>connections to other data providers
     * <li>connections to remote databases
     * <li>connections to remote servers
     * <li>closing local databases and files.
     * </ul>
     * Implementations must not deregister this data provider or any associated
     * listeners such as those required for performing import, export, backup,
     * and restore.
     * <p>
     * Implementations must not throw any exceptions. If any problems are
     * encountered, then they may be logged but the shutdown should progress as
     * completely as possible.
     */
    void stopDataProvider();

    /**
     * Indicates whether the provided base DN in this data provider
     * supports change notification.
     *
     * @param baseDN
     *            The base DN in this data provider.
     * @return {@code true} if the provided base DN in this data provider
     *         supports change notification.
     * @throws LdapException
     *             If {@code baseDN} is not one of the base DNs managed by this
     *             data provider.
     */
    boolean supportsChangeNotification(DN baseDN) throws LdapException;

}
