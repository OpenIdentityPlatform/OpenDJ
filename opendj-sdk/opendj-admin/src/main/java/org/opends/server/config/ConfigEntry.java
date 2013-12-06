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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.config;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.core.DirectoryServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A config entry wraps an entry to provides add, change and delete listeners on
 * it.
 */
public final class ConfigEntry {

    private static final Logger debugLogger = LoggerFactory.getLogger(ConfigEntry.class);

    /** The set of add listeners that have been registered with this entry. */
    private final CopyOnWriteArrayList<ConfigAddListener> addListeners;

    /** The set of change listeners that have been registered with this entry. */
    private final CopyOnWriteArrayList<ConfigChangeListener> changeListeners;

    /** The set of delete listeners that have been registered with this entry. */
    private final CopyOnWriteArrayList<ConfigDeleteListener> deleteListeners;

    /** The actual entry wrapped by this configuration entry. */
    private Entry entry;

    private final ConfigurationRepository configRepository;

    /**
     * Creates a new config entry with the provided entry.
     *
     * @param entry
     *            The entry that will be encapsulated by this config entry.
     */
    public ConfigEntry(Entry entry, ConfigurationRepository configRepository) {
        this.entry = entry;
        this.configRepository = configRepository;
        addListeners = new CopyOnWriteArrayList<ConfigAddListener>();
        changeListeners = new CopyOnWriteArrayList<ConfigChangeListener>();
        deleteListeners = new CopyOnWriteArrayList<ConfigDeleteListener>();
    }

    /**
     * Retrieves the actual entry wrapped by this configuration entry.
     *
     * @return The actual entry wrapped by this configuration entry.
     */
    public Entry getEntry() {
        return entry;
    }

    /**
     * Retrieves the DN for this configuration entry.
     *
     * @return The DN for this configuration entry.
     */
    public DN getDN() {
        return entry.getName();
    }

    /**
     * Indicates whether this configuration entry contains the specified
     * objectclass.
     *
     * @param name
     *            The name of the objectclass for which to make the
     *            determination.
     * @return <CODE>true</CODE> if this configuration entry contains the
     *         specified objectclass, or <CODE>false</CODE> if not.
     */
    public boolean hasObjectClass(String name) {
        // TODO : use the schema to get object class and check it in the entry
        ObjectClass oc = DirectoryServer.getObjectClass(name.toLowerCase());
        if (oc == null) {
            oc = DirectoryServer.getDefaultObjectClass(name);
        }

        return Entries.containsObjectClass(entry, oc);
    }

    /**
     * Retrieves the set of change listeners that have been registered with this
     * configuration entry.
     *
     * @return The set of change listeners that have been registered with this
     *         configuration entry.
     */
    public CopyOnWriteArrayList<ConfigChangeListener> getChangeListeners() {
        return changeListeners;
    }

    /**
     * Registers the provided change listener so that it will be notified of any
     * changes to this configuration entry. No check will be made to determine
     * whether the provided listener is already registered.
     *
     * @param listener
     *            The change listener to register with this config entry.
     */
    public void registerChangeListener(ConfigChangeListener listener) {
        changeListeners.add(listener);
    }

    /**
     * Attempts to deregister the provided change listener with this
     * configuration entry.
     *
     * @param listener
     *            The change listener to deregister with this config entry.
     * @return <CODE>true</CODE> if the specified listener was deregistered, or
     *         <CODE>false</CODE> if it was not.
     */
    public boolean deregisterChangeListener(ConfigChangeListener listener) {
        return changeListeners.remove(listener);
    }

    /**
     * Retrieves the set of config add listeners that have been registered for
     * this entry.
     *
     * @return The set of config add listeners that have been registered for
     *         this entry.
     */
    public CopyOnWriteArrayList<ConfigAddListener> getAddListeners() {
        return addListeners;
    }

    /**
     * Registers the provided add listener so that it will be notified if any
     * new entries are added immediately below this configuration entry.
     *
     * @param listener
     *            The add listener that should be registered.
     */
    public void registerAddListener(ConfigAddListener listener) {
        addListeners.addIfAbsent(listener);
    }

    /**
     * Deregisters the provided add listener so that it will no longer be
     * notified if any new entries are added immediately below this
     * configuration entry.
     *
     * @param listener
     *            The add listener that should be deregistered.
     */
    public void deregisterAddListener(ConfigAddListener listener) {
        addListeners.remove(listener);
    }

    /**
     * Retrieves the set of config delete listeners that have been registered
     * for this entry.
     *
     * @return The set of config delete listeners that have been registered for
     *         this entry.
     */
    public CopyOnWriteArrayList<ConfigDeleteListener> getDeleteListeners() {
        return deleteListeners;
    }

    /**
     * Registers the provided delete listener so that it will be notified if any
     * entries are deleted immediately below this configuration entry.
     *
     * @param listener
     *            The delete listener that should be registered.
     */
    public void registerDeleteListener(ConfigDeleteListener listener) {
        deleteListeners.addIfAbsent(listener);
    }

    /**
     * Deregisters the provided delete listener so that it will no longer be
     * notified if any new are removed immediately below this configuration
     * entry.
     *
     * @param listener
     *            The delete listener that should be deregistered.
     */
    public void deregisterDeleteListener(ConfigDeleteListener listener) {
        deleteListeners.remove(listener);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return entry.getName().toNormalizedString();
    }

    /**
     * Retrieves the set of children associated with this configuration entry.
     *
     * @return  The set of children associated with this configuration entry.
     */
    public Set<DN> getChildren() {
        return configRepository.getChildren(entry);
    }
}
