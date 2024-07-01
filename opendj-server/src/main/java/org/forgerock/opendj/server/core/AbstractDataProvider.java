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
 * Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.forgerock.opendj.server.core;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides a skeletal implementation of the {@link DataProvider}
 * interface, to minimize the effort required to implement this interface.
 */
public abstract class AbstractDataProvider implements DataProvider {
    private static final Logger debugLogger = LoggerFactory.getLogger(AbstractDataProvider.class);

    /** The list of event listeners associated with this data provider. */
    private final List<DataProviderEventListener> eventListeners = new CopyOnWriteArrayList<>();

    /** Creates a new abstract data provider. */
    protected AbstractDataProvider() {
        // No implementation required.
    }

    /**
     * {@inheritDoc}
     * <p>
     * The default implementation is to invoke {@code getEntry(dn)} and return
     * {@code true} if the entry was successfully retrieved.
     */
    @Override
    public boolean containsEntry(final DN dn) throws LdapException {
        return getEntry(dn) != null;
    }

    /** {@inheritDoc} */
    @Override
    public final void deregisterEventListener(final DataProviderEventListener listener) {
        eventListeners.remove(listener);
    }

    /** {@inheritDoc} */
    @Override
    public final void registerEventListener(final DataProviderEventListener listener) {
        eventListeners.add(listener);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The default implementation is to return false for all base DNs indicating
     * that change notification is not supported.
     */
    @Override
    public boolean supportsChangeNotification(final DN baseDN) throws LdapException {
        return false;
    }

    /**
     * Notify all event listeners that this data provider has changed state due
     * to an operational error, configuration change, or an administrative
     * action.
     * <p>
     * This method can be used to forward events to parent data providers.
     *
     * @param event
     *            The data provider event.
     */
    protected final void notifyDataProviderEventOccurred(final DataProviderEvent event) {
        for (final DataProviderEventListener listener : eventListeners) {
            try {
                listener.handleDataProviderEvent(event);
            } catch (final Exception e) {
                debugLogger.trace("Unexpected error occurred while invoking listener", e);
            }
        }
    }

    /**
     * Notify all event listeners that this data provider has changed state due
     * to an operational error, configuration change, or an administrative
     * action.
     * <p>
     * This method is equivalent to the following code:
     *
     * <pre>
     * DataProviderEvent event = new DataProviderEvent(reason, types);
     * notifyDataProviderStateChanged(event);
     * </pre>
     *
     * @param reason
     *            A message describing this event.
     * @param types
     *            The types of event that have occurred in the data provider.
     */
    protected final void notifyDataProviderEventOccurred(final LocalizableMessage reason,
            final Set<DataProviderEvent.Type> types) {
        final DataProviderEvent event = new DataProviderEvent(reason, types);
        notifyDataProviderEventOccurred(event);
    }

}
