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
 * An adaptor class which converts {@link ServerManagedObjectAddListener}
 * callbacks to {@link ConfigurationAddListener} callbacks.
 *
 * @param <T>
 *            The type of server managed object that this listener should be
 *            notified about.
 */
final class ServerManagedObjectAddListenerAdaptor<T extends Configuration> implements
    ServerManagedObjectAddListener<T> {

    /** The underlying add listener. */
    private final ConfigurationAddListener<T> listener;

    /**
     * Creates a new server managed object add listener adaptor.
     *
     * @param listener
     *            The underlying add listener.
     */
    public ServerManagedObjectAddListenerAdaptor(ConfigurationAddListener<T> listener) {
        this.listener = listener;
    }

    @Override
    public ConfigChangeResult applyConfigurationAdd(ServerManagedObject<? extends T> mo) {
        return listener.applyConfigurationAdd(mo.getConfiguration());
    }

    /**
     * Gets the configuration add listener associated with this adaptor.
     *
     * @return Returns the configuration add listener associated with this
     *         adaptor.
     */
    public ConfigurationAddListener<T> getConfigurationAddListener() {
        return listener;
    }

    @Override
    public boolean isConfigurationAddAcceptable(ServerManagedObject<? extends T> mo,
        List<LocalizableMessage> unacceptableReasons) {
        return listener.isConfigurationAddAcceptable(mo.getConfiguration(), unacceptableReasons);
    }

}
