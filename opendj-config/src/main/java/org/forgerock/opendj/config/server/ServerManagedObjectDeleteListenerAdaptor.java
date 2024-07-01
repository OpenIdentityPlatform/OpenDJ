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
 * An adaptor class which converts {@link ServerManagedObjectDeleteListener}
 * callbacks to {@link ConfigurationDeleteListener} callbacks.
 *
 * @param <T>
 *            The type of server managed object that this listener should be
 *            notified about.
 */
final class ServerManagedObjectDeleteListenerAdaptor<T extends Configuration> implements
        ServerManagedObjectDeleteListener<T> {

    /** The underlying delete listener. */
    private final ConfigurationDeleteListener<T> listener;

    /**
     * Creates a new server managed object delete listener adaptor.
     *
     * @param listener
     *            The underlying delete listener.
     */
    public ServerManagedObjectDeleteListenerAdaptor(ConfigurationDeleteListener<T> listener) {
        this.listener = listener;
    }

    @Override
    public ConfigChangeResult applyConfigurationDelete(ServerManagedObject<? extends T> mo) {
        return listener.applyConfigurationDelete(mo.getConfiguration());
    }

    /**
     * Gets the configuration delete listener associated with this adaptor.
     *
     * @return Returns the configuration delete listener associated with this
     *         adaptor.
     */
    public ConfigurationDeleteListener<T> getConfigurationDeleteListener() {
        return listener;
    }

    @Override
    public boolean isConfigurationDeleteAcceptable(ServerManagedObject<? extends T> mo,
            List<LocalizableMessage> unacceptableReasons) {
        return listener.isConfigurationDeleteAcceptable(mo.getConfiguration(), unacceptableReasons);
    }

}
