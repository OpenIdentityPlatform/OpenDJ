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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.server.spi;

import java.util.List;
import java.util.Set;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;

/** Provides configuration entries and listener registration on the entries. */
public interface ConfigurationRepository {

    /**
     * Returns the set of DNs of children of the entry corresponding to the
     * provided DN. .
     *
     * @param dn
     *            DN of a configuration entry.
     * @return the set of DN of children of the corresponding entry
     * @throws ConfigException
     *             If a problem occurs during retrieval.
     */
    Set<DN> getChildren(DN dn) throws ConfigException;

    /**
     * Returns the configuration entry for the provided DN.
     *
     * @param dn
     *            DN of the configuration entry
     * @return the config entry
     * @throws ConfigException
     *             If a problem occurs while trying to retrieve the requested
     *             entry.
     */
    Entry getEntry(DN dn) throws ConfigException;

    /**
     * Checks if the provided DN corresponds to a configuration entry.
     *
     * @param dn
     *            DN of the configuration entry
     * @return {@code true} if and only if there is a configuration entry with
     *         this DN
     * @throws ConfigException
     *             If a problem occurs.
     */
    boolean hasEntry(DN dn) throws ConfigException;

    /**
     * Registers the provided add listener so that it will be notified if any
     * new entries are added immediately below the entry corresponding to the
     * provided DN.
     *
     * @param dn
     *            The DN of the configuration entry.
     * @param listener
     *            The add listener that should be registered.
     */
    void registerAddListener(DN dn, ConfigAddListener listener);

    /**
     * Registers the provided delete listener so that it will be notified if any
     * entries are deleted immediately below the entry corresponding to the
     * provided DN.
     *
     * @param dn
     *            The DN of the configuration entry.
     * @param listener
     *            The delete listener that should be registered.
     */
    void registerDeleteListener(DN dn, ConfigDeleteListener listener);

    /**
     * Registers the provided change listener so that it will be notified of any
     * changes to the entry corrresponding to provided DN. No check will be made
     * to determine whether the provided listener is already registered.
     *
     * @param dn
     *            The DN of the configuration entry.
     * @param listener
     *            The change listener that should be registered.
     */
    void registerChangeListener(DN dn, ConfigChangeListener listener);

    /**
     * Deregisters the provided add listener so that it will no longer be
     * notified if any new entries are added immediately below the entry
     * corresponding to the provided DN.
     *
     * @param dn
     *            The DN of the configuration entry.
     * @param listener
     *            The add listener that should be deregistered.
     */
    void deregisterAddListener(DN dn, ConfigAddListener listener);

    /**
     * Deregisters the provided delete listener so that it will no longer be
     * notified if any entries are deleted immediately below the entry
     * corresponding to the provided DN.
     *
     * @param dn
     *            The DN of the configuration entry.
     * @param listener
     *            The delete listener that should be deregistered.
     */
    void deregisterDeleteListener(DN dn, ConfigDeleteListener listener);

    /**
     * Attempts to deregister the provided change listener with the provided DN.
     *
     * @param dn
     *            The DN of the configuration entry.
     * @param listener
     *            The change listener to deregister with this DN.
     * @return <CODE>true</CODE> if the specified listener was deregistered, or
     *         <CODE>false</CODE> if it was not.
     */
    boolean deregisterChangeListener(DN dn, ConfigChangeListener listener);

    /**
     * Retrieves the add listeners that have been registered with the provided
     * DN.
     *
     * @param dn
     *            The DN of the configuration entry.
     * @return The list of add listeners.
     */
    List<ConfigAddListener> getAddListeners(DN dn);

    /**
     * Retrieves the delete listeners that have been registered with the
     * provided DN.
     *
     * @param dn
     *            The DN of the configuration entry.
     * @return The list of delete listeners.
     */
    List<ConfigDeleteListener> getDeleteListeners(DN dn);

    /**
     * Retrieves the change listeners that have been registered with the
     * provided DN.
     *
     * @param dn
     *            The DN of the configuration entry.
     * @return The list of change listeners.
     */
    List<ConfigChangeListener> getChangeListeners(DN dn);

}
