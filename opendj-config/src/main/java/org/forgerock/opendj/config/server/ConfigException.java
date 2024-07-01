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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.server;

import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;

/** Thrown during the course of interactions with the Directory Server configuration. */
public final class ConfigException extends Exception implements LocalizableException {
    private static final long serialVersionUID = -540463620272921157L;
    private final LocalizableMessage message;

    /**
     * Returns the message that explains the problem that occurred.
     *
     * @return LocalizableMessage of the problem
     */
    @Override
    public LocalizableMessage getMessageObject() {
        return message;
    }

    /**
     * Creates a new configuration exception with the provided message.
     *
     * @param message
     *            The message to use for this configuration exception.
     */
    public ConfigException(LocalizableMessage message) {
        super(message.toString());
        this.message = message;
    }

    /**
     * Creates a new configuration exception with the provided message and
     * underlying cause.
     *
     * @param message
     *            The message to use for this configuration exception.
     * @param cause
     *            The underlying cause that triggered this configuration
     *            exception.
     */
    public ConfigException(LocalizableMessage message, Throwable cause) {
        super(message.toString(), cause);
        this.message = message;
    }

}
