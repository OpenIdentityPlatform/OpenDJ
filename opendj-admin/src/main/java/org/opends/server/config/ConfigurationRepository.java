/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013 ForgeRock AS.
 */
package org.opends.server.config;

import java.util.List;
import java.util.Set;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;

/**
 * Provides configuration entries and listener registration on the entries.
 */
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
    public void registerAddListener(DN dn, ConfigAddListener listener);

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
    public void registerDeleteListener(DN dn, ConfigDeleteListener listener);

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
    public void registerChangeListener(DN dn, ConfigChangeListener listener);

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
    public void deregisterAddListener(DN dn, ConfigAddListener listener);

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
    public void deregisterDeleteListener(DN dn, ConfigDeleteListener listener);

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
    public boolean deregisterChangeListener(DN dn, ConfigChangeListener listener);

    /**
     * Retrieves the add listeners that have been registered with the provided
     * DN.
     *
     * @param dn
     *            The DN of the configuration entry.
     * @return The list of add listeners.
     */
    public List<ConfigAddListener> getAddListeners(DN dn);

    /**
     * Retrieves the delete listeners that have been registered with the
     * provided DN.
     *
     * @param dn
     *            The DN of the configuration entry.
     * @return The list of delete listeners.
     */
    public List<ConfigDeleteListener> getDeleteListeners(DN dn);

    /**
     * Retrieves the change listeners that have been registered with the
     * provided DN.
     *
     * @param dn
     *            The DN of the configuration entry.
     * @return The list of change listeners.
     */
    public List<ConfigChangeListener> getChangeListeners(DN dn);

}
