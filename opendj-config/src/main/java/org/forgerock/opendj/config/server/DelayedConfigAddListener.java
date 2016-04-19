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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.forgerock.opendj.config.server.spi.ConfigAddListener;
import org.forgerock.opendj.config.server.spi.ConfigDeleteListener;
import org.forgerock.opendj.config.server.spi.ConfigurationRepository;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.i18n.LocalizableMessageBuilder;

/**
 * A configuration add listener which will monitor a parent entry to see when a
 * specified child entry has been added. When the child entry is added the add
 * listener will automatically register its "delayed" add or delete listener.
 */
final class DelayedConfigAddListener implements ConfigAddListener {

    private static final Logger debugLogger = LoggerFactory.getLogger(DelayedConfigAddListener.class);

    /** The name of the parent entry. */
    private final DN parent;

    /**
     * The name of the subordinate entry which should have an add or
     * delete listener registered with it when it is created.
     */
    private final DN child;

    /**
     * The add listener to be registered with the subordinate entry when
     * it is added (or null if a delete listener should be registered).
     */
    private final ConfigAddListener delayedAddListener;

    /**
     * The delete listener to be registered with the subordinate entry
     * when it is added (or null if an add listener should be registered).
     */
    private final ConfigDeleteListener delayedDeleteListener;

    private final ConfigurationRepository configRepository;

    /**
     * Create a new delayed add listener which will register an add listener
     * with the specified entry when it is added.
     *
     * @param child
     *            The name of the subordinate entry which should have an add
     *            listener registered with it when it is created.
     * @param addListener
     *            The add listener to be added to the subordinate entry when it
     *            is added.
     * @param configRepository
     *            Repository of config entries.
     */
    public DelayedConfigAddListener(DN child, ConfigAddListener addListener, ConfigurationRepository configRepository) {
        this.parent = child.parent();
        this.child = child;
        this.delayedAddListener = addListener;
        this.delayedDeleteListener = null;
        this.configRepository = configRepository;
    }

    /**
     * Create a new delayed add listener which will register a delete listener
     * with the specified entry when it is added.
     *
     * @param child
     *            The name of the subordinate entry which should have a delete
     *            listener registered with it when it is created.
     * @param deleteListener
     *            The delete listener to be added to the subordinate entry when
     *            it is added.
     * @param configRepository
     *            Repository of config entries.
     */
    public DelayedConfigAddListener(DN child, ConfigDeleteListener deleteListener,
        ConfigurationRepository configRepository) {
        this.parent = child.parent();
        this.child = child;
        this.delayedAddListener = null;
        this.configRepository = configRepository;
        this.delayedDeleteListener = deleteListener;
    }

    @Override
    public ConfigChangeResult applyConfigurationAdd(Entry configEntry) {
        if (configEntry.getName().equals(child)) {
            // The subordinate entry matched our criteria so register the
            // listener(s).
            if (delayedAddListener != null) {
                configRepository.registerAddListener(configEntry.getName(), delayedAddListener);
            }

            if (delayedDeleteListener != null) {
                configRepository.registerDeleteListener(configEntry.getName(), delayedDeleteListener);
            }

            try {
                // We are no longer needed.
                if (configRepository.hasEntry(parent)) {
                    configRepository.deregisterAddListener(parent, this);
                }
            } catch (ConfigException e) {
                debugLogger.trace("Unable to deregister add listener", e);
                // Ignore this error as it implies that this listener has
                // already been deregistered.
            }
        }

        return new ConfigChangeResult();
    }

    @Override
    public boolean configAddIsAcceptable(Entry configEntry, LocalizableMessageBuilder unacceptableReason) {
        // Always acceptable.
        return true;
    }

    /**
     * Gets the delayed add listener.
     * <p>
     * This method is provided for unit-testing.
     *
     * @return Returns the delayed add listener, or <code>null</code> if this
     *         listener is delaying a delete listener.
     */
    ConfigAddListener getDelayedAddListener() {
        return delayedAddListener;
    }

    /**
     * Gets the delayed delete listener.
     * <p>
     * This method is provided for unit-testing.
     *
     * @return Returns the delayed delete listener, or <code>null</code> if this
     *         listener is delaying a add listener.
     */
    ConfigDeleteListener getDelayedDeleteListener() {
        return delayedDeleteListener;
    }

}
