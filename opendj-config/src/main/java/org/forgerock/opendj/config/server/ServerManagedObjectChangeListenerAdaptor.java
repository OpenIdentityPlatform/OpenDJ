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
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.server;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.Configuration;

/**
 * An adaptor class which converts {@link ServerManagedObjectChangeListener}
 * callbacks to {@link ConfigurationChangeListener} callbacks.
 *
 * @param <T>
 *            The type of server managed object that this listener should be
 *            notified about.
 */
final class ServerManagedObjectChangeListenerAdaptor<T extends Configuration> implements
    ServerManagedObjectChangeListener<T> {

    /** The underlying change listener. */
    private final ConfigurationChangeListener<? super T> listener;

    /**
     * Creates a new server managed object change listener adaptor.
     *
     * @param listener
     *            The underlying change listener.
     */
    public ServerManagedObjectChangeListenerAdaptor(ConfigurationChangeListener<? super T> listener) {
        this.listener = listener;
    }

    @Override
    public ConfigChangeResult applyConfigurationChange(ServerManagedObject<? extends T> mo) {
        return listener.applyConfigurationChange(mo.getConfiguration());
    }

    /**
     * Gets the configuration change listener associated with this adaptor.
     *
     * @return Returns the configuration change listener associated with this
     *         adaptor.
     */
    public ConfigurationChangeListener<? super T> getConfigurationChangeListener() {
        return listener;
    }

    @Override
    public boolean isConfigurationChangeAcceptable(ServerManagedObject<? extends T> mo,
        List<LocalizableMessage> unacceptableReasons) {
        return listener.isConfigurationChangeAcceptable(mo.getConfiguration(), unacceptableReasons);
    }

}
