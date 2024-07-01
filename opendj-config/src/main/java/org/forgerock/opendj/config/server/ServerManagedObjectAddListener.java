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
 */
package org.forgerock.opendj.config.server;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.Configuration;

import java.util.List;

/**
 * This interface defines the methods that a Directory Server configurable
 * component should implement if it wishes to be able to receive notifications
 * when a new server managed object is added.
 *
 * @param <T>
 *            The type of server managed object that this listener should be
 *            notified about.
 */
public interface ServerManagedObjectAddListener<T extends Configuration> {

    /**
     * Indicates whether the proposed addition of a new server managed object is
     * acceptable to this add listener.
     *
     * @param mo
     *            The server managed object that will be added.
     * @param unacceptableReasons
     *            A list that can be used to hold messages about why the
     *            provided server managed object is not acceptable.
     * @return Returns <code>true</code> if the proposed addition is acceptable,
     *         or <code>false</code> if it is not.
     */
    boolean isConfigurationAddAcceptable(ServerManagedObject<? extends T> mo,
            List<LocalizableMessage> unacceptableReasons);

    /**
     * Adds a new server managed object to this add listener.
     *
     * @param mo
     *            The server managed object that will be added.
     * @return Returns information about the result of adding the server managed
     *         object.
     */
    ConfigChangeResult applyConfigurationAdd(ServerManagedObject<? extends T> mo);
}
