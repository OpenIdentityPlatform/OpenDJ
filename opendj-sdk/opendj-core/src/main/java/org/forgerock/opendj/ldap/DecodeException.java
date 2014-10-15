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

package org.forgerock.opendj.ldap;

import java.io.IOException;

import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;

/**
 * Thrown when data from an input source cannot be decoded, perhaps due to the
 * data being malformed in some way. By default decoding exceptions are fatal,
 * indicating that the associated input source is no longer usable.
 */
@SuppressWarnings("serial")
public final class DecodeException extends IOException implements LocalizableException {
    /**
     * Creates a new non-fatal decode exception with the provided message.
     *
     * @param message
     *            The message that explains the problem that occurred.
     * @return The new non-fatal decode exception.
     */
    public static DecodeException error(final LocalizableMessage message) {
        return new DecodeException(message, false, null);
    }

    /**
     * Creates a new non-fatal decode exception with the provided message and
     * root cause.
     *
     * @param message
     *            The message that explains the problem that occurred.
     * @param cause
     *            The underlying cause of this exception.
     * @return The new non-fatal decode exception.
     */
    public static DecodeException error(final LocalizableMessage message, final Throwable cause) {
        return new DecodeException(message, false, cause);
    }

    /**
     * Creates a new fatal decode exception with the provided message. The
     * associated input source can no longer be used.
     *
     * @param message
     *            The message that explains the problem that occurred.
     * @return The new fatal decode exception.
     */
    public static DecodeException fatalError(final LocalizableMessage message) {
        return new DecodeException(message, true, null);
    }

    /**
     * Creates a new fatal decode exception with the provided message and root
     * cause. The associated input source can no longer be used.
     *
     * @param message
     *            The message that explains the problem that occurred.
     * @param cause
     *            The underlying cause of this exception.
     * @return The new fatal decode exception.
     */
    public static DecodeException fatalError(final LocalizableMessage message, final Throwable cause) {
        return new DecodeException(message, true, cause);
    }

    private final LocalizableMessage message;

    private final boolean isFatal;

    /** Construction is provided via factory methods. */
    private DecodeException(final LocalizableMessage message, final boolean isFatal,
            final Throwable cause) {
        super(message.toString(), cause);
        this.message = message;
        this.isFatal = isFatal;
    }

    /**
     * Returns the message that explains the problem that occurred.
     *
     * @return LocalizableMessage of the problem
     */
    public LocalizableMessage getMessageObject() {
        return message;
    }

    /**
     * Indicates whether or not the error was fatal and the associated input
     * source can no longer be used.
     *
     * @return {@code true} if the error was fatal and the associated input
     *         source can no longer be used, otherwise {@code false} .
     */
    public boolean isFatal() {
        return isFatal;
    }
}
