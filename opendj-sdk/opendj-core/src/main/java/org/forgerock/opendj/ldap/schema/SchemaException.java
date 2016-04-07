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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;

/**
 * Thrown when a schema could not be decoded or validated.
 */
@SuppressWarnings("serial")
final class SchemaException extends Exception implements LocalizableException {
    /** The I18N message associated with this exception. */
    private final LocalizableMessage message;

    /**
     * Creates a new schema exception with the provided message.
     *
     * @param message
     *            The message that explains the problem that occurred.
     */
    public SchemaException(final LocalizableMessage message) {
        super(String.valueOf(message));
        this.message = message;
    }

    /**
     * Creates a new schema exception with the provided message and cause.
     *
     * @param message
     *            The message that explains the problem that occurred.
     * @param cause
     *            The cause which may be later retrieved by the
     *            {@link #getCause} method. A {@code null} value is permitted,
     *            and indicates that the cause is nonexistent or unknown.
     */
    public SchemaException(final LocalizableMessage message, final Throwable cause) {
        super(String.valueOf(message), cause);
        this.message = message;
    }

    @Override
    public LocalizableMessage getMessageObject() {
        return this.message;
    }
}
