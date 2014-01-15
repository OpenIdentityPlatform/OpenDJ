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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.config;

import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;

/**
 * Exceptions thrown when interacting with administration framework.
 */
public abstract class AdminException extends Exception implements LocalizableException {
    private final LocalizableMessage message;

    /**
     * Fake serialization ID.
     */
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
