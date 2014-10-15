/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
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

    /** {@inheritDoc} */
    public LocalizableMessage getMessageObject() {
        return this.message;
    }
}
