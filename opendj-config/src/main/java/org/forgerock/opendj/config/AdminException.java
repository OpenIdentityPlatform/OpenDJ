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
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;

/** Exceptions thrown when interacting with administration framework. */
public abstract class AdminException extends Exception implements LocalizableException {
    private final LocalizableMessage message;

    /** Fake serialization ID. */
    private static final long serialVersionUID = 1L;

    /**
     * Create an admin exception with a message and cause.
     *
     * @param message
     *            The message.
     * @param cause
     *            The cause.
     */
    protected AdminException(LocalizableMessage message, Throwable cause) {
        super(cause);
        this.message = message;
    }

    /**
     * Create an admin exception with a message.
     *
     * @param message
     *            The message.
     */
    protected AdminException(LocalizableMessage message) {
        this.message = message;
    }

    @Override
    public LocalizableMessage getMessageObject() {
        return message;
    }
}
